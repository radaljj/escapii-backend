package com.escapii.service;

import com.escapii.dto.CustomDateInquiryRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.model.InquiryStatus;

import java.util.List;

public interface CustomDateInquiryService {

    /** Sačuvaj novi upit korisnika. Vraća kreiran upit. */
    CustomDateInquiryResponse submitInquiry(CustomDateInquiryRequest request);

    /** Svi upiti (za admin panel), sortirani po datumu kreiranja. */
    List<CustomDateInquiryResponse> getAllInquiries();

    /** Promeni status upita (admin akcija). */
    CustomDateInquiryResponse updateStatus(Long id, InquiryStatus status);
}
