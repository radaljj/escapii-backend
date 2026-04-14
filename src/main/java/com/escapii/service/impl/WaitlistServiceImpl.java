package com.escapii.service.impl;

import com.escapii.model.WaitlistEntry;
import com.escapii.repository.WaitlistRepository;
import com.escapii.service.EmailService;
import com.escapii.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final EmailService emailService;

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
            log.info("[Waitlist] Novi subscriber: {} za {}", email, airport);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Race condition — već postoji
            return false;
        }
    }

    @Override
    public Map<String, Object> getWaitlistSummary() {
        List<WaitlistEntry> all = waitlistRepository.findAll();
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
        for (WaitlistEntry e : entries) {
            try {
                emailService.sendWaitlistNotification(e.getEmail(), airport);
                sent++;
            } catch (Exception ex) {
                log.error("[Waitlist] Greška pri slanju na {}: {}", e.getEmail(), ex.getMessage());
            }
        }
        waitlistRepository.deleteByAirport(airport);
        log.info("[Waitlist] Poslato {} notifikacija za {}, lista obrisana.", sent, airport);
        return sent;
    }
}
