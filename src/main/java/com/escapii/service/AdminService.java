package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationRequest;
import com.escapii.dto.DestinationResponse;
import com.escapii.dto.TermDestinationResponse;
import com.escapii.model.BookingStatus;
import com.escapii.model.InquiryStatus;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
    AdminBookingResponse setDestination(Long id, String destination);
    AdminBookingResponse setWeatherCity(Long id, String weatherCity);
    AdminBookingResponse setAirlineName(Long id, String name);
    AdminBookingResponse setAirlineBookingCode(Long id, String code);
    AdminBookingResponse markRevealBoxSent(Long id);

    // ── Fakture ──
    AdminBookingResponse sendInvoice(Long bookingId);
    com.escapii.dto.GiftVoucherResponse sendVoucherInvoice(Long voucherId);

    // ── Upiti za custom termine ──
    List<CustomDateInquiryResponse> getAllInquiries();
    CustomDateInquiryResponse updateInquiryStatus(Long id, InquiryStatus status);
    CustomDateInquiryResponse updateInquiryPrice(Long id, BigDecimal price);
    void deleteInquiry(Long id);

    /**
     * Kreira privatni termin direktno iz podataka upita (atomično - bez race conditiona).
     * Termin je privatan od prvog trenutka; nikad nije javno vidljiv.
     */
    AdminDateResponse createPrivateDateFromInquiry(Long inquiryId, CreatePrivateDateRequest request);
}
