package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.backendsdcc.models.PaymentMethod;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class PaymentMethodDTO
{
    private PaymentMethod paymentMethod;
}

//TODO vedere usage e capire se serve

