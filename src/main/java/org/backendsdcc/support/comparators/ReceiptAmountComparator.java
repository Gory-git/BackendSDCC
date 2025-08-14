package org.backendsdcc.support.comparators;

import org.backendsdcc.models.Receipt;

import java.util.Comparator;

public class ReceiptAmountComparator implements Comparator<Receipt>
{
    @Override
    public int compare(Receipt o1, Receipt o2)
    {
        return Float.compare(o1.getAmount(), o2.getAmount());
    }
}
