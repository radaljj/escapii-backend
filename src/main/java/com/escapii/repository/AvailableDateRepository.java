package com.escapii.repository;

import com.escapii.model.AvailableDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvailableDateRepository extends JpaRepository<AvailableDate, Long> {

    List<AvailableDate> findByDepartureAirportAndActiveTrueOrderByDepartureDateAsc(String departureAirport);

    List<AvailableDate> findAllByOrderByDepartureDateAsc();

    boolean existsByDepartureAirport(String departureAirport);

    /**
     * Pronalazi AvailableDate koji je vezan za određenu rezervaciju.
     * Koristi se npr. za ažuriranje availableSlots pri promeni statusa bookinga.
     */
    @Query("SELECT b.selectedDate FROM Booking b WHERE b.id = :bookingId")
    Optional<AvailableDate> findByBookingId(@Param("bookingId") Long bookingId);
}
