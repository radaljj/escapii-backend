package com.escapii.controller;

import com.escapii.dto.DateResponse;
import com.escapii.dto.TermDestinationResponse;
import com.escapii.mapper.DateMapper;
import com.escapii.repository.TermDestinationRepository;
import com.escapii.service.AvailableDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/dates")
@RequiredArgsConstructor
public class DateController {

    private final AvailableDateService      availableDateService;
    private final DateMapper                dateMapper;
    private final TermDestinationRepository termDestinationRepository;

    /**
     * GET /api/dates?airport=BEG
     *
     * Vraća aktivne termine za izabrani aerodrom polaska (Korak 3 forme).
     * Vraća DateResponse DTO — destinacije se dohvataju posebno po terminu.
     */
    @GetMapping
    public ResponseEntity<List<DateResponse>> getDatesByAirport(@RequestParam String airport) {
        return ResponseEntity.ok(
                dateMapper.toResponseList(availableDateService.getActiveDatesByAirport(airport))
        );
    }

    /**
     * GET /api/dates/{id}/destinations
     *
     * Aktivne destinacije za konkretan termin — koristi booking forma na frontendu.
     * Vraća samo destinacije sa active=true za taj termin.
     * 404 → termin ne postoji ili nema aktivnih destinacija
     */
    @GetMapping("/{id}/destinations")
    public ResponseEntity<List<TermDestinationResponse>> getActiveDestinationsForDate(@PathVariable Long id) {
        List<TermDestinationResponse> result = termDestinationRepository.findActiveByDateId(id)
                .stream().map(TermDestinationResponse::new).toList();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/dates/private?token=TOKEN
     *
     * Vraća privatni termin po tokenu (iz linka koji admin pošalje korisniku).
     * Token je UUID bez crtica (32 hex karaktera).
     * 404 → token ne postoji | 410 → link istekao
     */
    @GetMapping("/private")
    public ResponseEntity<DateResponse> getPrivateDate(@RequestParam String token) {
        return ResponseEntity.ok(
                dateMapper.toResponse(availableDateService.getPrivateDateByToken(token))
        );
    }
}
