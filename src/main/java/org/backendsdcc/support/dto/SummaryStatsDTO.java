package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SummaryStatsDTO
{
    private BigDecimal totalRevenue;
    private long receiptCount;
    private BigDecimal averageReceipt;
    private long userCount;
    private long adminCount;
}
