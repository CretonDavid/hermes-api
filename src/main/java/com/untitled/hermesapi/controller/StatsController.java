package com.untitled.hermesapi.controller;

import com.untitled.hermesapi.dto.stats.*;
import com.untitled.hermesapi.model.enums.CategoryType;
import com.untitled.hermesapi.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Toutes les statistiques acceptent une période (from, to) et une liste optionnelle de comptes.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/summary")
    public ResponseEntity<StatsSummaryResponse> summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.summary(from, to, accountIds));
    }

    @GetMapping("/categories")
    public ResponseEntity<StatsListResponse<CategoryStat>> byCategory(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "EXPENSE") CategoryType type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.byCategory(from, to, type, parentId, accountIds));
    }

    @GetMapping("/monthly")
    public ResponseEntity<StatsListResponse<MonthlyStat>> monthly(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.monthly(from, to, accountIds));
    }

    @GetMapping("/category-trend")
    public ResponseEntity<StatsListResponse<CategoryTrendStat>> categoryTrend(
            @RequestParam Long categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.categoryTrend(categoryId, from, to, accountIds));
    }

    @GetMapping("/payees")
    public ResponseEntity<StatsListResponse<PayeeStat>> topPayees(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.topPayees(from, to, limit, accountIds));
    }

    @GetMapping("/balance-history")
    public ResponseEntity<BalanceHistoryResponse> balanceHistory(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) StatsService.Granularity granularity,
            @RequestParam(required = false) List<Long> accountIds) {
        return ResponseEntity.ok(statsService.balanceHistory(from, to, granularity, accountIds));
    }

    @GetMapping("/savings")
    public ResponseEntity<SavingsStatsResponse> savings(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(statsService.savings(from, to));
    }
}
