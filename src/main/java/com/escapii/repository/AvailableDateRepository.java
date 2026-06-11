package com.escapii.repository;

import com.escapii.model.AvailableDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AvailableDateRepository extends JpaRepository<AvailableDate, Long> {

    /** Javni termini - isključuje privatne (isPrivate=true) */
    List<AvailableDate> findByDepartureAirportAndActiveTrueAndIsPrivateFalseOrderByDepartureDateAsc(String departureAirport);

    List<AvailableDate> findAllByOrderByDepartureDateAsc();

    boolean existsByDepartureAirport(String departureAirport);

    /**
     * Pronalazi AvailableDate koji je vezan za određenu rezervaciju.
     * Koristi se npr. za ažuriranje availableSlots pri promeni statusa bookinga.
     */
    @Query("SELECT b.selectedDate FROM Booking b WHERE b.id = :bookingId")
    Optional<AvailableDate> findByBookingId(@Param("bookingId") Long bookingId);

    /**
     * Pronalazi privatni termin po tokenu.
     * Vraća Optional.empty() ako token ne postoji ili termin nije privatan.
     */
    Optional<AvailableDate> findByPrivateTokenAndIsPrivateTrue(String privateToken);

    /**
     * Briše prošle termine koji NEMAJU nijednu rezervaciju.
     * Sigurno - ne narušava FK constraints.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM AvailableDate d WHERE d.departureDate < :today " +
           "AND NOT EXISTS (SELECT b FROM Booking b WHERE b.selectedDate.id = d.id)")
    int deleteExpiredWithNoBookings(@Param("today") LocalDate today);

    /**
     * Deaktivira prošle termine koji IMAJU rezervacije - ne brišemo ih, čuvamo istoriju.
     */
    @Transactional
    @Modifying
    @Query("UPDATE AvailableDate d SET d.active = false WHERE d.departureDate < :today " +
           "AND d.active = true " +
           "AND EXISTS (SELECT b FROM Booking b WHERE b.selectedDate.id = d.id)")
    int deactivateExpiredWithBookings(@Param("today") LocalDate today);
}
