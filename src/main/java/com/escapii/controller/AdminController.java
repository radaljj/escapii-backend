package com.escapii.controller;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.AdminDestinationRequest;
import com.escapii.dto.AdminNotesRequest;
import com.escapii.dto.AdminWeatherCityRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.MakePrivateRequest;
import com.escapii.model.BookingStatus;
import com.escapii.model.InquiryStatus;
import com.escapii.config.DailyTaskScheduler;
import com.escapii.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Admin API za upravljanje terminima i rezervacijama.
 * Svi endpointi su zaštićeni X-Admin-Key headerom (AdminKeyFilter).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService       adminService;
    private final DailyTaskScheduler dailyTaskScheduler;

    // ══ DESTINACIJE ══════════════════════════════════════════════════════════

    /** GET /api/admin/destinations - sve destinacije (uključujući neaktivne). */
    @GetMapping("/destinations")
    public ResponseEntity<List<DestinationResponse>> getAllDestinations() {
        return ResponseEntity.ok(adminService.getAllDestinations());
    }

    /** PATCH /api/admin/destinations/{id}/active?value=false - aktiviraj/deaktiviraj destinaciju. */
    @PatchMapping("/destinations/{id}/active")
    public ResponseEntity<Map<String, Object>> toggleDestinationActive(
            @PathVariable Long id,
            @RequestParam boolean value) {
        adminService.toggleDestinationActive(id, value);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "active", value,
                "message", value ? "Destinacija aktivirana" : "Destinacija deaktivirana"
        ));
    }

    // ══ TERMINI ══════════════════════════════════════════════════════════════

    /** GET /api/admin/dates - svi termini sa potencijalnim destinacijama. */
    @GetMapping("/dates")
    public ResponseEntity<List<AdminDateResponse>> getAllDates() {
        return ResponseEntity.ok(adminService.getAllDates());
    }

    /** POST /api/admin/dates - dodaj novi termin. */
    @PostMapping("/dates")
    public ResponseEntity<AdminDateResponse> addDate(@Valid @RequestBody AdminDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addDate(request));
    }

    /** PUT /api/admin/dates/{id}/destinations - ažuriraj potencijalne destinacije. */
    @PutMapping("/dates/{id}/destinations")
    public ResponseEntity<AdminDateResponse> updateDestinations(
            @PathVariable Long id,
            @RequestBody List<Long> destinationIds) {
        return ResponseEntity.ok(adminService.updateDestinations(id, destinationIds));
    }

    /** PATCH /api/admin/dates/{id}/active?value=false - aktiviraj/deaktiviraj termin. */
    @PatchMapping("/dates/{id}/active")
    public ResponseEntity<Map<String, Object>> toggleActive(
            @PathVariable Long id,
            @RequestParam boolean value) {
        adminService.toggleActive(id, value);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "active", value,
                "message", value ? "Termin aktiviran" : "Termin deaktiviran"
        ));
    }

    /** PATCH /api/admin/dates/{id}/slots?value=50 - izmeni broj dostupnih mesta. */
    @PatchMapping("/dates/{id}/slots")
    public ResponseEntity<Map<String, Object>> updateSlots(
            @PathVariable Long id,
            @RequestParam int value) {
        if (value < 0 || value > 9999) {
            return ResponseEntity.badRequest().body(Map.of("error", "Broj mesta mora biti između 0 i 9999."));
        }
        adminService.updateSlots(id, value);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "availableSlots", value,
                "message", "Broj mesta ažuriran na " + value
        ));
    }

    /** PATCH /api/admin/dates/{id}/price?value=299 - izmeni osnovnu cenu termina. */
    @PatchMapping("/dates/{id}/price")
    public ResponseEntity<Map<String, Object>> updatePrice(
            @PathVariable Long id,
            @RequestParam int value) {
        if (value < 1 || value > 9999) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cena mora biti između 1 i 9999€."));
        }
        adminService.updatePrice(id, value);
        return ResponseEntity.ok(Map.of(
                "id", id,
                "basePrice", value,
                "message", "Cena ažurirana na " + value + "€"
        ));
    }

    /** DELETE /api/admin/dates/{id} - trajno obriši termin. */
    @DeleteMapping("/dates/{id}")
    public ResponseEntity<Map<String, String>> deleteDate(@PathVariable Long id) {
        adminService.deleteDate(id);
        return ResponseEntity.ok(Map.of("message", "Termin obrisan"));
    }

    /**
     * POST /api/admin/dates/{id}/make-private
     * Body: { "travelers": 2, "expiresInHours": 48 }
     * Pretvara termin u privatni - generiše token, ograničava slots, postavlja expiresAt.
     * Odgovor sadrži privateToken koji admin kopira i šalje korisniku.
     */
    @PostMapping("/dates/{id}/make-private")
    public ResponseEntity<AdminDateResponse> makeDatePrivate(
            @PathVariable Long id,
            @Valid @RequestBody MakePrivateRequest request) {
        AdminDateResponse response = adminService.makePrivate(
                id,
                request.travelers(),
                request.effectiveExpiry(),
                request.pricePerPerson()
        );
        return ResponseEntity.ok(response);
    }

    // ══ UPITI ZA CUSTOM TERMINE ══════════════════════════════════════════════

    /**
     * GET /api/admin/inquiries - svi upiti za custom termine, najnoviji prvi.
     */
    @GetMapping("/inquiries")
    public ResponseEntity<List<CustomDateInquiryResponse>> getAllInquiries() {
        return ResponseEntity.ok(adminService.getAllInquiries());
    }

    /**
     * POST /api/admin/inquiries/{id}/create-private-date
     * Kreira privatni termin direktno iz podataka upita (atomično, bez race conditiona).
     * Termin je privatan od prvog trenutka - nikad nije javno vidljiv.
     * Body: { "pricePerPerson": 299, "travelers": 2, "expiresInHours": 48 }
     */
    @PostMapping("/inquiries/{id}/create-private-date")
    public ResponseEntity<AdminDateResponse> createPrivateDateFromInquiry(
            @PathVariable Long id,
            @Valid @RequestBody CreatePrivateDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createPrivateDateFromInquiry(id, request));
    }

    /**
     * DELETE /api/admin/inquiries/{id} - trajno obriši upit.
     * Poziva se automatski sa fronta kada se status postavi na PRIVATE_SENT.
     */
    @DeleteMapping("/inquiries/{id}")
    public ResponseEntity<Map<String, String>> deleteInquiry(@PathVariable Long id) {
        adminService.deleteInquiry(id);
        return ResponseEntity.ok(Map.of("message", "Upit obrisan"));
    }

    /**
     * PATCH /api/admin/inquiries/{id}/status?value=PRIVATE_SENT - promeni status upita.
     * Dozvoljene vrednosti: PENDING, PRIVATE_SENT
     */
    @PatchMapping("/inquiries/{id}/status")
    public ResponseEntity<CustomDateInquiryResponse> updateInquiryStatus(
            @PathVariable Long id,
            @RequestParam InquiryStatus value) {
        return ResponseEntity.ok(adminService.updateInquiryStatus(id, value));
    }

    /**
     * PATCH /api/admin/inquiries/{id}/price?value=279.00 - postavi cenu putovanja.
     * Vrednost u EUR (ukupno za sve putnike). Null vrednost briše cenu.
     */
    @PatchMapping("/inquiries/{id}/price")
    public ResponseEntity<CustomDateInquiryResponse> updateInquiryPrice(
            @PathVariable Long id,
            @RequestParam(required = false) BigDecimal value) {
        return ResponseEntity.ok(adminService.updateInquiryPrice(id, value));
    }

    // ══ REZERVACIJE ══════════════════════════════════════════════════════════

    /** GET /api/admin/bookings - sve rezervacije sortirane po datumu (najnovije prve). */
    @GetMapping("/bookings")
    public ResponseEntity<List<AdminBookingResponse>> getAllBookings() {
        return ResponseEntity.ok(adminService.getAllBookings());
    }

    /**
     * DELETE /api/admin/bookings/{id} - trajno briše rezervaciju.
     * Dozvoljeno samo ako rezervacija nikad nije bila CONFIRMED
     * (status != CONFIRMED i oldStatus != CONFIRMED).
     * PENDING i CANCELLED-bez-potvrde rezervacije su briš-eligible.
     */
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Map<String, String>> deleteBooking(@PathVariable Long id) {
        adminService.deleteBooking(id);
        return ResponseEntity.ok(Map.of("message", "Rezervacija obrisana"));
    }

    /** POST /api/admin/scheduler/test - ručno okida jutarnji digest (samo za testiranje). */
    @PostMapping("/scheduler/test")
    public ResponseEntity<Map<String, String>> testScheduler() {
        dailyTaskScheduler.triggerDigest();
        return ResponseEntity.ok(Map.of("status", "Digest je poslan - proveri email."));
    }

    /** PATCH /api/admin/bookings/{id}/status?value=CONFIRMED - promeni status rezervacije. */
    @PatchMapping("/bookings/{id}/status")
    public ResponseEntity<AdminBookingResponse> updateBookingStatus(
            @PathVariable Long id,
            @RequestParam BookingStatus value) {
        return ResponseEntity.ok(adminService.updateBookingStatus(id, value));
    }

    /** PATCH /api/admin/bookings/{id}/notes - sačuvaj internu napomenu (samo za admina). */
    @PatchMapping("/bookings/{id}/notes")
    public ResponseEntity<AdminBookingResponse> updateAdminNotes(
            @PathVariable Long id,
            @Valid @RequestBody AdminNotesRequest body) {
        String notes = body.adminNotes() != null ? body.adminNotes() : "";
        return ResponseEntity.ok(adminService.updateAdminNotes(id, notes));
    }

    /**
     * PATCH /api/admin/bookings/{id}/destination
     * Body: { "destination": "Barcelona" }
     * Admin unosi destinaciju - scheduler je šalje korisniku automatski na T-2 (48h pre polaska).
     */
    @PatchMapping("/bookings/{id}/destination")
    public ResponseEntity<AdminBookingResponse> setDestination(
            @PathVariable Long id,
            @Valid @RequestBody AdminDestinationRequest body) {
        String destination = body.destination() != null ? body.destination() : "";
        return ResponseEntity.ok(adminService.setDestination(id, destination));
    }

    /**
     * PATCH /api/admin/bookings/{id}/weather-city
     * Body: { "weatherCity": "Santa Cruz de Tenerife, Spain" }
     * Opcionalni geocoding hint - ako je destinacija ambigvitetna (npr. "Tenerife"),
     * ovde se upiše precizniji naziv za vremensku prognozu.
     * Prazno polje → brisanje overridea, koristi se assignedDestination.
     */
    @PatchMapping("/bookings/{id}/weather-city")
    public ResponseEntity<AdminBookingResponse> setWeatherCity(
            @PathVariable Long id,
            @Valid @RequestBody AdminWeatherCityRequest body) {
        return ResponseEntity.ok(adminService.setWeatherCity(id, body.weatherCity()));
    }

    /**
     * PATCH /api/admin/bookings/{id}/airline-name
     * Body: { "name": "Wizz Air" }
     * Naziv avio kompanije - prikazuje se korisniku na reveal stranici.
     */
    @PatchMapping("/bookings/{id}/airline-name")
    public ResponseEntity<AdminBookingResponse> setAirlineName(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(adminService.setAirlineName(id, body.get("name")));
    }

    /**
     * PATCH /api/admin/bookings/{id}/airline-code
     * Body: { "code": "ABC123" }
     * Kod avio kompanije za check-in - prikazuje se korisniku na reveal stranici.
     */
    @PatchMapping("/bookings/{id}/airline-code")
    public ResponseEntity<AdminBookingResponse> setAirlineBookingCode(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(adminService.setAirlineBookingCode(id, body.get("code")));
    }

    /**
     * POST /api/admin/bookings/{id}/send-reveal
     * Ručno šalje reveal email. Ako je već poslato → 409 Conflict.
     * Header X-Frontend-Url: URL WordPress sajta koji okida request
     * (npr. http://escapiitest.great-site.net ili https://escapii.rs).
     * Ako header nije prisutan, koristi konfigurisani app.frontend-url.
     */
    @PostMapping("/bookings/{id}/send-reveal")
    public ResponseEntity<Map<String, String>> sendRevealManual(
            @PathVariable Long id,
            @RequestHeader(value = "X-Frontend-Url", required = false) String siteUrl) {
        return ResponseEntity.ok(dailyTaskScheduler.sendRevealForBooking(id, siteUrl));
    }

    /**
     * POST /api/admin/bookings/{id}/send-forecast
     * Ručno šalje forecast email. Ako je već poslato → 409 Conflict.
     */
    @PostMapping("/bookings/{id}/send-forecast")
    public ResponseEntity<Map<String, String>> sendForecastManual(@PathVariable Long id) {
        return ResponseEntity.ok(dailyTaskScheduler.sendForecastForBooking(id));
    }

    /**
     * POST /api/admin/bookings/{id}/reveal-box-sent
     * Označava da je Reveal Box fizički poslan korisniku.
     * Nakon ovoga auto-reveal email se neće poslati.
     */
    @PostMapping("/bookings/{id}/reveal-box-sent")
    public ResponseEntity<AdminBookingResponse> markRevealBoxSent(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.markRevealBoxSent(id));
    }

}
