package com.escapii.controller;

import com.escapii.dto.CustomDateInquiryRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.service.CustomDateInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Javni endpoint za slanje upita o custom terminu.
 * Korisnik bira datum koji ne postoji u ponudi - upit stiže adminu na pregled.
 */
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final CustomDateInquiryService inquiryService;

    /**
     * POST /api/inquiries/custom-date
     * Korisnik šalje upit za custom termin (datum, broj noćenja, putnici, email, napomena).
     * Rate limited: max 3 zahteva po IP na sat (RateLimitingFilter).
     */
    @PostMapping("/custom-date")
    public ResponseEntity<CustomDateInquiryResponse> submitCustomDateInquiry(
            @Valid @RequestBody CustomDateInquiryRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(inquiryService.submitInquiry(request));
    }
}
