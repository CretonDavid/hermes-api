package com.untitled.hermesapi.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("refresh_token")
        String refreshToken

) {
}
