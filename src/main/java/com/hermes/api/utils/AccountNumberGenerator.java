package com.hermes.api.utils;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

    private static final int LENGTH = 30;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder passkey = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            passkey.append(secureRandom.nextInt(10));
        }

        return passkey.toString();
    }
}
