package org.backendsdcc.support.comparators;

import org.backendsdcc.models.Receipt;
import java.time.Instant;
import java.util.Comparator;

public class ReceiptDateComparator implements Comparator<Receipt>
{
    @Override
    public int compare(Receipt o1, Receipt o2)
    {
        Instant date1 = o1.getDate();
        Instant date2 = o2.getDate();

        return date1.compareTo(date2);
    }
}
