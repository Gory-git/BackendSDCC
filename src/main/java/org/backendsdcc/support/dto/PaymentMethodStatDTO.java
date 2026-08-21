package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.backendsdcc.models.PaymentMethod;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentMethodStatDTO
{
    private PaymentMethod paymentMethod;
    private long count;
    private BigDecimal total;
}
