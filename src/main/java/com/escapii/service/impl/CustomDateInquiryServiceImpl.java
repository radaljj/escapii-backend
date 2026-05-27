package com.escapii.service.impl;

import com.escapii.dto.CustomDateInquiryRequest;
import com.escapii.dto.CustomDateInquiryResponse;
import com.escapii.model.CustomDateInquiry;
import com.escapii.model.InquiryStatus;
import com.escapii.service.email.core.EmailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.escapii.repository.CustomDateInquiryRepository;
import com.escapii.service.CustomDateInquiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final EmailSender emailSender;

    @Value("${app.team-email}")
    private String teamEmail;

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

        // Email notifikacija timu
        String notes = (saved.getNotes() != null && !saved.getNotes().isBlank())
                ? "<tr><td style='padding:6px 0;color:#888;'>Napomena</td><td style='padding:6px 0;'>" + saved.getNotes() + "</td></tr>"
                : "";
        String html = """
                <div style="font-family:sans-serif;max-width:520px;margin:0 auto;background:#0f2d35;color:#e8e0d5;border-radius:12px;padding:28px 32px;">
                  <h2 style="margin:0 0 20px;color:#CA8A71;font-size:20px;">📅 Nov prilagođeni upit</h2>
                  <table style="width:100%%;border-collapse:collapse;font-size:15px;">
                    <tr><td style="padding:6px 0;color:#888;width:130px;">ID</td><td style="padding:6px 0;">#%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Email</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Aerodrom</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Putnici</td><td style="padding:6px 0;">%d</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Željeni datum</td><td style="padding:6px 0;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#888;">Noći</td><td style="padding:6px 0;">%d</td></tr>
                    %s
                  </table>
                </div>
                """.formatted(
                        saved.getId(), saved.getEmail(), saved.getAirport(),
                        saved.getTravelers(), saved.getDesiredDepartureDate(),
                        saved.getNights(), notes);
        boolean ok = emailSender.send(teamEmail, "📅 Nov prilagođeni upit #" + saved.getId(), html);
        if (!ok) log.warn("[Inquiry] Tim notifikacija nije poslata za upit id={}", saved.getId());

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
