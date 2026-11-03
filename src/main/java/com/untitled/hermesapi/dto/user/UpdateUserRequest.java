package com.untitled.hermesapi.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(

        @NotNull(message = "La devise de référence est obligatoire")
        Long referenceCurrencyId

) {
}
