package com.escapii.service;

import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.GiftTripInquiryRequest;
import com.escapii.dto.GiftTripInquiryResponse;
import com.escapii.model.InquiryStatus;

import java.math.BigDecimal;
import java.util.List;

public interface GiftTripInquiryService {

    /** Kreira novi gift trip upit (PENDING). Šalje notifikaciju timu. */
    GiftTripInquiryResponse submitInquiry(GiftTripInquiryRequest request);

    /** Admin: lista svih gift trip upita, sortirano po datumu. */
    List<GiftTripInquiryResponse> getAllInquiries();

    /** Admin: menja status upita. */
    GiftTripInquiryResponse updateStatus(Long id, InquiryStatus status);

    /** Admin: postavlja cenu putovanja. */
    GiftTripInquiryResponse updatePrice(Long id, BigDecimal price);

    /** Admin: kreira privatni termin iz gift trip upita (identičan flow kao za CustomDateInquiry). */
    AdminDateResponse createPrivateDateFromGiftTrip(Long tripId, CreatePrivateDateRequest req);
}
