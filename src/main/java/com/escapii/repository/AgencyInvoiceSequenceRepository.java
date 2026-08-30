package com.escapii.repository;

import com.escapii.model.AgencyInvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AgencyInvoiceSequenceRepository extends JpaRepository<AgencyInvoiceSequence, Integer> {

    /**
     * Cita sekvencu sa PESSIMISTIC_WRITE zakljucavanjem - dva paralelna finalize
     * poziva ne smeju da dobiju isti broj fakture.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AgencyInvoiceSequence> findByYear(Integer year);
}
