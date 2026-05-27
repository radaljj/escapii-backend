package com.escapii.service.impl;

import com.escapii.dto.AdminDateResponse;
import com.escapii.dto.CreatePrivateDateRequest;
import com.escapii.dto.GiftTripInquiryRequest;
import com.escapii.dto.GiftTripInquiryResponse;
import com.escapii.model.AvailableDate;
import com.escapii.model.GiftTripInquiry;
import com.escapii.model.InquiryStatus;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.repository.GiftTripInquiryRepository;
import com.escapii.service.GiftTripInquiryService;
import com.escapii.service.email.GiftTripEmailService;
import com.escapii.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftTripInquiryServiceImpl implements GiftTripInquiryService {

    private final GiftTripInquiryRepository inquiryRepository;
    private final AvailableDateRepository   availableDateRepository;
    private final GiftTripEmailService emailService;

    @Override
    @Transactional
    public GiftTripInquiryResponse submitInquiry(GiftTripInquiryRequest req) {
        GiftTripInquiry inquiry = new GiftTripInquiry();
        inquiry.setAirport(req.airport().trim().toUpperCase());
        inquiry.setTravelers(req.travelers());
        inquiry.setDesiredDepartureDate(req.desiredDepartureDate());
        inquiry.setNights(req.nights());
        inquiry.setBuyerEmail(req.buyerEmail().trim().toLowerCase());
        inquiry.setNotes(req.notes() != null ? req.notes().trim() : null);
        inquiry.setRecipientName(req.recipientName().trim());
        inquiry.setRecipientEmail(req.recipientEmail().trim().toLowerCase());
        inquiry.setGiftMessage(req.giftMessage() != null ? req.giftMessage().trim() : null);

        GiftTripInquiry saved = inquiryRepository.save(inquiry);
        log.info("[GiftTrip] Nov upit: id={}, airport={}, travelers={}, datum={}, buyerEmail={}, recipient={}",
                saved.getId(), saved.getAirport(), saved.getTravelers(),
                saved.getDesiredDepartureDate(), saved.getBuyerEmail(), saved.getRecipientName());

        emailService.sendTeamAlert(saved);

        return new GiftTripInquiryResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GiftTripInquiryResponse> getAllInquiries() {
        return inquiryRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(GiftTripInquiryResponse::new)
                .toList();
    }

    @Override
    @Transactional
    public GiftTripInquiryResponse updateStatus(Long id, InquiryStatus status) {
        GiftTripInquiry inquiry = findOrThrow(id);
        inquiry.setStatus(status);
        inquiry.setClosedAt(status == InquiryStatus.CLOSED ? LocalDateTime.now() : null);
        log.info("[GiftTrip] Status upita id={} promenjen na {}", id, status);
        return new GiftTripInquiryResponse(inquiryRepository.save(inquiry));
    }

    @Override
    @Transactional
    public GiftTripInquiryResponse updatePrice(Long id, BigDecimal price) {
        GiftTripInquiry inquiry = findOrThrow(id);
        inquiry.setPrice(price);
        log.info("[GiftTrip] Cena upita id={} postavljena na {}", id, price);
        return new GiftTripInquiryResponse(inquiryRepository.save(inquiry));
    }

    @Override
    @Transactional
    public AdminDateResponse createPrivateDateFromGiftTrip(Long tripId, CreatePrivateDateRequest req) {
        GiftTripInquiry inquiry = findOrThrow(tripId);

        java.time.LocalDate depDate = inquiry.getDesiredDepartureDate();
        java.time.LocalDate retDate = depDate.plusDays(inquiry.getNights());

        AvailableDate date = new AvailableDate();
        date.setDepartureDate(depDate);
        date.setReturnDate(retDate);
        date.setNumberOfNights(inquiry.getNights());
        date.setDepartureAirport(inquiry.getAirport());
        date.setAvailableSlots(req.travelers());
        date.setBasePrice(req.pricePerPerson());
        date.setActive(true);
        date.setIsPrivate(true);
        date.setPrivateToken(TokenUtils.generate());
        date.setExpiresAt(java.time.LocalDateTime.now().plusHours(req.effectiveExpiry()));

        AvailableDate saved = availableDateRepository.save(date);

        // Automatski označi gift trip upit kao PRIVATE_SENT i sačuvaj token za admin panel
        inquiry.setStatus(InquiryStatus.PRIVATE_SENT);
        inquiry.setPrivateToken(saved.getPrivateToken());
        inquiryRepository.save(inquiry);

        log.info("[GiftTrip] Privatni termin kreiran za trip id={} | {} → {} | token={} | {}€/os",
                tripId, depDate, retDate, saved.getPrivateToken(), req.pricePerPerson());

        return new AdminDateResponse(saved);
    }

    @Override
    @Transactional
    public void deleteInquiry(Long id) {
        GiftTripInquiry inquiry = findOrThrow(id);
        inquiryRepository.delete(inquiry);
        log.info("[GiftTrip] Upit id={} obrisan (PRIVATE_SENT flow).", id);
    }

    private GiftTripInquiry findOrThrow(Long id) {
        return inquiryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Gift trip upit sa ID=" + id + " nije pronađen."));
    }
}
