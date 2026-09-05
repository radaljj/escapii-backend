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

    boolean existsByAirportCodeIgnoreCase(String airportCode);

    /**
     * Nalazi destinaciju po imenu koje je admin rukom otkucao na rezervaciji
     * ({@code Booking.assignedDestination}).
     *
     * <p>To polje je slobodan tekst i nije strani ključ ka ovoj tabeli, pa je ovo
     * jedina veza između rezervacije i destinacije. Poredi se i srpsko i englesko
     * ime, bez obzira na velika slova i suvišne razmake - admin ume da otkuca i
     * "Firenca" i "Florence" za istu stvar.
     *
     * <p>Namerno NE radi delimično poklapanje: "Rim" se ne sme poklopiti sa
     * "Rimini". Ako nema tačnog pogotka, radije nema ni partnerskih linkova.
     */
    @Query("""
           SELECT d FROM Destination d
            WHERE LOWER(TRIM(d.name))   = LOWER(TRIM(:naziv))
               OR LOWER(TRIM(d.nameEn)) = LOWER(TRIM(:naziv))
           """)
    List<Destination> findByAnyNameIgnoreCase(@Param("naziv") String naziv);
}
