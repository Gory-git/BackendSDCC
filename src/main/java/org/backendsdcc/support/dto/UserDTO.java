package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Date;

@Getter
@Setter
@Component
public class UserDTO
{
    private String name;
    private String surname;
    private String email;
    private Date dateOfBirth;
    private String role;
}
