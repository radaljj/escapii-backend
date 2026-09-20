package com.escapii.controller;

import com.escapii.promo.ExclusionPromo;
import com.escapii.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * Promo „besplatno isključivanje destinacija" u admin panelu: kod, datum do kog važi i prekidač.
 * Menja se bez deploya - promo traje mesec-dva, a ako kod procuri gasi se jednim klikom.
 * Zaštićeno kao i ostatak /api/admin/** (AdminKeyFilter).
 */
@RestController
@RequestMapping("/api/admin/promo")
@RequiredArgsConstructor
public class PromoAdminController {

    private final ExclusionPromo exclusionPromo;
    private final BookingRepository bookingRepository;

    /** @param freeCount koliko isključivanja ukupno je besplatno uz kod (2-4); izostavljeno = 3 */
    public record PromoAdminRequest(String code, LocalDate validUntil, boolean enabled, Integer freeCount) {}

    /**
     * @param active        da li promo u ovom trenutku stvarno radi (uključen i nije istekao)
     * @param usedCount     rezervacije sa ovim kodom koje nisu otkazane
     * @param savedTotalEur koliko su te rezervacije ukupno uštedele na isključivanjima
     */
    public record PromoAdminResponse(String code, LocalDate validUntil, boolean enabled, boolean active,
                                     int freeCount, long usedCount, long savedTotalEur) {}

    @GetMapping
    public ResponseEntity<PromoAdminResponse> get() {
        return ResponseEntity.ok(odgovor(exclusionPromo.podesavanja()));
    }

    @PutMapping
    public ResponseEntity<PromoAdminResponse> save(@RequestBody PromoAdminRequest request) {
        try {
            return ResponseEntity.ok(odgovor(
                    exclusionPromo.sacuvaj(request.code(), request.validUntil(), request.enabled(),
                            request.freeCount() != null ? request.freeCount() : 3)));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private PromoAdminResponse odgovor(ExclusionPromo.Podesavanja p) {
        long koriscen = p.kod().isBlank() ? 0 : bookingRepository.countPromoUses(p.kod());
        long usteda   = p.kod().isBlank() ? 0 : bookingRepository.sumPromoSaved(p.kod());
        return new PromoAdminResponse(p.kod(), p.vaziDo(), p.ukljucen(),
                p.aktivan(exclusionPromo.danas()), p.besplatnih(), koriscen, usteda);
    }
}
