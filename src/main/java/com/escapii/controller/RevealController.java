package com.escapii.controller;

import com.escapii.service.RevealService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Javni endpoint za otkrivanje destinacije.
 * NIJE zaštićen admin ključem — dostupan svima sa validnim tokenom.
 */
@Slf4j
@RestController
@RequestMapping("/api/reveal")
@RequiredArgsConstructor
public class RevealController {

    private final RevealService revealService;

    /**
     * GET /api/reveal?token=abc123
     *
     * Validacije (sve mora proći):
     *  1. Token postoji u bazi
     *  2. Booking status == CONFIRMED
     *  3. assignedDestination nije null/prazan
     *  4. Datum polaska nije prošao (link važi do dana polaska)
     *
     * Vraća samo destinaciju i ime putnika — ništa osjetljivo.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> reveal(@RequestParam String token) {
        return ResponseEntity.ok(revealService.getRevealInfo(token));
    }
}
