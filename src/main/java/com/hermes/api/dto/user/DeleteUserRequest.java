package com.hermes.api.dto.user;

import jakarta.validation.constraints.NotBlank;

// Le numéro de compte est redemandé pour confirmer la suppression définitive
public record DeleteUserRequest(

        @NotBlank(message = "Le numéro d'accès est obligatoire")
        String accountNumber

) {
}
