package com.escapii.service.impl;

import com.escapii.model.AvailableDate;
import com.escapii.repository.AvailableDateRepository;
import com.escapii.service.AvailableDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailableDateServiceImpl implements AvailableDateService {

    private final AvailableDateRepository availableDateRepository;

    @Override
    public List<AvailableDate> getActiveDatesByAirport(String airport) {
        return availableDateRepository.findByDepartureAirportAndActiveTrueOrderByDepartureDateAsc(
                airport.trim().toUpperCase()
        );
    }
}
