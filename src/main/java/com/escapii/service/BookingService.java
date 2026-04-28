package com.escapii.service;

import com.escapii.dto.BookingRequest;
import com.escapii.dto.BookingResponse;
import com.escapii.dto.BookingStatusResponse;
import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;

public interface BookingService {

    /** Čuva kompletan upit u bazu i asinhrono šalje emailove. */
    BookingResponse createBooking(BookingRequest request);

    /** Provjera statusa rezervacije po referentnom broju i prezimenu. */
    BookingStatusResponse lookupStatus(String bookingRef, String lastName);

    /** Kalkuliše cenu bez čuvanja u bazu (Korak 7 forme). */
    PricePreviewResponse previewPrice(
            Long selectedDateId,
            int n,
            AccommodationType accommodationType,
            int exclusionCount,
            int cabinSuitcaseCount,
            boolean hasInsurance,
            boolean hasBreakfast,
            boolean hasSeatsTogether
    );
}
