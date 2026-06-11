package com.escapii.repository;

import com.escapii.model.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {

    /** Sve aktivne destinacije (bez filtera po aerodromu). */
    List<Destination> findByActiveTrueOrderByNameAsc();

    /** Aktivne destinacije dostupne iz konkretnog aerodroma polaska (npr. "BEG"). */
    @Query("SELECT d FROM Destination d JOIN d.departureAirports a " +
           "WHERE d.active = true AND a = :airport ORDER BY d.name ASC")
    List<Destination> findByDepartureAirportAndActiveTrueOrderByNameAsc(@Param("airport") String airport);

    /** Sve destinacije sortiane po imenu (admin - uključuje i neaktivne). */
    List<Destination> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
