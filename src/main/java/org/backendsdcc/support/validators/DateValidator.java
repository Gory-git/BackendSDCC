package org.backendsdcc.support.validators;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.StringTokenizer;

public class DateValidator
{
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");

    public static boolean isValid(String date)
    {
        StringTokenizer stringTokenizer = new StringTokenizer(date, "-:");
        if (stringTokenizer.countTokens() != 6)
            return false;
        String year = stringTokenizer.nextToken();
        if (year.length() != 4)
            return false;
        String month = stringTokenizer.nextToken();
        if (month.length() != 2 || Integer.parseInt(month) > 12 ||  Integer.parseInt(month) <= 0)
            return false;
        String day = stringTokenizer.nextToken();
        if (day.length() != 2 || Integer.parseInt(day) > 31 ||  Integer.parseInt(day) <= 0) // TODO gestire meglio
            return false;
        String hour = stringTokenizer.nextToken();
        if (hour.length() != 2 || Integer.parseInt(hour) >= 24 || Integer.parseInt(hour) < 0)
            return false;
        String minute = stringTokenizer.nextToken();
        if (minute.length() != 2 || Integer.parseInt(minute) >= 60 || Integer.parseInt(minute) < 0)
            return false;
        String second = stringTokenizer.nextToken();
        if (second.length() != 2 || Integer.parseInt(second) >= 60 ||   Integer.parseInt(second) < 0)
            return false;

        LocalDateTime dateTime = LocalDateTime.parse(date, formatter);
        LocalDateTime minTime = LocalDateTime.parse("2025-01-01-08:30:00", formatter);
        return !dateTime.isAfter(LocalDateTime.now()) && !dateTime.isBefore(minTime);
    }

    public static boolean isValid(Date date)
    {
        if (date == null)
            return false;
        return isValid(date.toString());
    }

    public static Date parse(String date)
    {
        if (isValid(date))
            throw new RuntimeException("Invalid date");

        LocalDateTime dateTime = LocalDateTime.parse(date, formatter);
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
