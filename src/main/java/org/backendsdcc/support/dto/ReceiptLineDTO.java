package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.backendsdcc.models.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class ReceiptLineDTO
{
    private ProductDTO product;
    private Integer quantity;
    private BigDecimal price;
}
