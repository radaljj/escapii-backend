package com.escapii.controller;

import com.escapii.model.AppError;
import com.escapii.service.AppErrorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoint za pregled i upravljanje greškama aplikacije.
 * Zaštićeno AdminKeyFilter-om kao i ostali /api/admin/** endpointi.
 */
@RestController
@RequestMapping("/api/admin/errors")
@RequiredArgsConstructor
public class AppErrorController {

    private final AppErrorService appErrorService;

    /** Sve greške, najnovije prve. */
    @GetMapping
    public ResponseEntity<List<AppError>> getAll() {
        return ResponseEntity.ok(appErrorService.getAll());
    }

    /** Broj nerešenih grešaka — za badge. */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countUnresolved() {
        return ResponseEntity.ok(Map.of("count", appErrorService.countUnresolved()));
    }

    /** Označi grešku kao rešenu. */
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable Long id) {
        appErrorService.resolve(id);
        return ResponseEntity.ok(Map.of("resolved", true));
    }

    /** Obriši sve rešene greške. */
    @DeleteMapping("/resolved")
    public ResponseEntity<Map<String, Object>> deleteResolved() {
        appErrorService.deleteResolved();
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /** Obriši sve greške. */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAll() {
        appErrorService.deleteAll();
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /** TEST ONLY — namerno baca exception da testiramo monitoring. Obrisati nakon testa! */
    @GetMapping("/trigger-test-error")
    public ResponseEntity<Void> triggerTestError() {
        throw new RuntimeException("TEST: Ovo je namerna test greška za proveru error monitoringa 🚨");
    }
}
