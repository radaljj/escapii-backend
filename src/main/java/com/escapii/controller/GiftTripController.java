package com.escapii.controller;

import com.escapii.dto.GiftTripInquiryRequest;
import com.escapii.dto.GiftTripInquiryResponse;
import com.escapii.service.GiftTripInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Javni endpoint za slanje gift trip upita.
 * Rate limited: max 3 zahteva po IP na sat (RateLimitingFilter).
 */
@RestController
@RequestMapping("/api/gifts")
@RequiredArgsConstructor
public class GiftTripController {

    private final GiftTripInquiryService tripInquiryService;

    /**
     * POST /api/gifts/trips
     * Kupac šalje upit za gift putovanje (isti flow kao privatni upit, samo + primalac).
     */
    @PostMapping("/trips")
    public ResponseEntity<GiftTripInquiryResponse> submitTripInquiry(
            @Valid @RequestBody GiftTripInquiryRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tripInquiryService.submitInquiry(request));
    }
}
