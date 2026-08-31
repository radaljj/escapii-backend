package com.escapii.repository;

import com.escapii.model.AgencyInvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgencyInvoiceSequenceRepository extends JpaRepository<AgencyInvoiceSequence, Integer> {

    /**
     * Cita sekvencu sa PESSIMISTIC_WRITE zakljucavanjem - dva paralelna finalize
     * poziva ne smeju da dobiju isti broj fakture.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AgencyInvoiceSequence> findByYear(Integer year);

    /**
     * Idempotentna inicijalizacija reda za datu godinu. Bez ovoga bi prva
     * faktura u novoj godini imala race: dva bookinga istovremeno rade
     * {@code orElseGet(new AgencyInvoiceSequence(year))} pa oba pokusaju INSERT
     * na isti PK - jedan pukne. Sa {@code ON CONFLICT DO NOTHING} INSERT je
     * bezbedan i moze da se zove pre lock-a bez straha od kolizije.
     */
    @Modifying
    @Query(value = "INSERT INTO agency_invoice_sequences (year, last_seq) "
                 + "VALUES (:year, 0) ON CONFLICT (year) DO NOTHING",
           nativeQuery = true)
    void ensureYearRow(@Param("year") int year);
}
