package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
public class UserDTO
{
    private String name;
    private String surname;
    private String email;
    private String phone;
    private String codiceFiscale;
    private String role;
    private String firebaseUid;
}
