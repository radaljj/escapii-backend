package com.escapii.controller;

import com.escapii.dto.DepartureAirportResponse;
import com.escapii.model.DepartureAirport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Javna lista aerodroma polaska.
 *
 * Frontend (kartice u koraku 1, poklon sekcija) i admin panel (dropdown termina,
 * checkboxovi destinacija, kartice liste čekanja) crtaju se iz ovoga - nema
 * hardkodovanih aerodroma ni na jednom od tih mesta.
 */
@RestController
@RequestMapping("/api/airports")
public class AirportController {

    /** GET /api/airports - svi aerodromi polaska, redosledom iz definicije. */
    @GetMapping
    public ResponseEntity<List<DepartureAirportResponse>> getAirports() {
        return ResponseEntity.ok(
                Arrays.stream(DepartureAirport.values())
                        .map(DepartureAirportResponse::of)
                        .toList()
        );
    }
}
