package org.backendsdcc.support.ai;

import org.backendsdcc.support.exceptions.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quante domande al giorno puo' fare un utente al chatbot.
 *
 * Serve perche' /chat e' l'unico endpoint che costa soldi veri a ogni chiamata:
 * un utente autenticato che lo richiama in ciclo consuma i crediti OpenAI
 * dell'applicazione, e i limiti gia' presenti (lunghezza della domanda e dello
 * storico) tagliano il costo della singola richiesta, non il loro numero.
 *
 * Il conteggio sta in memoria, quindi vale per istanza: con due backend dietro
 * un bilanciatore il tetto effettivo raddoppia, e un riavvio azzera la giornata.
 * E' una semplificazione voluta - l'alternativa e' una scrittura sul database a
 * ogni domanda - e regge finche' l'istanza e' una sola, com'e' in produzione.
 * Il limite e' contro lo spreco accidentale, non contro un attaccante.
 */
@Component
public class ChatRateLimiter
{
    /**
     * Oltre questa soglia si buttano via le righe dei giorni passati: senza,
     * la mappa terrebbe un'entrata per ogni utente che ha mai scritto al
     * chatbot. Il numero e' alto apposta, la pulizia deve essere rara.
     */
    private static final int MAX_UTENTI_IN_MEMORIA = 1000;

    private record Consumo(LocalDate giorno, int domande) {}

    private final Map<String, Consumo> consumi = new ConcurrentHashMap<>();

    @Value("${app.chat.max-domande-giornaliere:30}")
    private int massimo;

    /**
     * Registra una domanda dell'utente indicato e lascia passare, oppure
     * solleva se ha gia' esaurito la quota di oggi.
     *
     * Si conta prima di chiamare il modello, non dopo: il credito lo consuma
     * anche una richiesta che poi fallisce, e contare solo i successi
     * lascerebbe fuori proprio il caso del ciclo che va in errore.
     */
    public void registraDomanda(String utente)
    {
        LocalDate oggi = LocalDate.now(ZoneOffset.UTC);

        if (consumi.size() > MAX_UTENTI_IN_MEMORIA)
            consumi.values().removeIf(consumo -> !consumo.giorno().equals(oggi));

        // compute() e' atomico sulla singola chiave: due richieste in parallelo
        // dello stesso utente non possono leggere lo stesso contatore e
        // scrivere entrambe "uno in piu'".
        Consumo aggiornato = consumi.compute(utente, (chiave, precedente) ->
                precedente == null || !precedente.giorno().equals(oggi)
                        ? new Consumo(oggi, 1)
                        : new Consumo(oggi, precedente.domande() + 1));

        if (aggiornato.domande() > massimo)
            throw new TooManyRequestsException(
                    "Hai raggiunto il limite di " + massimo + " domande al giorno al chatbot. Riprova domani.");
    }
}
