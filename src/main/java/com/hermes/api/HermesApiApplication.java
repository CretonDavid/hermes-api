package com.hermes.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// L'authentification passe uniquement par les JWT : pas d'utilisateur en mémoire généré par Spring
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HermesApiApplication {

    static void main(String[] args) {
        SpringApplication.run(HermesApiApplication.class, args);
    }

}
