package com.escapii.service.impl;

import com.escapii.model.Booking;
import com.escapii.model.GiftVoucher;
import com.escapii.model.VoucherStatus;
import com.escapii.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Jedino mesto koje menja koliko je novca zauzeto na poklon vaučeru.
 *
 * <p><b>Invarijanta koju ova klasa čuva:</b>
 * {@code voucher.usedAmount == zbir voucherLockedAmount svih živih rezervacija}.
 *
 * <p>Ranije je ista logika bila prepisana na tri mesta (otkazivanje, brisanje,
 * automatsko otkazivanje u scheduleru) i vremenom su se razišla. Konkretno: vraćanje
 * rezervacije iz otkazanog stanja zaključavalo je {@code min(preostalo, popust)} jer
 * je neko drugi u međuvremenu mogao potrošiti deo istog vaučera, ali je otkazivanje
 * i dalje oslobađalo <i>ceo popust</i>. Razlika se stvarala ni iz čega i vaučer je
 * prijavljivao više novca nego što ima.
 *
 * <p>Zato oslobađanje ide isključivo po {@link Booking#getVoucherLockedAmount()} -
 * po onome što je stvarno uzeto, nikad po traženom popustu. Polje se pri oslobađanju
 * postavlja na {@code null}, pa je i dvostruko oslobađanje nemoguće: drugi poziv
 * nema šta da oduzme.
 *
 * <p>Klasa samo menja objekte u memoriji; čuvanje ostaje na pozivaocu, koji je već
 * u transakciji i drži {@code findByCodeForUpdate} lock nad vaučerom.
 */
@Slf4j
@Component
public class VoucherLedger {

    /**
     * Zaključava do {@code trazeno} na vaučeru i pamti na rezervaciji koliko je stvarno
     * uzeto. Ako je preostalo manje od traženog, zaključava se samo preostalo.
     *
     * @return iznos koji je stvarno zaključan (može biti 0 ako na vaučeru nema ništa)
     */
    public BigDecimal lock(Booking booking, GiftVoucher voucher, BigDecimal trazeno) {
        if (trazeno == null || trazeno.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal preostalo = voucher.getAmount().subtract(voucher.getUsedAmount())
                .max(BigDecimal.ZERO);
        BigDecimal zakljucano = preostalo.min(trazeno);
        if (zakljucano.signum() <= 0) {
            log.warn("[Voucher] {} nema preostalog iznosa - rezervacija {} ne zaključava ništa",
                    LogUtils.maskVoucherCode(voucher.getCode()), booking.getBookingRef());
            return BigDecimal.ZERO;
        }

        BigDecimal novoUzeto = voucher.getUsedAmount().add(zakljucano);
        voucher.setUsedAmount(novoUzeto);
        voucher.setUsedInBookingRef(booking.getId());
        voucher.setStatus(statusZa(voucher, novoUzeto));
        // Rezervacija od sada drži tačno ovoliko - i oslobodiće tačno ovoliko.
        booking.setVoucherLockedAmount(zakljucano);
        return zakljucano;
    }

    /**
     * Oslobađa tačno ono što rezervacija drži i briše zapis o tome.
     *
     * <p>Idempotentno: drugi poziv nad istom rezervacijom vidi {@code null} i ne radi
     * ništa. To je i strukturna zaštita od niza "otkaži pa obriši", koji je ranije
     * oduzimao isti iznos dvaput.
     *
     * @return iznos koji je oslobođen (0 ako rezervacija ništa nije držala)
     */
    public BigDecimal release(Booking booking, GiftVoucher voucher) {
        BigDecimal zakljucano = booking.getVoucherLockedAmount();
        if (zakljucano == null || zakljucano.signum() <= 0) {
            booking.setVoucherLockedAmount(null);
            return BigDecimal.ZERO;
        }

        BigDecimal novoUzeto = voucher.getUsedAmount().subtract(zakljucano).max(BigDecimal.ZERO);
        voucher.setUsedAmount(novoUzeto);
        voucher.setStatus(statusZa(voucher, novoUzeto));
        // Postojeće ponašanje: oznaka "iskorišćen u rezervaciji" se briše pri
        // oslobađanju. Zaključan je i testom (VoucherRelockOnUncancelTest).
        voucher.setUsedAt(null);
        voucher.setUsedInBookingRef(null);
        booking.setVoucherLockedAmount(null);
        return zakljucano;
    }

    /**
     * Status se izvodi iz iznosa, ne postavlja se ručno: potrošen do kraja je RESERVED
     * (čeka potvrdu putovanja), sve ispod je ACTIVE i može se dalje trošiti. Ranije se
     * pri otkazivanju bezuslovno postavljalo ACTIVE, pa je vaučer koji druge rezervacije
     * i dalje drže u celini izgledao kao slobodan.
     */
    private VoucherStatus statusZa(GiftVoucher voucher, BigDecimal uzeto) {
        return uzeto.compareTo(voucher.getAmount()) >= 0
                ? VoucherStatus.RESERVED
                : VoucherStatus.ACTIVE;
    }
}
