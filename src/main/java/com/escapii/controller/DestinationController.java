package com.escapii.controller;

import com.escapii.dto.CountryDto;
import com.escapii.dto.DestinationResponse;
import com.escapii.mapper.DestinationMapper;
import com.escapii.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;
    private final DestinationMapper destinationMapper;

    /**
     * GET /api/destinations
     * GET /api/destinations?airport=BEG
     *
     * Vraća destinacije po aerodromu (za carousel/prikaz). Booking forma koristi
     * GET /api/dates/{id}/destinations za per-termin aktivne destinacije.
     */
    @GetMapping
    public ResponseEntity<List<DestinationResponse>> getDestinations(
            @RequestParam(required = false) String airport) {
        return ResponseEntity.ok(
                destinationMapper.toResponseList(destinationService.getDestinationsByAirport(airport))
        );
    }

    /** GET /api/destinations/all - sve destinacije uključujući neaktivne (za carousel). */
    @GetMapping("/all")
    public ResponseEntity<List<DestinationResponse>> getAllDestinations() {
        return ResponseEntity.ok(
                destinationMapper.toResponseList(destinationService.getAllDestinations())
        );
    }

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        return destinationService.fetchCountries();
    }
}
