package com.escapii.repository;

import com.escapii.model.RevealEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RevealEventRepository extends JpaRepository<RevealEvent, Long> {

    Optional<RevealEvent> findByBookingRef(String bookingRef);

    /** Batch fetch za admin panel — svi eventi za datu listu referenci. */
    List<RevealEvent> findAllByBookingRefIn(List<String> bookingRefs);
}
