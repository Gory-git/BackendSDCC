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
    /** Ultime quattro cifre della carta, oppure null. Mai il numero completo. */
    private String cardLast4;
    private List<ReceiptLineDTO> lines;
    private String s3Key;
}
