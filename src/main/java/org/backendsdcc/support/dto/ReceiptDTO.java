package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
public class ReceiptDTO
{
    private String code;
    private float amount;
    private float tax;
    private String date;
    private String paymentMethod;
    private String userEmail;
    private List<ProductDTO> products;
    private List<Integer> quantities;
}
