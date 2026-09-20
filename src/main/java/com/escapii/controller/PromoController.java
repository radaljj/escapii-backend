package com.escapii.controller;

import com.escapii.promo.ExclusionPromo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Javna provera promo koda iz forme za rezervaciju (isto polje kao poklon vaučer; sajt po obliku
 * koda bira koji endpoint zove - vaučeri su ESC-XXXX-XXXX-XXXX).
 *
 * <p>Odgovor samo kaže da li kod važi i do kad - cena se i dalje računa isključivo kroz
 * /api/booking/price-preview i pri samoj rezervaciji. Greška je uniformna: ne otkriva da li kod
 * postoji, da li je istekao ili je promo ugašen. Rate limit: RateLimitingFilter.
 */
@RestController
@RequestMapping("/api/promo")
@RequiredArgsConstructor
public class PromoController {

    private final ExclusionPromo exclusionPromo;

    public record PromoValidateRequest(
            @NotBlank(message = "Promo kod je obavezan")
            @Size(max = 40, message = "Promo kod nije važeći")
            String code) {}

    /**
     * @param kind       vrsta pogodnosti; za sada samo EXCLUSIONS_FREE (isključivanje destinacija besplatno)
     * @param validUntil poslednji dan važenja, za tekst na sajtu
     */
    public record PromoValidateResponse(boolean valid, String kind, LocalDate validUntil, String message) {
        static PromoValidateResponse ok(LocalDate validUntil) {
            return new PromoValidateResponse(true, "EXCLUSIONS_FREE", validUntil, null);
        }
        static PromoValidateResponse invalid() {
            return new PromoValidateResponse(false, null, null, "Promo kod nije važeći.");
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<PromoValidateResponse> validate(@Valid @RequestBody PromoValidateRequest request) {
        if (!exclusionPromo.vazi(request.code())) {
            return ResponseEntity.ok(PromoValidateResponse.invalid());
        }
        return ResponseEntity.ok(PromoValidateResponse.ok(exclusionPromo.podesavanja().vaziDo()));
    }
}
