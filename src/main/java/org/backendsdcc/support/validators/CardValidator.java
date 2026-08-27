package org.backendsdcc.support.validators;

import org.backendsdcc.models.PaymentMethod;

/**
 * Del numero di carta l'applicazione conserva soltanto le ultime quattro cifre.
 *
 * Non e' una limitazione tecnica, e' la scelta: un PAN completo in chiaro nel
 * database porta con se' gli obblighi PCI-DSS e una responsabilita' che questo
 * sistema non ha nessun motivo di assumersi, visto che per cercare una ricevuta
 * quattro cifre bastano. Chi cerca puo' comunque digitare il numero intero:
 * viene ridotto qui, prima che tocchi il database o finisca in un log.
 */
public final class CardValidator
{
    /** Le carte vere stanno fra 13 e 19 cifre (Visa 13/16, Amex 15, Maestro fino a 19). */
    private static final int MIN_PAN = 13;
    private static final int MAX_PAN = 19;

    private CardValidator() {}

    public static boolean richiedeCarta(PaymentMethod paymentMethod)
    {
        return paymentMethod == PaymentMethod.CREDIT_CARD || paymentMethod == PaymentMethod.DEBIT_CARD;
    }

    /**
     * Riduce alle ultime quattro cifre quello che arriva, che siano quattro
     * cifre o un numero completo con spazi e trattini.
     *
     * @return le quattro cifre, oppure null se l'ingresso e' vuoto
     * @throws IllegalArgumentException se non e' un numero di carta plausibile
     */
    public static String toLast4(String input)
    {
        if (input == null || input.isBlank())
            return null;

        String cifre = input.replaceAll("\\D", "");

        if (cifre.length() < 4)
            throw new IllegalArgumentException("Servono almeno le ultime quattro cifre della carta");
        if (cifre.length() > MAX_PAN)
            throw new IllegalArgumentException("Numero di carta non valido");

        // Il controllo di Luhn ha senso solo su un numero intero: se l'utente ha
        // scritto solo le ultime cifre non c'e' niente da verificare, e
        // pretenderlo renderebbe impossibile inserire una ricevuta di cui si
        // conosce appunto solo la coda del numero.
        if (cifre.length() >= MIN_PAN && !luhn(cifre))
            throw new IllegalArgumentException("Numero di carta non valido");

        return cifre.substring(cifre.length() - 4);
    }

    /**
     * L'algoritmo di Luhn: si raddoppia una cifra su due partendo dalla penultima
     * e si sottrae 9 ai risultati sopra il 9; la somma di un numero valido e'
     * divisibile per dieci. Intercetta i refusi, non l'esistenza della carta.
     */
    private static boolean luhn(String cifre)
    {
        int somma = 0;
        boolean raddoppia = false;
        for (int i = cifre.length() - 1; i >= 0; i--)
        {
            int cifra = cifre.charAt(i) - '0';
            if (raddoppia)
            {
                cifra *= 2;
                if (cifra > 9)
                    cifra -= 9;
            }
            somma += cifra;
            raddoppia = !raddoppia;
        }
        return somma % 10 == 0;
    }
}
