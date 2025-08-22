package org.backendsdcc.support.comparators;

import org.antlr.v4.runtime.misc.NotNull;
import org.backendsdcc.models.Receipt;

import java.util.Comparator;
import java.util.Date;
import java.util.StringTokenizer;

public class ReceiptDateComparator implements Comparator<Receipt>
{
    @Override
    public int compare(Receipt o1, Receipt o2)
    {
        Date date1 = o1.getDate();
        Date date2 = o2.getDate();

        return date1.compareTo(date2);
    }
}
