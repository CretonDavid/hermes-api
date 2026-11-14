package com.hermes.api.controller;

import com.hermes.api.dto.goal.GoalRequest;
import com.hermes.api.dto.goal.GoalResponse;
import com.hermes.api.dto.goal.GoalUpdateRequest;
import com.hermes.api.dto.goal.GoalUseRequest;
import com.hermes.api.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Budgets spéciaux : objectifs d'épargne mis de côté sur un compte bancaire
@RestController
@RequiredArgsConstructor
@RequestMapping("/goals")
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getAll() {
        return ResponseEntity.ok(goalService.findAll());
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> update(@PathVariable Long id, @Valid @RequestBody GoalUpdateRequest request) {
        return ResponseEntity.ok(goalService.update(id, request));
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<GoalResponse> use(@PathVariable Long id, @Valid @RequestBody GoalUseRequest request) {
        return ResponseEntity.ok(goalService.use(id, request));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<GoalResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(goalService.restore(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        goalService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
