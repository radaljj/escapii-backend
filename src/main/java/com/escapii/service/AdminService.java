package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.AdminDateRequest;
import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.dto.DestinationResponse;
import com.escapii.model.BookingStatus;
import com.escapii.model.InquiryStatus;

import java.math.BigDecimal;
import java.util.List;

public interface AdminService {

    // ── Destinacije ──
    List<DestinationResponse> getAllDestinations();
    void toggleDestinationActive(Long id, boolean active);

    // ── Termini ──
    List<AdminDateResponse> getAllDates();
    AdminDateResponse addDate(AdminDateRequest request);
    AdminDateResponse updateDestinations(Long id, List<Long> destinationIds);
    void toggleActive(Long id, boolean active);
    void updateSlots(Long id, int slots);
    void deleteDate(Long id);

    /**
     * Pretvara termin u privatni — generiše token, postavlja slots i expiresAt.
     * Vraća ažuriran AdminDateResponse sa privateToken poljem.
     */
    AdminDateResponse makePrivate(Long dateId, int travelers, int expiresInHours, Integer pricePerPerson);

    // ── Rezervacije ──
    List<AdminBookingResponse> getAllBookings();
    AdminBookingResponse updateBookingStatus(Long id, BookingStatus status);
    AdminBookingResponse updateAdminNotes(Long id, String adminNotes);
    AdminBookingResponse setDestination(Long id, String destination);
    AdminBookingResponse setWeatherCity(Long id, String weatherCity);
    AdminBookingResponse setAirlineName(Long id, String name);
    AdminBookingResponse setAirlineBookingCode(Long id, String code);

    // ── Upiti za custom termine ──
    List<CustomDateInquiryResponse> getAllInquiries();
    CustomDateInquiryResponse updateInquiryStatus(Long id, InquiryStatus status);
    CustomDateInquiryResponse updateInquiryPrice(Long id, BigDecimal price);

    /**
     * Kreira privatni termin direktno iz podataka upita (atomično — bez race conditiona).
     * Termin je privatan od prvog trenutka; nikad nije javno vidljiv.
     */
    AdminDateResponse createPrivateDateFromInquiry(Long inquiryId, CreatePrivateDateRequest request);
}
