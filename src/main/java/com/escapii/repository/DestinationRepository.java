package com.escapii.repository;

import com.escapii.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {

    /** Sve destinacije po aerodromu polaska (bez active filtera — per-termin logika). */
    @Query("SELECT d FROM Destination d JOIN d.departureAirports a WHERE a = :airport ORDER BY d.name ASC")
    List<Destination> findByDepartureAirportOrderByNameAsc(@Param("airport") String airport);

    /** Sve destinacije sortirane po imenu. */
    List<Destination> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
