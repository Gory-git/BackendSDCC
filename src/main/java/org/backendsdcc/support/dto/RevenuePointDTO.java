package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RevenuePointDTO
{
    private String date; // yyyy-MM-dd
    private BigDecimal total;
    private long count;

    public RevenuePointDTO(String date, BigDecimal total, long count)
    {
        this.date = date;
        this.total = total;
        this.count = count;
    }
}
