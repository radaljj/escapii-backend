package com.escapii.repository;

import com.escapii.model.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByEmailAndAirport(String email, String airport);

    List<WaitlistEntry> findByAirportOrderByCreatedAtAsc(String airport);

    long countByAirport(String airport);

    void deleteByAirport(String airport);
}
