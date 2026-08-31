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
        "excludedDestination1", "excludedDestination2", "excludedDestination3", "excludedDestination4"
    })
    List<Booking> findAllByOrderByCreatedAtDesc();

    /**
     * Učitava jedan booking sa svim asocijacijama potrebnim za slanje emaila -
     * isključene destinacije (JOIN FETCH) + putnici (@BatchSize).
     * Koristiti uvek pre prosleđivanja Bookinga u email servis.
     */
    @EntityGraph(attributePaths = {
        "excludedDestination1", "excludedDestination2", "excludedDestination3", "excludedDestination4",
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
     *       from  = today    → pokušavamo svakog dana dok polazak ne prođe
     *       until = today+7  → primarni okidač na T-7, ostalo je nadoknada
     *
     * Prognoza sme da stigne i na dan polaska, ali nikad posle reveala -
     * taj redosled drži DailyTaskScheduler, koji je šalje prvu.
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
     * CONFIRMED bookingovi spremni za slanje dokumenta rezervacije (jos nije poslat).
     * Digest sekcija za tim da uploaduje zvanicni PDF od agencije - slanje je automatsko posle toga.
     *
     * Pravilo kad je "spreman":
     *   - non-box: postoji RevealEvent (kupac je kliknuo reveal link)
     *   - box:     revealSentAt IS NOT NULL (kutija je vec otkrila destinaciju,
     *              ne trazimo klik jer nije obavezan)
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.revealSentAt IS NOT NULL " +
           "AND b.confirmationSentAt IS NULL " +
           "AND b.selectedDate.returnDate >= :today " +
           "AND b.selectedDate.departureDate <= :cutoff " +
           "AND ((b.hasRevealBox = true) " +
           "     OR (b.hasRevealBox = false AND b.bookingRef IN (SELECT r.bookingRef FROM RevealEvent r))) " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findRevealedAndViewed(@Param("today") LocalDate today,
                                        @Param("cutoff") LocalDate cutoff);

    /**
     * CONFIRMED bookingovi kojima je reveal email poslan ALI korisnik NIJE otvorio reveal stranicu,
     * a polazak je za <= 2 dana — hitno upozorenje u digestu.
     *
     * Reveal Box rezervacije su ISKLJUCENE: box korisnik nije duzan da klikne
     * link (destinaciju je vec saznao iz kutije), pa nema smisla alarmirati tim
     * da nije otvorio - dokument ce mu automatski otici cim reveal mejl bude poslat.
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
     * - reveal poslan (revealSentAt IS NOT NULL) - vazi i za Reveal Box rezervacije
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

    /**
     * CONFIRMED bookingovi kojima dokument ceka na slanje: uploadovan je,
     * confirmationSentAt jos nije upisan, i uslov reveal-a je ispunjen.
     *
     * Pravilo (isto kao u ConfirmationDocumentAutoSender.canReceive):
     *   - box:     revealSentAt IS NOT NULL (kutija je vec otkrila destinaciju)
     *   - non-box: postoji RevealEvent (kupac je kliknuo reveal link)
     *
     * Prozor: samo aktivni bookinzi (returnDate >= today) - istekle ne saljemo.
     * Sluzi kao safety net za retry - ako auto-send u sendReveals cycle-u pukne
     * (SMTP hiccup, restart scheduler-a), naredni dnevni prolaz ce ih pokupiti.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.confirmationDocument IS NOT NULL " +
           "AND b.confirmationSentAt IS NULL " +
           "AND b.selectedDate.returnDate >= :today " +
           "AND ((b.hasRevealBox = true AND b.revealSentAt IS NOT NULL) " +
           "     OR (b.hasRevealBox = false AND b.bookingRef IN (SELECT r.bookingRef FROM RevealEvent r))) " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findPendingConfirmationDocuments(@Param("today") LocalDate today);

    /**
     * Dashboard upit za tab "Obracun i fakturisanje agencija". Filtrira po opcionoj
     * agenciji, opcionom rasponu datuma polaska i opcionom settlement statusu.
     * CANCELLED bookinzi su iskljuceni - ne ulaze u naplatu.
     *
     * <p>Sortirano po datumu polaska descending da najnoviji dolaze prvi.
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.status <> com.escapii.model.BookingStatus.CANCELLED " +
           "AND (:agencyId IS NULL OR b.agencyIdSnapshot = :agencyId) " +
           "AND (:fromDate IS NULL OR b.selectedDate.departureDate >= :fromDate) " +
           "AND (:toDate IS NULL OR b.selectedDate.departureDate <= :toDate) " +
           "AND (:status IS NULL OR b.settlementStatus = :status) " +
           "ORDER BY b.selectedDate.departureDate DESC, b.id DESC")
    List<Booking> findForAgencyDashboard(@Param("agencyId") Long agencyId,
                                         @Param("fromDate") LocalDate fromDate,
                                         @Param("toDate") LocalDate toDate,
                                         @Param("status") com.escapii.model.SettlementStatus status);

}
