package com.escapii.service;

import com.escapii.dto.PricePreviewResponse;
import com.escapii.model.AccommodationType;
import com.escapii.model.AvailableDate;

public interface PriceCalculator {

    /**
     * Cenovnik:
     *
     *   Po osobi (× n putnika):
     *     baza + doručak? +15 + sedišta? +10 + osiguranje? +20
     *
     *   Flat (jedna cena za celu rezervaciju):
     *     Superior? +100
     *     Isključivanja: 1. besplatno | 2. +10 | 3. +10 (max 20)
     *
     *   Kabinski kofer (selektivan po putniku):
     *     cabinSuitcaseCount × 100
     */
    PricePreviewResponse calculate(
            AvailableDate date,
            int n,
            AccommodationType accommodationType,
            int exclusionCount,
            int cabinSuitcaseCount,
            boolean hasInsurance,
            boolean hasBreakfast,
            boolean hasSeatsTogether,
            boolean hasRevealBox
    );
}
