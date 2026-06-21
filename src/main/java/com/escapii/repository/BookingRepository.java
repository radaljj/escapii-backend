package com.escapii.repository;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status);

    /**
     * Učitava sve bookinge zajedno sa svim isključenim destinacijama (JOIN FETCH)
     * i putnicima (@BatchSize) - ukupno 2 SQL upita bez obzira na N rezervacija.
     *
     * @EntityGraph JOIN-uje 3 excluded destination kolone u jednom SELECT-u.
     * Passengers se učitavaju batch-om (50 po upitu) zahvaljujući @BatchSize na entitetu.
     */
    @EntityGraph(attributePaths = {
        "excludedDestination1", "excludedDestination2", "excludedDestination3"
    })
    List<Booking> findAllByOrderByCreatedAtDesc();

    /**
     * Učitava jedan booking sa svim asocijacijama potrebnim za slanje emaila -
     * isključene destinacije (JOIN FETCH) + putnici (@BatchSize).
     * Koristiti uvek pre prosleđivanja Bookinga u email servis.
     */
    @EntityGraph(attributePaths = {
        "excludedDestination1", "excludedDestination2", "excludedDestination3",
        "passengers"
    })
    Optional<Booking> findWithDetailsById(Long id);

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

    /** Pregled statusa - case-insensitive i za ref i za prezime. */
    @Query("SELECT b FROM Booking b WHERE LOWER(b.bookingRef) = LOWER(TRIM(:ref)) AND LOWER(TRIM(b.lastName)) = LOWER(TRIM(:lastName))")
    java.util.Optional<Booking> findByRefAndLastName(
            @Param("ref")      String ref,
            @Param("lastName") String lastName
    );

    /** Pronađi booking po reveal tokenu (za /api/reveal endpoint). */
    java.util.Optional<Booking> findByRevealToken(String revealToken);

    /**
     * Učitava booking sa pesimističkim lock-om - serijalizuje istovremene admin akcije
     * (npr. dvostruki klik na "Pošalji Reveal"/"Pošalji Prognozu") da se ne pošalje duplo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /** Ukupan broj rezervacija za dati termin (sve statuse) - koristi se pre brisanja. */
    long countBySelectedDateId(Long selectedDateId);

    /**
     * Duplikat check - isti email + isti termin kreiran u poslednjih 24h.
     * Koristi se za anti-spam zaštitu pri kreiranju bookinga.
     */
    @Query("SELECT COUNT(b) > 0 FROM Booking b " +
           "WHERE LOWER(b.email) = LOWER(:email) " +
           "AND b.selectedDate.id = :dateId " +
           "AND b.createdAt > :since " +
           "AND b.status != 'CANCELLED'")
    boolean existsDuplicateBooking(
            @Param("email")  String email,
            @Param("dateId") Long dateId,
            @Param("since")  LocalDateTime since);

    /**
     * CONFIRMED bookingovi kojima:
     *   - assignedDestination je unesena
     *   - revealSentAt je null (još nije poslato)
     *   - departureDate <= cutoff (T-2 od danas ili ranije ako propušteno)
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
     *       from  = today+4  → nikad ne šaljemo ako je reveal već otišao (T-2)
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

    /** Bookings kojima je revealSentAt između zadatih trenutaka (za digest - šta je danas poslato). */
    @Query("SELECT b FROM Booking b WHERE b.revealSentAt >= :from AND b.revealSentAt < :until")
    List<Booking> findRevealSentBetween(@Param("from") LocalDateTime from,
                                        @Param("until") LocalDateTime until);

    /** Bookings kojima je forecastSentAt između zadatih trenutaka (za digest - šta je danas poslato). */
    @Query("SELECT b FROM Booking b WHERE b.forecastSentAt >= :from AND b.forecastSentAt < :until")
    List<Booking> findForecastSentBetween(@Param("from") LocalDateTime from,
                                          @Param("until") LocalDateTime until);

    /**
     * CONFIRMED bookingovi sa Reveal Box-om koji još nisu poslati,
     * a polazak je za <= 5 dana - digest treba da podseti tim.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.hasRevealBox = true " +
           "AND b.revealBoxSent = false " +
           "AND b.selectedDate.departureDate >= :today " +
           "AND b.selectedDate.departureDate <= :until " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findPendingRevealBoxes(@Param("today") LocalDate today,
                                         @Param("until") LocalDate until);

    /**
     * CONFIRMED bookingovi kojima je reveal email poslan i korisnik je otvorio reveal stranicu
     * (RevealEvent postoji) — tim treba da pošalje potvrdu leta i smeštaja.
     * Isključeni: Reveal Box rezervacije (oni dobijaju fizičku kutiju, ne email reveal).
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.revealSentAt IS NOT NULL " +
           "AND b.hasRevealBox = false " +
           "AND b.selectedDate.returnDate >= :today " +
           "AND b.selectedDate.departureDate <= :cutoff " +
           "AND b.bookingRef IN (SELECT r.bookingRef FROM RevealEvent r) " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findRevealedAndViewed(@Param("today") LocalDate today,
                                        @Param("cutoff") LocalDate cutoff);

    /**
     * CONFIRMED bookingovi kojima je reveal email poslan ALI korisnik NIJE otvorio reveal stranicu,
     * a polazak je za <= 2 dana — hitno upozorenje u digestu.
     * Isključeni: Reveal Box rezervacije.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.revealSentAt IS NOT NULL " +
           "AND b.hasRevealBox = false " +
           "AND b.selectedDate.departureDate >= :today " +
           "AND b.selectedDate.departureDate <= :cutoff " +
           "AND b.bookingRef NOT IN (SELECT r.bookingRef FROM RevealEvent r) " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findRevealedButNotViewed(@Param("today") LocalDate today,
                                            @Param("cutoff") LocalDate cutoff);

    /**
     * CONFIRMED bookingovi čiji je returnDate <= today i ispunjeni svi uslovi:
     * - reveal poslan (revealSentAt IS NOT NULL)
     * - airline booking code unet (nije null niti prazan string)
     * Napomena: forecastSentAt nije uslov - forecast može biti propušten ako je
     * booking potvrđen unutar T-4 dana pre polaska (scheduler ga ne stigne poslati).
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.selectedDate.returnDate <= :today " +
           "AND b.revealSentAt IS NOT NULL " +
           "AND b.airlineBookingCode IS NOT NULL " +
           "AND b.airlineBookingCode != ''")
    List<Booking> findReadyForCompletion(@Param("today") LocalDate today);

}
