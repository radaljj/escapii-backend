package com.escapii.controller;

import com.escapii.dto.DateResponse;
import com.escapii.mapper.DateMapper;
import com.escapii.service.AvailableDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dates")
@RequiredArgsConstructor
public class DateController {

    private final AvailableDateService availableDateService;
    private final DateMapper dateMapper;

    /**
     * GET /api/dates?airport=BEG
     *
     * Vraća aktivne termine za izabrani aerodrom polaska (Korak 3 forme).
     * Vraća DateResponse DTO — potentialDestinations nisu exposovane.
     */
    @GetMapping
    public ResponseEntity<List<DateResponse>> getDatesByAirport(@RequestParam String airport) {
        return ResponseEntity.ok(
                dateMapper.toResponseList(availableDateService.getActiveDatesByAirport(airport))
        );
    }
}
