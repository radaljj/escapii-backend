package com.escapii.repository;

import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * Šifra rezervacije bez obzira na velika/mala slova. Kod vaučera poklonjenog
     * putovanja je šifra rezervacije, a /poklon stranica kod uvek diže u velika slova.
     */
    java.util.Optional<Booking> findByBookingRefIgnoreCase(String bookingRef);

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
     * Duplikat check pri kreiranju rezervacije: isti mejl + isti termin, a postojeći upit još
     * ČEKA obradu (PENDING). Potvrđena ili završena rezervacija ne blokira - novi zahtev sa istog
     * mejla je nov upit (npr. za još putnika) koji admin vidi i odlučuje (Marko 2026-09-17).
     * Bez vremenskog prozora: dok upit čeka, isti upit je duplikat bez obzira kad je poslat.
     */
    @Query("SELECT COUNT(b) > 0 FROM Booking b " +
           "WHERE LOWER(b.email) = LOWER(:email) " +
           "AND b.selectedDate.id = :dateId " +
           "AND b.status = com.escapii.model.BookingStatus.PENDING")
    boolean existsPendingDuplicate(
            @Param("email")  String email,
            @Param("dateId") Long dateId);

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
     * taj redosled drži DailyTaskScheduler, koji je šalje prvu. Ako je reveal već otišao
     * (ručno iz panela, bez prognoze), prognoza se više ne šalje: pisana je kao najava reveala.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.assignedDestination IS NOT NULL " +
           "AND b.forecastSentAt IS NULL " +
           "AND b.revealSentAt IS NULL " +
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


    // ── Ciljani upisi za dnevni scheduler ────────────────────────────────────
    //
    // sendReveals i sendForecasts NAMERNO nemaju @Transactional na nivou metode
    // (da pad jednog bookinga ne poništi flag onima kojima je mejl već otišao).
    // Posledica je da su svi Booking objekti u tim petljama DETACHED: lista se
    // učita u 10:00:00, a obrada traje minutima jer svaki booking zove geokodiranje,
    // prognozu i SMTP.
    //
    // Zbog toga se ovde NE SME zvati save(booking): za detached entitet to je
    // em.merge(), a merge prepisuje SVE kolone vrednostima iz snapshot-a od 10:00.
    // Ako admin u međuvremenu uploaduje PDF ili otkaže rezervaciju, ta izmena se
    // tiho gubi - Booking nema @Version pa ništa ne primeti.
    //
    // Zato svaki od ovih upita dira TAČNO JEDNU kolonu. Uslov "IS NULL" uz to čini
    // upis idempotentnim: drugi prolaz ne pomera već upisano vreme.
    //
    // flushAutomatically=true je za ADMIN putanju (upload/resend), gde je booking MANAGED i
    // prljav (upravo mu je upisan PDF) u ISTOJ transakciji. Bez toga bi redosled "prvo
    // entitet, pa ciljani upis" zavisio od Hibernate auto-flush pravila; ovako je
    // deterministican: prljavi entitet se izbaci PRE ciljanog upisa, snapshot mu se
    // osvezi, pa na commit-u nema drugog UPDATE-a koji bi flag vratio na NULL.
    // clearAutomatically NAMERNO nema: ocistio bi ceo persistence context, a pozivalac
    // posle toga jos cita lazy kolekcije sa istog bookinga za odgovor panelu.

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.forecastSentAt = :kad WHERE b.id = :id AND b.forecastSentAt IS NULL")
    int markForecastSent(@Param("id") Long id, @Param("kad") LocalDateTime kad);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.revealSentAt = :kad WHERE b.id = :id AND b.revealSentAt IS NULL")
    int markRevealSent(@Param("id") Long id, @Param("kad") LocalDateTime kad);

    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.confirmationSentAt = :kad WHERE b.id = :id AND b.confirmationSentAt IS NULL")
    int markConfirmationSent(@Param("id") Long id, @Param("kad") LocalDateTime kad);

    /**
     * Upisuje reveal token samo ako ga još nema. Vraća 0 ako je neko drugi
     * (npr. ručno slanje iz panela) upisao svoj u međuvremenu - pozivalac tada
     * mora pročitati token iz baze, jer mejl mora nositi onaj koji je zaista
     * sačuvan.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Booking b SET b.revealToken = :token WHERE b.id = :id AND b.revealToken IS NULL")
    int saveRevealTokenIfAbsent(@Param("id") Long id, @Param("token") String token);

    @Query("SELECT b.revealToken FROM Booking b WHERE b.id = :id")
    Optional<String> findRevealTokenById(@Param("id") Long id);

    /**
     * Da li je rezervacija JOS UVEK podobna za slanje reveala, u ovom trenutku.
     *
     * <p>Dnevne petlje ucitaju listu odjednom pa je obradjuju minutima (geokodiranje,
     * prognoza, SMTP). Odluka "posalji" se zato donosi na snimku starom koliko i cela
     * petlja: ako admin u medjuvremenu rucno posalje reveal ili skloni destinaciju,
     * petlja to ne vidi i posalje drugi put. Ciljani upisi to ne resavaju - oni stite
     * upis, ne odluku.
     *
     * <p>Zove se pod bravom na redu (findByIdForUpdate), pa se ručno slanje iz panela i krug
     * ne preklapaju - ko uđe drugi, zatekne „već poslato".
     */
    @Query("""
           SELECT COUNT(b) FROM Booking b
            WHERE b.id = :id
              AND b.revealSentAt IS NULL
              AND b.assignedDestination IS NOT NULL
              AND b.status = com.escapii.model.BookingStatus.CONFIRMED
           """)
    long jeLiJosZaReveal(@Param("id") Long id);

    /** Isto za prognozu - vidi {@link #jeLiJosZaReveal}. */
    @Query("""
           SELECT COUNT(b) FROM Booking b
            WHERE b.id = :id
              AND b.forecastSentAt IS NULL
              AND b.revealSentAt IS NULL
              AND b.assignedDestination IS NOT NULL
              AND b.status = com.escapii.model.BookingStatus.CONFIRMED
           """)
    long jeLiJosZaPrognozu(@Param("id") Long id);

    /**
     * Rezervacije koje još imaju upisan broj pasoša, a rok mu je istekao: povratak pre
     * {@code cutoff} (bilo koji status), ili otkazana rezervacija čiji je polazak prošao.
     * Koristi {@code com.escapii.passport.PassportRetentionService}.
     */
    @Query("SELECT DISTINCT b FROM Booking b JOIN b.selectedDate d JOIN b.passengers p " +
           "WHERE p.passportNumber IS NOT NULL " +
           "AND (d.returnDate < :cutoff OR (b.status = 'CANCELLED' AND d.departureDate < :today))")
    List<Booking> findWithPassportsToPurge(@Param("cutoff") LocalDate cutoff, @Param("today") LocalDate today);

    /**
     * Potvrđene rezervacije bez poslatog reveala čiji je polazak između {@code today} i
     * {@code cutoff}. Koristi {@code JobHealthService} za /api/health/jobs - bez obzira na
     * razlog (nema destinacije, nema prognoze, slanje palo), svaka takva je kupac koji
     * čeka reveal koji je već trebalo da stigne.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.revealSentAt IS NULL " +
           "AND b.selectedDate.departureDate >= :today " +
           "AND b.selectedDate.departureDate <= :cutoff")
    List<Booking> findRevealOverdue(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);

    /**
     * Potvrđene rezervacije bez poslate prognoze sa polaskom od {@code today} do {@code cutoff}.
     * Za {@code JobHealthService}: prognoza ide na T-7, pa je polazak za 5 dana ili manje bez nje
     * znak da nešto ne radi (geokoder, vremenski servis, mejl). Bez destinacije se ne računa - za
     * to postoji upozorenje „nema destinacije" (a na T-2 postaje reveal koji kasni). Ne računa se
     * ni kad je reveal već otišao (ručno): prognoza posle reveala ne ide.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND b.forecastSentAt IS NULL " +
           "AND b.revealSentAt IS NULL " +
           "AND b.assignedDestination IS NOT NULL AND TRIM(b.assignedDestination) <> '' " +
           "AND b.selectedDate.departureDate >= :today " +
           "AND b.selectedDate.departureDate <= :cutoff")
    List<Booking> findForecastOverdue(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);

    /** Koliko neotkazanih rezervacija je iskoristilo promo kod - za karticu „Promo" u panelu. */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.promoCode = :kod AND b.status <> 'CANCELLED'")
    long countPromoUses(@Param("kod") String kod);

    /** Koliko su te rezervacije ukupno uštedele (€). */
    @Query("SELECT COALESCE(SUM(b.promoSavedEur), 0) FROM Booking b WHERE b.promoCode = :kod AND b.status <> 'CANCELLED'")
    long sumPromoSaved(@Param("kod") String kod);

    /**
     * Potvrđene rezervacije BEZ unete destinacije sa polaskom u prozoru - jutarnji krug i digest
     * upozoravaju tim, jer bez destinacije ne ide ni prognoza ni reveal.
     */
    @Query("SELECT b FROM Booking b WHERE b.status = 'CONFIRMED' " +
           "AND (b.assignedDestination IS NULL OR TRIM(b.assignedDestination) = '') " +
           "AND b.selectedDate.departureDate >= :today " +
           "AND b.selectedDate.departureDate <= :until " +
           "ORDER BY b.selectedDate.departureDate ASC")
    List<Booking> findConfirmedWithoutDestination(@Param("today") LocalDate today, @Param("until") LocalDate until);

    // ── Zbirne fakture agencijama ────────────────────────────────────────────

    /**
     * Završena putovanja agencije koja još nisu ni u jednoj zbirnoj fakturi - kandidati za sledeću
     * fakturu. Namerno BEZ filtera po settlementStatus: da li je obračun spreman odlučuje kalkulator,
     * a rezervacija je „fakturisana" samo ako pokazuje na zbirnu fakturu. (Filter po statusu je
     * jednom tiho izostavio rezervaciju koja je kroz stari tok po rezervaciji imala INVOICED/VOIDED.)
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.agencyIdSnapshot = :agencyId " +
           "  AND b.status = com.escapii.model.BookingStatus.COMPLETED " +
           "  AND b.agencyInvoice IS NULL " +
           "ORDER BY b.selectedDate.returnDate ASC, b.id ASC")
    List<Booking> findCompletedNotInvoiced(@Param("agencyId") Long agencyId);

    /**
     * Ostaci starog toka fakturisanja po rezervaciji (pre 2026-09-17): status INVOICED/PAID/VOIDED
     * bez veze ka zbirnoj fakturi. LegacySettlementReset ih pri startu vraća u aktivan tok.
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.agencyInvoice IS NULL " +
           "  AND b.settlementStatus IN (com.escapii.model.SettlementStatus.INVOICED, " +
           "                             com.escapii.model.SettlementStatus.PAID, " +
           "                             com.escapii.model.SettlementStatus.VOIDED) " +
           "ORDER BY b.id ASC")
    List<Booking> findLegacySettledWithoutInvoice();

    /**
     * Potvrđene/završene rezervacije na terminu koje još nisu ni u jednoj zbirnoj fakturi - one
     * PRATE promenu agencije na terminu (AdminService.assignAgencyToDate).
     */
    @Query("SELECT b FROM Booking b " +
           "WHERE b.selectedDate.id = :dateId " +
           "  AND b.agencyInvoice IS NULL " +
           "  AND b.status IN (com.escapii.model.BookingStatus.CONFIRMED, com.escapii.model.BookingStatus.COMPLETED) " +
           "ORDER BY b.id ASC")
    List<Booking> findOnDateNotInvoiced(@Param("dateId") Long dateId);

    /**
     * Potvrđene/završene rezervacije bez zbirne fakture čiji snimak agencije NIJE agencija koja
     * trenutno stoji na terminu. BookingAgencySync ih pri startu usklađuje: termin je izvor istine
     * dok rezervacija nije fakturisana.
     */
    @Query("SELECT b FROM Booking b JOIN b.selectedDate d JOIN d.agency a " +
           "WHERE b.agencyInvoice IS NULL " +
           "  AND b.status IN (com.escapii.model.BookingStatus.CONFIRMED, com.escapii.model.BookingStatus.COMPLETED) " +
           "  AND (b.agencyIdSnapshot IS NULL OR b.agencyIdSnapshot <> a.id) " +
           "ORDER BY b.id ASC")
    List<Booking> findAgencyOutOfSync();

    /**
     * Rezervacije koje doplatu za solo putnika još nose kao zasebnu stavku, a nisu u zbirnoj
     * fakturi. SoloSurchargeMerge ih pri startu prepakuje u BASE_PACKAGE (fakturisane se ne diraju).
     */
    @Query("SELECT DISTINCT b FROM Booking b JOIN b.financialItems fi " +
           "WHERE fi.itemType = com.escapii.model.ItemType.SOLO_SURCHARGE " +
           "  AND b.agencyInvoice IS NULL " +
           "ORDER BY b.id ASC")
    List<Booking> findWithSoloSurchargeItem();

    /** Koliko rezervacija agencije je u datom statusu, a još nije fakturisano (npr. CONFIRMED = putovanja u toku). */
    long countByAgencyIdSnapshotAndStatusAndAgencyInvoiceIsNull(Long agencyId, BookingStatus status);

    List<Booking> findByAgencyInvoiceIdOrderByIdAsc(Long agencyInvoiceId);
}
