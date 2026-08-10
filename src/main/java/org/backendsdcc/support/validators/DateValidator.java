package org.backendsdcc.support.validators;

import java.sql.Date;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
public class DateValidator
{
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");

    private static final Instant MIN_INSTANT = Instant.parse("2025-01-01T08:30:00.00Z");

    private DateValidator() {}

    public static boolean isValid(String date)
    {
        if (date == null || date.isBlank())
            return false;
        Instant instant;
        try
        {
            instant = Instant.parse(date);
        } catch (DateTimeException e)
        {
            return false;
        }
        return isValid(instant);
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
        if (!isValid(date))
            throw new IllegalArgumentException(
                    "Data non valida o fuori range: '" + date + "'. " +
                            "Formato atteso: yyyy-MM-dd'T'HH:mm:ss'Z' " +
                            "(es. 2025-06-15T14:30:00Z)"
            );

        return Instant.parse(date);
    }
}
