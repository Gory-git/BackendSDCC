package org.backendsdcc.support.validators;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
public class DateValidator
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss").withZone(ZoneOffset.UTC);;

    private static final Instant MIN_INSTANT = Instant.parse("2025-01-01T08:30:00.00Z");

    /**
     * Le date arrivano dal browser, che le calcola con l'orologio del client: se
     * quello è avanti anche di pochi secondi rispetto al server, un "adesso"
     * legittimo diventa una data nel futuro e viene rifiutato. È successo in
     * produzione con uno scarto di una decina di secondi, mentre in locale non si
     * vedeva perché browser e backend condividono lo stesso orologio.
     * Cinque minuti è la stessa tolleranza che si usa di norma per lo scarto di
     * orologio nella validazione dei token.
     */
    private static final Duration TOLLERANZA_FUTURO = Duration.ofMinutes(5);

    private DateValidator() {}

    public static boolean isValid(String date)
    {
        if (date == null || date.isBlank())
            return false;
        try
        {
            Instant instant = FORMATTER.parse(date.trim(), Instant::from);
            return isValid(instant);
        } catch (DateTimeException e)
        {
            return false;
        }
    }

    public static boolean isValid(Instant instant)
    {
        if (instant == null)
            return false;
        Instant limiteSuperiore = Instant.now().plus(TOLLERANZA_FUTURO);
        return !instant.isAfter(limiteSuperiore) && !instant.isBefore(MIN_INSTANT);
    }

    public static Instant parse(String date)
    {
        String normalized = date == null ? null : date.trim();
        if (!isValid(normalized))
            throw new IllegalArgumentException(
                    "Data non valida o fuori range: '" + date + "'. " +
                            "Formato atteso: yyyy-MM-dd-HH:mm:ss " +
                            "(es. 2025-06-15-14:30:00)"
            );
        return FORMATTER.parse(normalized, Instant::from);
    }

    public static String format(Instant instant)
    {
        return FORMATTER.format(instant);
    }
}
