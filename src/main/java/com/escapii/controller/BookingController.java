package com.escapii.controller;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.BookingResponse;
import com.escapii.dto.BookingStatusResponse;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/booking
     * Rate limiting: 5 puta po IP/sat (RateLimitingFilter).
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    /**
     * GET /api/booking/status?ref=ESC-xxx&lastName=Markovic
     * Javni endpoint — korisnik provjerava status rezervacije bez login-a.
     */
    @GetMapping("/status")
    public ResponseEntity<BookingStatusResponse> getStatus(
            @RequestParam String ref,
            @RequestParam String lastName
    ) {
        return ResponseEntity.ok(bookingService.lookupStatus(ref, lastName));
    }

    /**
     * GET /api/booking/price-preview
     * Kalkulacija cene bez čuvanja u bazu (Korak 7 forme).
     */
    @GetMapping("/price-preview")
    public ResponseEntity<PricePreviewResponse> pricePreview(
            @RequestParam                            Long    selectedDateId,
            @RequestParam(defaultValue = "1")        int     numberOfTravelers,
            @RequestParam(defaultValue = "STANDARD") AccommodationType accommodationType,
            @RequestParam(defaultValue = "0")        int     exclusionCount,
            @RequestParam(defaultValue = "0")        int     cabinSuitcaseCount,
            @RequestParam(defaultValue = "false")    boolean hasInsurance,
            @RequestParam(defaultValue = "false")    boolean hasBreakfast,
            @RequestParam(defaultValue = "false")    boolean hasSeatsTogether
    ) {
        return ResponseEntity.ok(bookingService.previewPrice(
                selectedDateId, numberOfTravelers, accommodationType, exclusionCount,
                cabinSuitcaseCount, hasInsurance, hasBreakfast, hasSeatsTogether
        ));
    }
}
