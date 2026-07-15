package com.escapii.repository;

import com.escapii.model.InvoiceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceSequence> findByYear(Integer year);
}
