package com.escapii.service.impl;

import com.escapii.dto.CustomDateInquiryRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.model.CustomDateInquiry;
import com.escapii.model.InquiryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.CustomDateInquiryService;
import com.escapii.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomDateInquiryServiceImpl implements CustomDateInquiryService {

    private final CustomDateInquiryRepository inquiryRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public CustomDateInquiryResponse submitInquiry(CustomDateInquiryRequest req) {
        CustomDateInquiry inquiry = new CustomDateInquiry();
        inquiry.setAirport(req.airport().trim().toUpperCase());
        inquiry.setTravelers(req.travelers());
        inquiry.setDesiredDepartureDate(req.desiredDepartureDate());
        inquiry.setNights(req.nights());
        inquiry.setEmail(req.email().trim().toLowerCase());
        inquiry.setNotes(req.notes() != null ? req.notes().trim() : null);

        CustomDateInquiry saved = inquiryRepository.save(inquiry);
        log.info("[Inquiry] Nov upit: id={}, airport={}, travelers={}, datum={}, email={}",
                saved.getId(), saved.getAirport(), saved.getTravelers(),
                saved.getDesiredDepartureDate(), saved.getEmail());

        notificationService.newInquiry(saved.getEmail(), saved.getAirport());
        return new CustomDateInquiryResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomDateInquiryResponse> getAllInquiries() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CustomDateInquiryResponse::new)
                .toList();
    }

    @Override
    @Transactional
    public CustomDateInquiryResponse updateStatus(Long id, InquiryStatus status) {
        CustomDateInquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Upit sa ID=" + id + " nije pronađen."));
        inquiry.setStatus(status);
        inquiry.setClosedAt(status == InquiryStatus.CLOSED ? LocalDateTime.now() : null);
        log.info("[Inquiry] Status upita id={} promenjen na {}", id, status);
        return new CustomDateInquiryResponse(inquiryRepository.save(inquiry));
    }

    @Override
    @Transactional
    public CustomDateInquiryResponse updatePrice(Long id, BigDecimal price) {
        CustomDateInquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Upit sa ID=" + id + " nije pronađen."));
        inquiry.setPrice(price);
        log.info("[Inquiry] Cena upita id={} postavljena na {}", id, price);
        return new CustomDateInquiryResponse(inquiryRepository.save(inquiry));
    }
}
