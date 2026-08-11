package org.backendsdcc.support.validators;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
public class DateValidator
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss").withZone(ZoneOffset.UTC);;

    private static final Instant MIN_INSTANT = Instant.parse("2025-01-01T08:30:00.00Z");

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
        Instant now = Instant.now();
        return !instant.isAfter(now) && !instant.isBefore(MIN_INSTANT);
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
}
