package com.escapii.service;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.GiftVoucherRevealResponse;

/**
 * Vaučer POKLONJENOG PUTOVANJA - PDF koji kupac dobija u prilogu potvrde
 * rezervacije kad je putovanje poklon, da ga odštampa ili prosledi obdarenom.
 * Sam prilog pravi i šalje {@code BookingEmailService}; ovde je ono što
 * stranica /poklon i admin panel traže od vaučera.
 *
 * <p>Kod vaučera je šifra rezervacije ({@code ESC-xxxxxxxx}, velikim slovima).
 * Nema nove tabele ni kolone: važi i za poklone potvrđene pre ove izmene, a
 * šifru kupac ionako zna iz svakog svog mejla. Obdareni sa njom vidi samo ono
 * što i vaučer nosi - termin, aerodrom, putnike - nikad cenu. Isto to bi video
 * i preko "Moja rezervacija" (šifra + prezime), pa šifra ne otvara ništa novo.
 */
public interface GiftTripVoucherService {

    /**
     * Šta /poklon stranica prikaže za kod. Nevalidno ako kod nije šifra
     * potvrđenog poklona - PENDING (uplata nije legla), otkazano ili obična
     * rezervacija ne daju ništa, isto kao nepostojeći kod.
     */
    GiftVoucherRevealResponse reveal(String code);

    /**
     * Admin: ponovo pošalji potvrdu rezervacije sa vaučerom u prilogu. 404 kad
     * rezervacija ne postoji, 409 kad nije poklon ili nije potvrđena, 502 kad
     * PDF ili slanje puknu - da panel pokaže pravi razlog.
     */
    AdminBookingResponse resend(Long bookingId);
}
