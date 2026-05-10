package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.service.AvailableDateService;
import com.escapii.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableDateServiceImpl implements AvailableDateService {

    private final AvailableDateRepository availableDateRepository;

    @Override
    public List<AvailableDate> getActiveDatesByAirport(String airport) {
        return availableDateRepository.findByDepartureAirportAndActiveTrueAndIsPrivateFalseOrderByDepartureDateAsc(
                airport.trim().toUpperCase()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AvailableDate getPrivateDateByToken(String token) {
        AvailableDate date = availableDateRepository
                .findByPrivateTokenAndIsPrivateTrue(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Privatni link nije pronađen."));

        if (date.getExpiresAt() != null && LocalDateTime.now().isAfter(date.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Privatni link je istekao. Kontaktirajte Escapii tim.");
        }

        return date;
    }

    @Override
    @Transactional
    public AvailableDate makePrivate(Long dateId, int travelers, int expiresInHours, Integer pricePerPerson) {
        AvailableDate date = availableDateRepository.findById(dateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Termin sa ID=" + dateId + " nije pronađen."));

        date.setIsPrivate(true);
        date.setPrivateToken(TokenUtils.generate());
        date.setAvailableSlots(travelers);
        date.setExpiresAt(LocalDateTime.now().plusHours(expiresInHours));

        if (pricePerPerson != null && pricePerPerson > 0) {
            date.setBasePrice(pricePerPerson);
        }

        AvailableDate saved = availableDateRepository.save(date);
        log.info("[Private] Termin id={} privatizovan. Token={}, travelers={}, basePrice={}€/os, expiresAt={}",
                saved.getId(), saved.getPrivateToken(), travelers, saved.getBasePrice(), saved.getExpiresAt());
        return saved;
    }
}
