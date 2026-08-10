package org.backendsdcc.support.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductDTO
{
    @NotBlank(message = "Il nome del prodotto è obbligatorio")
    private String name;

    @NotBlank(message = "Il codice del prodotto è obbligatorio")
    private String code;

    @NotNull(message = "Il prezzo è obbligatorio")
    @DecimalMin(value = "0.00", inclusive = false, message = "Il prezzo deve essere maggiore di zero")
    private BigDecimal price;
}

// TODO valutare l'utilità di gestire le dimensioni massime