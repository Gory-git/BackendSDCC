package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UserStatDTO
{
    private String email;
    private String name;
    private String surname;
    private BigDecimal totalSpent;
    private long receiptCount;
}
