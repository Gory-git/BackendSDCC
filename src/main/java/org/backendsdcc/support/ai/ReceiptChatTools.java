package org.backendsdcc.support.ai;

import org.backendsdcc.services.ProductService;
import org.backendsdcc.services.ReceiptService;
import org.backendsdcc.services.StatsService;
import org.backendsdcc.services.UserService;
import org.backendsdcc.support.dto.*;
import org.backendsdcc.support.validators.DateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Gli strumenti che il modello puo' chiamare per rispondere alle domande.
 *
 * Due scelte di fondo, entrambe volute:
 *
 * 1. Nessuna generazione di SQL. Ogni strumento delega a un service che esiste
 *    gia' ed e' gia' usato dai controller, quindi i controlli di autorizzazione
 *    scritti li' valgono anche qui invece di essere aggirati.
 * 2. Tutti gli strumenti sono in sola lettura. Non esiste un modo di creare o
 *    cancellare qualcosa parlando col chatbot: le scritture restano sulle
 *    pagine dell'applicazione, dove c'e' una conferma esplicita dell'utente.
 *
 * ATTENZIONE, il punto 1 non vale per tutti i service allo stesso modo:
 * ReceiptService e UserService fanno il controllo di ruolo al loro interno,
 * mentre StatsService e ProductService.getMostBoughtProductOfTimeSpan NON lo
 * fanno - li' la guardia sta solo sul controller (@PreAuthorize piu' la regola
 * /admin/** in SecurityConfig), che da qui non passa nessuno. Per quei metodi
 * il controllo di ruolo e' rifatto a mano sotto: se lo togli, un utente normale
 * legge i dati di tutti.
 */
@Component
public class ReceiptChatTools
{
    private static final Logger log = LoggerFactory.getLogger(ReceiptChatTools.class);

    /**
     * Le ricerche fuzzy dei service vogliono una soglia Jaro-Winkler. Il modello
     * non ha modo di sceglierla sensatamente, quindi non gliela chiediamo: 0.85
     * e' abbastanza permissiva da assorbire un refuso e abbastanza stretta da
     * non restituire mezzo database.
     */
    private static final float SOGLIA_FUZZY = 0.85f;

    /**
     * Ogni riga che torna al modello e' input a pagamento del giro successivo.
     * Il taglio viene dichiarato nella risposta, cosi' il modello sa di non
     * avere il quadro completo e lo dice all'utente invece di sommare numeri
     * parziali spacciandoli per un totale.
     */
    private static final int MAX_RIGHE = 25;

    private static final String NON_AUTORIZZATO =
            "Non autorizzato: questo dato e' riservato agli amministratori. "
            + "Dillo all'utente e non provare altri strumenti per ottenerlo.";

    private static final String NESSUN_RISULTATO = "Nessun risultato.";

    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private ProductService productService;
    @Autowired
    private StatsService statsService;
    @Autowired
    private UserService userService;

    // Ricevute ────────────────────────────────────────────────────────────────

    @Tool(description = "Elenca le ricevute visibili all'utente. Un cliente vede solo le proprie, "
            + "un amministratore le vede tutte. Usalo per domande generiche come 'quante ricevute ho' "
            + "oppure 'qual e' la mia spesa totale'.")
    public String elencaRicevute(
            @ToolParam(description = "true per ordinare per data crescente, false per ordinare per importo crescente")
            boolean ordinaPerData)
    {
        return esegui("elencaRicevute", () -> righeRicevute(receiptService.getAllReceiptsOrdered(ordinaPerData)));
    }

    @Tool(description = "Cerca ricevute il cui codice assomiglia a quello indicato. La ricerca tollera "
            + "errori di battitura. Un cliente trova solo le proprie ricevute.")
    public String cercaRicevutePerCodice(
            @ToolParam(description = "Codice o frammento di codice della ricevuta, ad esempio 'SEED-0012'")
            String codice)
    {
        return esegui("cercaRicevutePerCodice",
                () -> righeRicevute(receiptService.findByCodeLike(codice, SOGLIA_FUZZY)));
    }

    @Tool(description = "Cerca le ricevute il cui importo totale sta fra un minimo e un massimo, in euro. "
            + "Un cliente trova solo le proprie ricevute.")
    public String cercaRicevutePerImporto(
            @ToolParam(description = "Importo minimo in euro, ad esempio 10.00") BigDecimal importoMinimo,
            @ToolParam(description = "Importo massimo in euro, ad esempio 50.00") BigDecimal importoMassimo)
    {
        return esegui("cercaRicevutePerImporto",
                () -> righeRicevute(receiptService.findByAmountBetween(importoMinimo, importoMassimo)));
    }

    @Tool(description = "SOLO AMMINISTRATORI. Cerca le ricevute intestate a un cliente, dato un pezzo "
            + "della sua email. La ricerca tollera errori di battitura.")
    public String cercaRicevutePerCliente(
            @ToolParam(description = "Email o frammento di email del cliente") String emailCliente)
    {
        return esegui("cercaRicevutePerCliente",
                () -> righeRicevute(receiptService.findByUserEmailLike(emailCliente, SOGLIA_FUZZY)));
    }

    @Tool(description = "Restituisce il dettaglio completo di una singola ricevuta, comprese le righe dei "
            + "prodotti acquistati con quantita' e prezzo. Serve il codice esatto.")
    public String dettaglioRicevuta(
            @ToolParam(description = "Codice esatto della ricevuta") String codice)
    {
        return esegui("dettaglioRicevuta", () ->
        {
            ReceiptDTO receipt = receiptService.getReceipt(codice);
            StringBuilder sb = new StringBuilder(rigaRicevuta(receipt));
            if (receipt.getLines() != null)
                for (ReceiptLineDTO line : receipt.getLines())
                    sb.append("\n  - ").append(line.getProductCode())
                      .append(" ").append(line.getProductName())
                      .append(" x").append(line.getQuantity())
                      .append(" a ").append(line.getPrice()).append(" EUR");
            return sb.toString();
        });
    }

    // Prodotti ────────────────────────────────────────────────────────────────

    @Tool(description = "Cerca prodotti a catalogo per nome o codice. La ricerca tollera errori di "
            + "battitura. Serve per trovare il codice di un prodotto di cui l'utente conosce solo il nome.")
    public String cercaProdotti(
            @ToolParam(description = "Nome o codice, anche parziale, del prodotto") String query)
    {
        return esegui("cercaProdotti", () ->
        {
            List<ProductDTO> prodotti = productService.findByNameOrCodeLike(query, SOGLIA_FUZZY);
            return righe(prodotti, p -> p.getCode() + " | " + p.getName());
        });
    }

    @Tool(description = "Il prodotto piu' acquistato in un periodo. Se non indichi il cliente vale "
            + "l'utente corrente. Un cliente non puo' chiedere i dati di un altro.")
    public String prodottoPiuAcquistato(
            @ToolParam(description = "Data di inizio del periodo, formato yyyy-MM-dd", required = false)
            @Nullable String dataInizio,
            @ToolParam(description = "Data di fine del periodo, formato yyyy-MM-dd", required = false)
            @Nullable String dataFine,
            @ToolParam(description = "Email del cliente. Solo un amministratore puo' indicarne una diversa dalla propria.",
                    required = false)
            @Nullable String emailCliente)
    {
        return esegui("prodottoPiuAcquistato", () ->
        {
            String mia = userService.getCurrentUser().getEmail();
            // getMostBoughtProductOfTimeSpan non guarda chi sta chiedendo:
            // senza questo ramo un cliente leggerebbe gli acquisti di chiunque.
            if (emailCliente != null && !emailCliente.isBlank()
                    && !emailCliente.equalsIgnoreCase(mia) && !isAdmin())
                return NON_AUTORIZZATO;

            String email = (emailCliente == null || emailCliente.isBlank()) ? mia : emailCliente;
            ProductDTO prodotto = productService.getMostBoughtProductOfTimeSpan(
                    email, inizio(dataInizio), fine(dataFine));
            return prodotto.getCode() + " | " + prodotto.getName();
        });
    }

    // Clienti ─────────────────────────────────────────────────────────────────

    @Tool(description = "SOLO AMMINISTRATORI. Cerca i clienti registrati per nome, cognome, email o "
            + "codice fiscale. La ricerca tollera errori di battitura.")
    public String cercaClienti(
            @ToolParam(description = "Nome, cognome, email o codice fiscale, anche parziale") String query)
    {
        return esegui("cercaClienti", () ->
        {
            List<UserDTO> utenti = userService.searchUsers(query, SOGLIA_FUZZY);
            return righe(utenti, u -> u.getEmail() + " | " + u.getName() + " " + u.getSurname()
                    + " | " + ("ROLE_ADMIN".equals(u.getRole()) ? "amministratore" : "cliente"));
        });
    }

    // Statistiche (tutte solo amministratori) ─────────────────────────────────

    @Tool(description = "SOLO AMMINISTRATORI. Riepilogo di un periodo: fatturato totale, numero di "
            + "ricevute, scontrino medio, numero di utenti registrati.")
    public String statisticheRiepilogo(
            @ToolParam(description = "Data di inizio, formato yyyy-MM-dd", required = false) @Nullable String dataInizio,
            @ToolParam(description = "Data di fine, formato yyyy-MM-dd", required = false) @Nullable String dataFine)
    {
        return eseguiComeAdmin("statisticheRiepilogo", () ->
        {
            SummaryStatsDTO s = statsService.getSummary(inizio(dataInizio), fine(dataFine));
            return "fatturato=" + s.getTotalRevenue() + " EUR"
                    + " | ricevute=" + s.getReceiptCount()
                    + " | scontrino medio=" + s.getAverageReceipt() + " EUR"
                    + " | utenti=" + s.getUserCount()
                    + " | amministratori=" + s.getAdminCount();
        });
    }

    @Tool(description = "SOLO AMMINISTRATORI. Fatturato giorno per giorno in un periodo.")
    public String ricaviNelTempo(
            @ToolParam(description = "Data di inizio, formato yyyy-MM-dd", required = false) @Nullable String dataInizio,
            @ToolParam(description = "Data di fine, formato yyyy-MM-dd", required = false) @Nullable String dataFine)
    {
        return eseguiComeAdmin("ricaviNelTempo", () ->
        {
            List<RevenuePointDTO> punti = statsService.getRevenueOverTime(inizio(dataInizio), fine(dataFine));
            return righe(punti, p -> p.getDate() + " | " + p.getTotal() + " EUR | " + p.getCount() + " ricevute");
        });
    }

    @Tool(description = "SOLO AMMINISTRATORI. I prodotti piu' venduti in un periodo, per quantita'.")
    public String prodottiPiuVenduti(
            @ToolParam(description = "Data di inizio, formato yyyy-MM-dd", required = false) @Nullable String dataInizio,
            @ToolParam(description = "Data di fine, formato yyyy-MM-dd", required = false) @Nullable String dataFine,
            @ToolParam(description = "Quanti prodotti restituire, da 1 a 25", required = false) @Nullable Integer limite)
    {
        return eseguiComeAdmin("prodottiPiuVenduti", () ->
        {
            List<ProductStatDTO> stats = statsService.getTopProducts(inizio(dataInizio), fine(dataFine), limite(limite));
            return righe(stats, s -> s.getProductCode() + " | " + s.getProductName()
                    + " | " + s.getQuantity() + " pezzi | " + s.getRevenue() + " EUR");
        });
    }

    @Tool(description = "SOLO AMMINISTRATORI. Come si sono distribuiti i pagamenti fra i metodi "
            + "disponibili in un periodo.")
    public String metodiDiPagamento(
            @ToolParam(description = "Data di inizio, formato yyyy-MM-dd", required = false) @Nullable String dataInizio,
            @ToolParam(description = "Data di fine, formato yyyy-MM-dd", required = false) @Nullable String dataFine)
    {
        return eseguiComeAdmin("metodiDiPagamento", () ->
        {
            List<PaymentMethodStatDTO> stats = statsService.getPaymentMethodBreakdown(inizio(dataInizio), fine(dataFine));
            return righe(stats, s -> s.getPaymentMethod() + " | " + s.getCount() + " ricevute | " + s.getTotal() + " EUR");
        });
    }

    @Tool(description = "SOLO AMMINISTRATORI. I clienti che hanno speso di piu' in un periodo.")
    public String clientiMigliori(
            @ToolParam(description = "Data di inizio, formato yyyy-MM-dd", required = false) @Nullable String dataInizio,
            @ToolParam(description = "Data di fine, formato yyyy-MM-dd", required = false) @Nullable String dataFine,
            @ToolParam(description = "Quanti clienti restituire, da 1 a 25", required = false) @Nullable Integer limite)
    {
        return eseguiComeAdmin("clientiMigliori", () ->
        {
            List<UserStatDTO> stats = statsService.getTopUsers(inizio(dataInizio), fine(dataFine), limite(limite));
            return righe(stats, s -> s.getEmail() + " | " + s.getName() + " " + s.getSurname()
                    + " | " + s.getTotalSpent() + " EUR | " + s.getReceiptCount() + " ricevute");
        });
    }

    // Infrastruttura comune ───────────────────────────────────────────────────

    private boolean isAdmin()
    {
        return "ROLE_ADMIN".equals(userService.getCurrentUser().getRole());
    }

    /**
     * Le eccezioni non escono mai da uno strumento. Se uscissero, Spring AI
     * girerebbe il messaggio originale al modello, che lo riporterebbe
     * testualmente all'utente: il testo degli errori interni non si mostra.
     * Qui viene loggato per intero e al modello arriva una frase neutra.
     */
    private String esegui(String nome, Supplier<String> azione)
    {
        try
        {
            String risultato = azione.get();
            log.debug("tool {} -> {} caratteri", nome, risultato.length());
            return risultato;
        } catch (Exception e)
        {
            log.warn("tool {} fallito", nome, e);
            return "Lo strumento non ha potuto rispondere. Dillo all'utente e non inventare i dati.";
        }
    }

    private String eseguiComeAdmin(String nome, Supplier<String> azione)
    {
        return esegui(nome, () -> isAdmin() ? azione.get() : NON_AUTORIZZATO);
    }

    private static int limite(@Nullable Integer limite)
    {
        if (limite == null || limite <= 0)
            return 5;
        return Math.min(limite, MAX_RIGHE);
    }

    /**
     * Il modello ragiona in giorni, i service in Instant. Le due estremita' non
     * sono simmetriche:
     * - l'inizio va portato avanti al minimo accettato da DateValidator,
     *   altrimenti "tutte le mie ricevute" verrebbe rifiutato come data non valida;
     * - la fine va riportata indietro ad adesso, perche' la fine della giornata
     *   odierna e' quasi sempre nel futuro e DateValidator rifiuta il futuro.
     *   E' la stessa correzione che il frontend fa con endOfDayIsoClamped.
     */
    private static Instant inizio(@Nullable String data)
    {
        Instant minimo = DateValidator.minInstant();
        Instant richiesto = parse(data, minimo, true);
        return richiesto.isBefore(minimo) ? minimo : richiesto;
    }

    private static Instant fine(@Nullable String data)
    {
        Instant adesso = Instant.now();
        Instant richiesto = parse(data, adesso, false);
        return richiesto.isAfter(adesso) ? adesso : richiesto;
    }

    private static Instant parse(@Nullable String data, Instant fallback, boolean inizioGiorno)
    {
        if (data == null || data.isBlank())
            return fallback;
        try
        {
            LocalDate giorno = LocalDate.parse(data.trim());
            return inizioGiorno
                    ? giorno.atStartOfDay(ZoneOffset.UTC).toInstant()
                    : giorno.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        } catch (DateTimeParseException e)
        {
            // Una data che il modello ha scritto storta non deve far fallire la
            // richiesta: si ripiega sull'estremo naturale dell'intervallo.
            return fallback;
        }
    }

    private String righeRicevute(List<ReceiptDTO> ricevute)
    {
        return righe(ricevute, this::rigaRicevuta);
    }

    private String rigaRicevuta(ReceiptDTO receipt)
    {
        return receipt.getCode()
                + " | " + receipt.getDate().atZone(ZoneOffset.UTC).toLocalDate()
                + " | " + receipt.getAmount() + " EUR"
                + " | " + receipt.getPaymentMethod()
                + " | " + receipt.getUserEmail();
    }

    private <T> String righe(List<T> elementi, Function<T, String> formato)
    {
        if (elementi == null || elementi.isEmpty())
            return NESSUN_RISULTATO;

        StringBuilder sb = new StringBuilder();
        sb.append("Trovati ").append(elementi.size()).append(" risultati.");
        if (elementi.size() > MAX_RIGHE)
            sb.append(" Ne elenco solo i primi ").append(MAX_RIGHE)
              .append(": avvisa l'utente che l'elenco e' parziale e non sommare gli importi come se fossero tutti.");
        sb.append("\n");

        elementi.stream().limit(MAX_RIGHE).forEach(e -> sb.append(formato.apply(e)).append("\n"));
        return sb.toString();
    }
}
