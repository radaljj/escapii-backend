package com.escapii.service.impl;

import com.escapii.model.WaitlistEntry;
import com.escapii.repository.WaitlistRepository;
import com.escapii.service.email.WaitlistEmailService;
import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.escapii.util.LogUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final WaitlistEmailService waitlistEmailService;

    @Override
    public boolean subscribe(String email, String airport) {
        if (waitlistRepository.existsByEmailAndAirport(email, airport)) {
            return false;
        }
        try {
            WaitlistEntry entry = new WaitlistEntry();
            entry.setEmail(email);
            entry.setAirport(airport);
            waitlistRepository.save(entry);
            log.info("[Waitlist] Novi subscriber: {} za {}", LogUtils.maskEmail(email), airport);
            waitlistEmailService.sendWaitlistConfirmation(email, airport);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Race condition — već postoji
            return false;
        }
    }

    // Gornja granica za findAll — sprečava OOM ako lista poraste
    private static final int WAITLIST_FETCH_LIMIT = 1000;

    @Override
    public Map<String, Object> getWaitlistSummary() {
        List<WaitlistEntry> all = waitlistRepository.findAll(PageRequest.of(0, WAITLIST_FETCH_LIMIT)).getContent();
        Map<String, Long> byAirport = all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        WaitlistEntry::getAirport,
                        java.util.stream.Collectors.counting()
                ));
        return Map.of(
                "total",     (long) all.size(),
                "byAirport", byAirport,
                "entries",   all
        );
    }

    @Override
    @Transactional
    public int notifyAndClear(String airport) {
        List<WaitlistEntry> entries = waitlistRepository.findByAirportOrderByCreatedAtAsc(airport);
        int sent = 0;
        // Only delete entries where the email was successfully sent
        for (WaitlistEntry entry : entries) {
            try {
                waitlistEmailService.sendWaitlistNotification(entry.getEmail(), entry.getAirport());
                waitlistRepository.delete(entry);
                sent++;
            } catch (Exception e) {
                log.warn("[Waitlist] Email nije poslat na {}: {}", LogUtils.maskEmail(entry.getEmail()), e.getMessage());
            }
        }
        log.info("[Waitlist] Poslato {} notifikacija za {}.", sent, airport);
        return sent;
    }
}
