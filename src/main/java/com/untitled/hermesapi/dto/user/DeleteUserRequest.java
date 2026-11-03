package com.untitled.hermesapi.dto.user;

import jakarta.validation.constraints.NotBlank;

// Le numéro de compte est redemandé pour confirmer la suppression définitive
public record DeleteUserRequest(

        @NotBlank(message = "Le numéro de compte est obligatoire")
        String accountNumber

) {
}
