package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.backendsdcc.models.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@Component
public class UserDTO
{
    private String name;
    private String surname;
    private String cf;
    private String email;
    private Date dateOfBirth;
    private String role;
    private List<PaymentMethod> paymentMethods;
}
