package org.backendsdcc.support.comparators;

import org.antlr.v4.runtime.misc.NotNull;
import org.backendsdcc.models.Receipt;

import java.util.Comparator;
import java.util.StringTokenizer;

public class ReceiptDateComparator implements Comparator<Receipt>
{
    @Override
    public int compare(Receipt o1, Receipt o2)
    {
        String date1 = o1.getDate();
        StringTokenizer st1 = new StringTokenizer(date1, "-:");
        int seconds1 = Integer.parseInt(st1.nextToken());
        int minutes1 = Integer.parseInt(st1.nextToken());
        int hour1 = Integer.parseInt(st1.nextToken());
        int day1 = Integer.parseInt(st1.nextToken());
        int month1 = Integer.parseInt(st1.nextToken());
        int year1 = Integer.parseInt(st1.nextToken());

        String date2 = o2.getDate();
        StringTokenizer st2 = new StringTokenizer(date2, "-:");
        int seconds2 = Integer.parseInt(st2.nextToken());
        int minutes2 = Integer.parseInt(st2.nextToken());
        int hour2 = Integer.parseInt(st2.nextToken());
        int day2 = Integer.parseInt(st2.nextToken());
        int month2 = Integer.parseInt(st2.nextToken());
        int year2 = Integer.parseInt(st2.nextToken());

        if (year1 > year2)
            return 1;
        if (year1 == year2 && month1 > month2)
            return 1;
        if (year1 == year2 && month1 == month2 &&  day1 > day2)
            return 1;
        if  (year1 == year2 && month1 == month2 && day1 == day2 && hour1 > hour2)
            return 1;
        if (year1 == year2 && month1 == month2 && day1 == day2 && hour1 == hour2 && minutes1 > minutes2)
            return 1;
        if (year1 == year2 && month1 == month2 && day1 == day2 && hour1 == hour2 && minutes1 == minutes2 && seconds1 > seconds2)
            return 1;
        if (year1 == year2 && month1 == month2 && day1 == day2 && hour1 == hour2 && minutes1 == minutes2 && seconds1 == seconds2)
            return 0;
        return -1;
    }
}
