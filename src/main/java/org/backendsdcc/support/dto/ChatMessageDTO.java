package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Un messaggio della conversazione col chatbot. Il client rimanda lo storico a
 * ogni richiesta: il backend non lo conserva.
 */
@Getter
@Setter
public class ChatMessageDTO
{
    /** "user" oppure "assistant": qualunque altro valore viene letto come "user". */
    private String role;
    private String content;
}
