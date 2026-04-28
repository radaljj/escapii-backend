package com.escapii.repository;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    /**
     * Učitava sve bookinge zajedno sa svim isključenim destinacijama (JOIN FETCH)
     * i putnicima (@BatchSize) — ukupno 2 SQL upita bez obzira na N rezervacija.
     *
     * @EntityGraph JOIN-uje 5 excluded destination kolona u jednom SELECT-u.
     * Passengers se učitavaju batch-om (50 po upitu) zahvaljujući @BatchSize na entitetu.
     */
    @EntityGraph(attributePaths = {
        "excludedDestination1", "excludedDestination2", "excludedDestination3",
        "excludedDestination4", "excludedDestination5"
    })
    List<Booking> findAllByOrderByCreatedAtDesc();

    /** Sve CONFIRMED rezervacije čiji je polazak između danas i datuma 'until' (za jutarnji digest). */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.selectedDate.departureDate >= :from " +
           "AND b.selectedDate.departureDate <= :until " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findConfirmedDepartingBetween(
            @Param("from")  LocalDate from,
            @Param("until") LocalDate until);

    /** PENDING rezervacije starije od zadatog trenutka (za auto-cancel). */
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.createdAt < :before")
    List<Booking> findStalePendingBefore(@Param("before") LocalDateTime before);

    /** Pregled statusa — case-insensitive i za ref i za prezime. */
    @Query("SELECT b FROM Booking b WHERE LOWER(b.bookingRef) = LOWER(TRIM(:ref)) AND LOWER(TRIM(b.lastName)) = LOWER(TRIM(:lastName))")
    java.util.Optional<Booking> findByRefAndLastName(
            @Param("ref")      String ref,
            @Param("lastName") String lastName
    );

    /** Pronađi booking po reveal tokenu (za /api/reveal endpoint). */
    java.util.Optional<Booking> findByRevealToken(String revealToken);

    /**
     * CONFIRMED bookingovi kojima:
     *   - assignedDestination je unesena
     *   - revealSentAt je null (još nije poslato)
     *   - departureDate <= cutoff (T-3 od danas ili ranije ako propušteno)
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.assignedDestination IS NOT NULL " +
           "AND b.revealSentAt IS NULL " +
           "AND b.selectedDate.departureDate <= :cutoff")
    List<Booking> findReadyForReveal(@Param("cutoff") LocalDate cutoff);

    /**
     * CONFIRMED bookingovi kojima:
     *   - assignedDestination je unesena (potrebna za geocoding)
     *   - forecastSentAt je null (još nije poslato)
     *   - departureDate je između [from, until]:
     *       from  = today+4  → nikad ne šaljemo ako je reveal već otišao (T-3)
     *       until = today+7  → primarni okidač na T-7; catch-up za propuštene dane T-4..T-6
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.assignedDestination IS NOT NULL " +
           "AND b.forecastSentAt IS NULL " +
           "AND b.selectedDate.departureDate >= :from " +
           "AND b.selectedDate.departureDate <= :until " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findReadyForForecast(@Param("from") LocalDate from,
                                       @Param("until") LocalDate until);

    /** Bookings kojima je revealSentAt između zadatih trenutaka (za digest — šta je danas poslato). */
    @Query("SELECT b FROM Booking b WHERE b.revealSentAt >= :from AND b.revealSentAt < :until")
    List<Booking> findRevealSentBetween(@Param("from") LocalDateTime from,
                                        @Param("until") LocalDateTime until);

    /** Bookings kojima je forecastSentAt između zadatih trenutaka (za digest — šta je danas poslato). */
    @Query("SELECT b FROM Booking b WHERE b.forecastSentAt >= :from AND b.forecastSentAt < :until")
    List<Booking> findForecastSentBetween(@Param("from") LocalDateTime from,
                                          @Param("until") LocalDateTime until);

}
