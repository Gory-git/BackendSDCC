package org.backendsdcc.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Campi che l'utente può modificare del proprio profilo. Volutamente separato da
 * {@link UserDTO}: quello trasporta anche email, role e firebaseUid, che non devono
 * mai poter arrivare dal body di una richiesta di aggiornamento.
 *
 * Le regex sono le stesse usate dal form di registrazione lato frontend. Il ramo
 * opzionale "(...)?" serve perché @Pattern salta i null ma non le stringhe vuote, e
 * svuotare telefono o codice fiscale è un'operazione legittima.
 */
@Getter
@Setter
public class UserUpdateDTO
{
    @NotBlank(message = "Il nome è obbligatorio.")
    private String name;

    @NotBlank(message = "Il cognome è obbligatorio.")
    private String surname;

    @Pattern(regexp = "^(\\+?[\\d\\s\\-()]{7,15})?$", message = "Formato telefono non valido.")
    private String phone;

    @Pattern(regexp = "^([A-Za-z0-9]{16})?$", message = "Il codice fiscale deve avere 16 caratteri alfanumerici.")
    private String codiceFiscale;
}
