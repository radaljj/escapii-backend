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

    /**
     * Javni termini - isključuje privatne (isPrivate=true).
     * departureDate mora biti STROGO posle danas (After = >) - usklađeno sa
     * BookingServiceImpl koji odbija rezervaciju za termin koji je "danas"
     * (isAfter(today) proverava isto). Bez ovoga bi termin koji je danas
     * ostao vidljiv i "dostupan" na sajtu ceo dan, ali svaka rezervacija
     * bi pukla sa 400 - cleanup cron ga deaktivira tek sledećeg jutra.
     */
    List<AvailableDate> findByDepartureAirportAndActiveTrueAndIsPrivateFalseAndDepartureDateAfterOrderByDepartureDateAsc(
            String departureAirport, java.time.LocalDate today);

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
     * Briše prošle termine (departureDate < cutoff) koji NEMAJU nijednu rezervaciju.
     * Sigurno - ne narušava FK constraints. Pozivalac šalje "sutra" kao cutoff da
     * termin sa departureDate=danas takođe bude tretiran kao istekao.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM AvailableDate d WHERE d.departureDate < :cutoff " +
           "AND NOT EXISTS (SELECT b FROM Booking b WHERE b.selectedDate.id = d.id)")
    int deleteExpiredWithNoBookings(@Param("cutoff") LocalDate cutoff);

    /**
     * Deaktivira prošle termine (departureDate < cutoff) koji IMAJU rezervacije -
     * ne brišemo ih, čuvamo istoriju. Pozivalac šalje "sutra" kao cutoff da termin
     * sa departureDate=danas takođe bude deaktiviran.
     */
    @Transactional
    @Modifying
    @Query("UPDATE AvailableDate d SET d.active = false WHERE d.departureDate < :cutoff " +
           "AND d.active = true " +
           "AND EXISTS (SELECT b FROM Booking b WHERE b.selectedDate.id = d.id)")
    int deactivateExpiredWithBookings(@Param("cutoff") LocalDate cutoff);
}
