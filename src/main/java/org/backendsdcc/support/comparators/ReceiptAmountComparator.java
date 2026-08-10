package org.backendsdcc.support.comparators;

import org.backendsdcc.models.Receipt;
import java.math.BigDecimal;
import java.util.Comparator;

public class ReceiptAmountComparator implements Comparator<Receipt>
{
    @Override
    public int compare(Receipt o1, Receipt o2)
    {
        return o1.getAmount().compareTo(o2.getAmount());
    }
}
