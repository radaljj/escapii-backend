package com.escapii.repository;

import com.escapii.model.TermDestination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TermDestinationRepository extends JpaRepository<TermDestination, Long> {

    List<TermDestination> findByDateIdOrderByDestinationNameAsc(Long dateId);

    Optional<TermDestination> findByDateIdAndDestinationId(Long dateId, Long destinationId);

    boolean existsByDateIdAndDestinationId(Long dateId, Long destinationId);

    /** Sve per-termin aktivne destinacije za javni booking. */
    @Query("SELECT td FROM TermDestination td " +
           "WHERE td.date.id = :dateId AND td.active = true " +
           "ORDER BY td.destination.name ASC")
    List<TermDestination> findActiveByDateId(@Param("dateId") Long dateId);

    /** Brisanje svih TermDestination zapisa za datu destinaciju (FK cleanup pri brisanju dest.). */
    void deleteByDestinationId(Long destinationId);
}
