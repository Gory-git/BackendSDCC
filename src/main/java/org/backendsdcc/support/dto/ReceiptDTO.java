package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.backendsdcc.models.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ReceiptDTO
{
    private String code;
    private BigDecimal amount;
    private BigDecimal tax;
    private Instant date;
    private PaymentMethod paymentMethod;
    private String userEmail;
    private List<ReceiptLineDTO> lines;
}
