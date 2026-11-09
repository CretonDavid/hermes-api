package com.hermes.api.controller;

import com.hermes.api.dto.user.DeleteUserRequest;
import com.hermes.api.dto.user.UpdateUserRequest;
import com.hermes.api.dto.user.UserResponse;
import com.hermes.api.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserResponse> getMe() {
        return ResponseEntity.ok(userService.getMe());
    }

    @PatchMapping
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(request));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disable() {
        userService.disable();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteUserRequest request) {
        userService.delete(request.accountNumber());
        return ResponseEntity.noContent().build();
    }
}
