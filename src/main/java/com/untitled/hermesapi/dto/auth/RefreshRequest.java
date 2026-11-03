package com.untitled.hermesapi.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Le refresh token est obligatoire")
        @JsonProperty("refresh_token")
        String refreshToken

) {
}
