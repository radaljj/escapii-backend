package com.escapii.controller;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.AdminDestinationRequest;
import com.escapii.dto.AdminNotesRequest;
import com.escapii.dto.AdminWeatherCityRequest;
import com.escapii.dto.DestinationResponse;
import com.escapii.model.BookingStatus;
import com.escapii.config.DailyTaskScheduler;
import com.escapii.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** GET /api/admin/destinations — sve destinacije (uključujući neaktivne). */
    @GetMapping("/destinations")
    public ResponseEntity<List<DestinationResponse>> getAllDestinations() {
        return ResponseEntity.ok(adminService.getAllDestinations());
    }

    /** PATCH /api/admin/destinations/{id}/active?value=false — aktiviraj/deaktiviraj destinaciju. */
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

    /** GET /api/admin/dates — svi termini sa potencijalnim destinacijama. */
    @GetMapping("/dates")
    public ResponseEntity<List<AdminDateResponse>> getAllDates() {
        return ResponseEntity.ok(adminService.getAllDates());
    }

    /** POST /api/admin/dates — dodaj novi termin. */
    @PostMapping("/dates")
    public ResponseEntity<AdminDateResponse> addDate(@Valid @RequestBody AdminDateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.addDate(request));
    }

    /** PUT /api/admin/dates/{id}/destinations — ažuriraj potencijalne destinacije. */
    @PutMapping("/dates/{id}/destinations")
    public ResponseEntity<AdminDateResponse> updateDestinations(
            @PathVariable Long id,
            @RequestBody List<Long> destinationIds) {
        return ResponseEntity.ok(adminService.updateDestinations(id, destinationIds));
    }

    /** PATCH /api/admin/dates/{id}/active?value=false — aktiviraj/deaktiviraj termin. */
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

    /** PATCH /api/admin/dates/{id}/slots?value=50 — izmeni broj dostupnih mesta. */
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

    /** DELETE /api/admin/dates/{id} — trajno obriši termin. */
    @DeleteMapping("/dates/{id}")
    public ResponseEntity<Map<String, String>> deleteDate(@PathVariable Long id) {
        adminService.deleteDate(id);
        return ResponseEntity.ok(Map.of("message", "Termin obrisan"));
    }

    // ══ REZERVACIJE ══════════════════════════════════════════════════════════

    /** GET /api/admin/bookings — sve rezervacije sortirane po datumu (najnovije prve). */
    @GetMapping("/bookings")
    public ResponseEntity<List<AdminBookingResponse>> getAllBookings() {
        return ResponseEntity.ok(adminService.getAllBookings());
    }

    /** POST /api/admin/scheduler/test — ručno okida jutarnji digest (samo za testiranje). */
    @PostMapping("/scheduler/test")
    public ResponseEntity<Map<String, String>> testScheduler() {
        dailyTaskScheduler.triggerDigest();
        return ResponseEntity.ok(Map.of("status", "Digest je poslan — proveri email."));
    }

    /** PATCH /api/admin/bookings/{id}/status?value=CONFIRMED — promeni status rezervacije. */
    @PatchMapping("/bookings/{id}/status")
    public ResponseEntity<AdminBookingResponse> updateBookingStatus(
            @PathVariable Long id,
            @RequestParam BookingStatus value) {
        return ResponseEntity.ok(adminService.updateBookingStatus(id, value));
    }

    /** PATCH /api/admin/bookings/{id}/notes — sačuvaj internu napomenu (samo za admina). */
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
     * Admin unosi destinaciju — scheduler je šalje korisniku automatski na T-3.
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
     * Opcionalni geocoding hint — ako je destinacija ambigvitetna (npr. "Tenerife"),
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
     * PATCH /api/admin/bookings/{id}/airline-code
     * Body: { "code": "ABC123" }
     * Kod avio kompanije za check-in — prikazuje se korisniku na reveal stranici.
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

}
