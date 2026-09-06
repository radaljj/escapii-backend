package com.escapii.service;

import com.escapii.model.Booking;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import com.escapii.service.impl.VoucherLedger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zaključava invarijantu poklon vaučera:
 * {@code usedAmount == zbir zaključanih iznosa svih živih rezervacija}.
 *
 * Vaučer SME da se koristi više puta - iznos je novčani, ne "iskorišćen/neiskorišćen".
 * Ono što ne sme jeste da se oslobodi više nego što je uzeto, a upravo to se dešavalo:
 * vraćanje iz otkazanog stanja zaključavalo je min(preostalo, popust), a otkazivanje
 * je oslobađalo ceo popust. Razlika se stvarala ni iz čega.
 */
class VoucherLedgerTest {

    private final VoucherLedger ledger = new VoucherLedger();

    private GiftVoucher vaucer(String iznos) {
        GiftVoucher v = new GiftVoucher();
        v.setCode("ESC-TEST01");
        v.setAmount(new BigDecimal(iznos));
        v.setUsedAmount(BigDecimal.ZERO);
        v.setStatus(VoucherStatus.ACTIVE);
        return v;
    }

    private Booking rezervacija(long id) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingRef("ESC-test000" + id);
        return b;
    }

    @Test
    void zakljucavanjePamtiIznosNaRezervaciji() {
        GiftVoucher v = vaucer("300");
        Booking a = rezervacija(1);

        BigDecimal uzeto = ledger.lock(a, v, new BigDecimal("150"));

        assertEquals(0, uzeto.compareTo(new BigDecimal("150")));
        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("150")));
        assertEquals(0, a.getVoucherLockedAmount().compareTo(new BigDecimal("150")));
        assertEquals(VoucherStatus.ACTIVE, v.getStatus(), "ostalo je jos 150 - vaucer je i dalje upotrebljiv");
    }

    /** Isti vaučer na dve rezervacije je normalan tok, ne greška. */
    @Test
    void istiVaucerNaDveRezervacijeDoIznosa() {
        GiftVoucher v = vaucer("300");
        Booking a = rezervacija(1);
        Booking b = rezervacija(2);

        ledger.lock(a, v, new BigDecimal("150"));
        ledger.lock(b, v, new BigDecimal("150"));

        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("300")));
        assertEquals(VoucherStatus.RESERVED, v.getStatus(), "potrosen do kraja");
    }

    @Test
    void zakljucavaSeSamoOnoStoJePreostalo() {
        GiftVoucher v = vaucer("300");
        ledger.lock(rezervacija(1), v, new BigDecimal("250"));

        Booking b = rezervacija(2);
        BigDecimal uzeto = ledger.lock(b, v, new BigDecimal("200"));   // trazi vise nego sto ima

        assertEquals(0, uzeto.compareTo(new BigDecimal("50")));
        assertEquals(0, b.getVoucherLockedAmount().compareTo(new BigDecimal("50")));
        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("300")),
                "nikad preko iznosa vaucera");
    }

    @Test
    void oslobadjanjeVracaTacnoOnoStoJeUzeto() {
        GiftVoucher v = vaucer("300");
        Booking a = rezervacija(1);
        ledger.lock(a, v, new BigDecimal("150"));

        BigDecimal vraceno = ledger.release(a, v);

        assertEquals(0, vraceno.compareTo(new BigDecimal("150")));
        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.ZERO));
        assertNull(a.getVoucherLockedAmount(), "posle oslobadjanja rezervacija ne drzi nista");
    }

    /** Niz "otkazi pa obrisi" - drugo oslobadjanje mora biti bez efekta. */
    @Test
    void dvostrukoOslobadjanjeNemaEfekta() {
        GiftVoucher v = vaucer("300");
        Booking a = rezervacija(1);
        ledger.lock(a, v, new BigDecimal("150"));

        ledger.release(a, v);
        BigDecimal drugiPut = ledger.release(a, v);

        assertEquals(0, drugiPut.compareTo(BigDecimal.ZERO));
        assertEquals(0, v.getUsedAmount().compareTo(BigDecimal.ZERO),
                "drugi poziv ne sme da spusti usedAmount ispod nule niti da kreditira vaucer");
    }

    /**
     * Tacan scenario iz revizije. Ranije je zavrsavao sa usedAmount = 0 iako rezervacija
     * B i dalje nosi 200 EUR popusta - vaucer bi prijavljivao 300 EUR slobodno.
     */
    @Test
    void otkaziPaDrugiPotrosiPaVratiPaOtkaziNePrekredituje() {
        GiftVoucher v = vaucer("300");
        Booking a = rezervacija(1);
        Booking b = rezervacija(2);

        ledger.lock(a, v, new BigDecimal("300"));          // A uzme sve
        ledger.release(a, v);                              // A otkazana -> 0
        ledger.lock(b, v, new BigDecimal("200"));          // B uzme 200

        // A se vraca na cekanje: moze da zakljuca samo preostalih 100, ne svojih 300.
        BigDecimal ponovo = ledger.lock(a, v, new BigDecimal("300"));
        assertEquals(0, ponovo.compareTo(new BigDecimal("100")));
        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("300")));

        // A se ponovo otkazuje: sme da vrati samo tih 100.
        BigDecimal vraceno = ledger.release(a, v);

        assertEquals(0, vraceno.compareTo(new BigDecimal("100")),
                "vraca se stvarno zakljucano, ne trazeni popust");
        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("200")),
                "B i dalje drzi 200 - vaucer ne sme prijaviti da je slobodan");
    }

    @Test
    void statusSeIzvodiIzIznosaAneRucno() {
        GiftVoucher v = vaucer("100");
        Booking a = rezervacija(1);
        Booking b = rezervacija(2);

        ledger.lock(a, v, new BigDecimal("100"));
        assertEquals(VoucherStatus.RESERVED, v.getStatus());

        // Druga rezervacija ne moze nista - vaucer je pun.
        assertEquals(0, ledger.lock(b, v, new BigDecimal("50")).compareTo(BigDecimal.ZERO));
        assertNull(b.getVoucherLockedAmount());

        ledger.release(a, v);
        assertEquals(VoucherStatus.ACTIVE, v.getStatus(), "oslobodjen - opet upotrebljiv");
    }

    @Test
    void rezervacijaKojaNistaNeDrziNemaSta() {
        GiftVoucher v = vaucer("300");
        v.setUsedAmount(new BigDecimal("200"));   // drugi ga drze

        BigDecimal vraceno = ledger.release(rezervacija(9), v);

        assertEquals(0, vraceno.compareTo(BigDecimal.ZERO));
        assertEquals(0, v.getUsedAmount().compareTo(new BigDecimal("200")),
                "tudji zakljucan iznos se ne dira");
    }
}
