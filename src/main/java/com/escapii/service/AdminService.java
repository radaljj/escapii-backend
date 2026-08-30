package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AgencyCostsRequest;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.AgencyEarningsResponse;
import com.escapii.dto.AgencyRequest;
import com.escapii.dto.AgencyResponse;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationRequest;
import com.escapii.dto.DestinationResponse;
import com.escapii.dto.TermDestinationResponse;
import com.escapii.model.BookingStatus;
import com.escapii.model.InquiryStatus;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AdminService {

    // ── Destinacije ──
    List<DestinationResponse> getAllDestinations();
    DestinationResponse createDestination(DestinationRequest request);
    DestinationResponse updateDestination(Long id, DestinationRequest request);
    void deleteDestination(Long id);
    DestinationResponse uploadDestinationImage(Long id, MultipartFile file);

    // ── Termini ──
    List<AdminDateResponse> getAllDates();
    AdminDateResponse addDate(AdminDateRequest request);
    void toggleActive(Long id, boolean active);

    // ── Per-termin destinacije ──
    List<TermDestinationResponse> getTermDestinations(Long dateId);
    TermDestinationResponse addDestinationToTerm(Long dateId, Long destinationId);
    void removeDestinationFromTerm(Long dateId, Long destinationId);
    TermDestinationResponse toggleTermDestination(Long dateId, Long destinationId, boolean active);
    TermDestinationResponse toggleConnecting(Long dateId, Long destinationId, boolean connecting);
    void updateSlots(Long id, int slots);
    void updatePrice(Long id, int price);
    void deleteDate(Long id);

    /**
     * Pretvara termin u privatni - generiše token, postavlja slots i expiresAt.
     * Vraća ažuriran AdminDateResponse sa privateToken poljem.
     */
    AdminDateResponse makePrivate(Long dateId, int travelers, int expiresInHours, Integer pricePerPerson);

    // ── Rezervacije ──
    List<AdminBookingResponse> getAllBookings();
    AdminBookingResponse updateBookingStatus(Long id, BookingStatus status);
    void deleteBooking(Long id);
    AdminBookingResponse updateAdminNotes(Long id, String adminNotes);
    AdminBookingResponse setDestination(Long id, String destination, boolean force);
    AdminBookingResponse setWeatherCity(Long id, String weatherCity);
    AdminBookingResponse setAirlineName(Long id, String name);
    AdminBookingResponse setAirlineBookingCode(Long id, String code);
    AdminBookingResponse markRevealBoxSent(Long id);

    // ── Fakture ──
    AdminBookingResponse sendInvoice(Long bookingId);
    com.escapii.dto.GiftVoucherResponse sendVoucherInvoice(Long voucherId);

    // ── Dokument rezervacije (od partnerske agencije) ──
    AdminBookingResponse uploadConfirmationDocument(Long bookingId, org.springframework.web.multipart.MultipartFile file);
    AdminBookingResponse resendConfirmationDocument(Long bookingId);

    // ── Upiti za custom termine ──
    List<CustomDateInquiryResponse> getAllInquiries();
    CustomDateInquiryResponse updateInquiryStatus(Long id, InquiryStatus status);
    CustomDateInquiryResponse updateInquiryPrice(Long id, BigDecimal price);
    CustomDateInquiryResponse updateInquiryDate(Long id, LocalDate desiredDepartureDate, Integer nights);
    void deleteInquiry(Long id);

    /**
     * Kreira privatni termin direktno iz podataka upita (atomično - bez race conditiona).
     * Termin je privatan od prvog trenutka; nikad nije javno vidljiv.
     */
    AdminDateResponse createPrivateDateFromInquiry(Long inquiryId, CreatePrivateDateRequest request);

    // ── Agencije ──
    List<AgencyResponse> getAllAgencies();
    List<AgencyResponse> getActiveAgencies();
    AgencyResponse createAgency(AgencyRequest req);
    AgencyResponse updateAgency(Long id, AgencyRequest req);
    AgencyResponse toggleAgencyActive(Long id);
    void assignAgencyToDate(Long dateId, Long agencyId);
    List<AgencyEarningsResponse> getAgencyEarnings();
    /** @deprecated Legacy jedinstveni unos troska. Novi kod koristi {@link #setAgencyCosts(Long, AgencyCostsRequest)}. */
    @Deprecated
    AdminBookingResponse setAgencyCost(Long bookingId, Integer agencyCost);

    // ── Novi obracun sa agencijom (per-booking faktura) ──
    /** Preview obracuna Escapii ↔ agencija za jednu rezervaciju (ide kroz kalkulator). */
    AgencySettlementResponse previewAgencySettlement(Long bookingId);
    /** Admin unosi per-stavku troskove agencije. Vraca svez preview posle unosa. */
    AgencySettlementResponse setAgencyCosts(Long bookingId, AgencyCostsRequest costs);
    /** Finalizuje fakturu Escapii → agencija: generise agencyInvoiceNumber, prelaz na INVOICED. */
    AgencySettlementResponse finalizeAgencyInvoice(Long bookingId);
    /** Rucni prelaz izmedju settlement statusa (INVOICED→PAID, PAID→INVOICED, INVOICED→READY_FOR_INVOICE). */
    AgencySettlementResponse updateSettlementStatus(Long bookingId, com.escapii.model.SettlementStatus newStatus);
    /** Dashboard agregacija za tab "Obracun i fakturisanje agencija". */
    java.util.List<com.escapii.dto.AgencyDashboardRow> agencyDashboard(
            Long agencyId, java.time.LocalDate from, java.time.LocalDate to,
            com.escapii.model.SettlementStatus status);
}
