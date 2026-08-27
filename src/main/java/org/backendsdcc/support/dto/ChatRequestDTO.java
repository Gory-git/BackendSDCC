package org.backendsdcc.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChatRequestDTO
{
    @NotBlank(message = "La domanda non puo' essere vuota")
    @Size(max = 500, message = "La domanda e' troppo lunga: massimo 500 caratteri")
    private String question;

    /** Gli scambi precedenti, dal piu' vecchio al piu' recente. Puo' mancare. */
    private List<ChatMessageDTO> history;
}
