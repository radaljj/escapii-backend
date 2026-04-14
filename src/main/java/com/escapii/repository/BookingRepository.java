package com.escapii.repository;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
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
}
