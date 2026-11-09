package com.hermes.api.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

public class AccountDisabledException extends ApplicationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountDisabledException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Ce compte utilisateur est désactivé");
    }
}
