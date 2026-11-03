package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.auth.LoginRequest;
import com.untitled.hermesapi.dto.auth.LoginResponse;
import com.untitled.hermesapi.dto.auth.RefreshRequest;
import com.untitled.hermesapi.dto.auth.RegisterResponse;
import com.untitled.hermesapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register() {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/reactivate")
    public ResponseEntity<LoginResponse> reactivate(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.reactivate(request));
    }
}
