package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponseDTO
{
    private String answer;

    public ChatResponseDTO(String answer)
    {
        this.answer = answer;
    }
}
