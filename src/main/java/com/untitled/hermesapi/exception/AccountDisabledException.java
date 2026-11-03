package com.untitled.hermesapi.exception;

import org.springframework.http.HttpStatus;

public class AccountDisabledException extends ApplicationException {

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Ce compte utilisateur est désactivé");
    }
}
