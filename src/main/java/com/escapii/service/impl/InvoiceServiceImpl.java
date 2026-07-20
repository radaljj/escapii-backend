package com.escapii.service.impl;

import com.escapii.dto.AdminBookingResponse;
import com.escapii.dto.GiftVoucherResponse;
import com.escapii.mapper.AdminBookingMapper;
import com.escapii.model.AvailableDate;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.GiftVoucher;
import com.escapii.model.InvoiceSequence;
import com.escapii.model.VoucherStatus;
import com.escapii.repository.BookingRepository;
import com.escapii.repository.GiftVoucherRepository;
import com.escapii.repository.InvoiceSequenceRepository;
import com.escapii.service.InvoiceService;
import com.escapii.service.email.InvoiceEmailService;
import com.escapii.service.invoice.InvoiceData;
import com.escapii.service.invoice.InvoicePdfService;
import com.escapii.service.voucher.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    @Value("${app.company.name:Escapii d.o.o.}")
    private String companyName;

    @Value("${app.company.address:Beograd, Srbija}")
    private String companyAddress;

    @Value("${app.company.pib:000000000}")
    private String companyPib;

    @Value("${app.company.mb:00000000}")
    private String companyMb;

    @Value("${app.company.account:000-0000000000000-00}")
    private String companyAccount;

    @Value("${app.company.bank:placeholder banka}")
    private String companyBank;

    @Value("${app.company.email:hello@escapii.rs}")
    private String companyEmail;

    @Value("${app.company.website:escapii.rs}")
    private String companyWebsite;

    @Value("${app.invoice.due-days:3}")
    private int invoiceDueDays;

    private final BookingRepository         bookingRepository;
    private final GiftVoucherRepository      giftVoucherRepository;
    private final InvoiceSequenceRepository  invoiceSequenceRepository;
    private final InvoicePdfService          invoicePdfService;
    private final InvoiceEmailService        invoiceEmailService;
    private final QrCodeGenerator            qrCodeGenerator;
    private final AdminBookingMapper         adminBookingMapper;

    @Override
    @Transactional
    public AdminBookingResponse sendInvoice(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Rezervacija nije pronađena: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profaktura se može poslati samo za PENDING rezervacije (trenutno: " + booking.getStatus() + ")");
        }

        if (booking.getInvoiceSentAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profaktura je već poslata " + booking.getInvoiceSentAt());
        }

        AvailableDate date = booking.getSelectedDate();
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Rezervacija nema odabrani datum polaska");
        }

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        InvoiceSequence seq = invoiceSequenceRepository.findByYear(year)
                .orElseGet(() -> invoiceSequenceRepository.save(new InvoiceSequence(year)));
        seq.setLastSeq(seq.getLastSeq() + 1);
        invoiceSequenceRepository.save(seq);
        String invoiceNum = "ESC-INV-" + year + "-" + String.format("%04d", seq.getLastSeq());

        // totalPriceAll je već post-discount (BookingServiceImpl smanjuje ga pri primeni vaučera)
        int total    = booking.getTotalPriceAll();
        int discount = booking.getVoucherDiscount() != null ? booking.getVoucherDiscount() : 0;
        int subtotal = total + discount; // originalna cena pre vaučera

        // IPS QR kod (NBS standard) - iznos RSD0 jer se preračunava po kursu NBS na dan uplate
        String ipsContent = buildIpsQrContent(booking, total);
        String ipsQrDataUri = qrCodeGenerator.pngDataUri(ipsContent, 300);

        InvoiceData invoiceData = new InvoiceData(
                invoiceNum,
                today,
                today.plusDays(invoiceDueDays),
                "Escapii putovanje iznenađenja",
                booking.getFirstName(),
                booking.getLastName(),
                booking.getEmail(),
                booking.getPhone(),
                booking.getBookingRef(),
                date.getDepartureDate(),
                date.getReturnDate(),
                booking.getNumberOfTravelers(),
                subtotal,
                discount,
                booking.getAppliedVoucherCode(),
                total,
                companyName, companyAddress, companyPib, companyMb,
                companyAccount, companyBank, companyEmail, companyWebsite,
                ipsQrDataUri
        );

        byte[] pdf = invoicePdfService.generate(invoiceData);

        booking.setInvoiceNumber(invoiceNum);
        booking.setInvoiceSentAt(LocalDateTime.now());
        Booking saved = bookingRepository.save(booking);

        invoiceEmailService.sendInvoiceToClient(booking, pdf, invoiceNum);
        log.info("[Invoice] Profaktura {} poslata za rezervaciju {} na {}",
                invoiceNum, booking.getBookingRef(), booking.getEmail());

        return adminBookingMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public GiftVoucherResponse sendVoucherInvoice(Long voucherId) {
        GiftVoucher voucher = giftVoucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Vaučer nije pronađen: " + voucherId));

        if (voucher.getStatus() != VoucherStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profaktura se može poslati samo za PENDING vaučere (trenutno: " + voucher.getStatus() + ")");
        }

        if (voucher.getInvoiceSentAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profaktura je već poslata " + voucher.getInvoiceSentAt());
        }

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        InvoiceSequence seq = invoiceSequenceRepository.findByYear(year)
                .orElseGet(() -> invoiceSequenceRepository.save(new InvoiceSequence(year)));
        seq.setLastSeq(seq.getLastSeq() + 1);
        invoiceSequenceRepository.save(seq);
        String invoiceNum = "ESC-INV-" + year + "-" + String.format("%04d", seq.getLastSeq());

        int total = voucher.getAmount().intValue();

        String account = companyAccount.replaceAll("[^0-9]", "");
        String formattedAccount = account.length() == 18
                ? account.substring(0, 3) + "-" + account.substring(3, 16) + "-" + account.substring(16)
                : companyAccount;
        String ref = voucher.getCode().replace("ESC-", "").replace("-", "");
        String ipsContent = "K:PR|V:01|C:1" +
                "|R:" + formattedAccount +
                "|N:" + companyName.replace("|", "") +
                "|I:RSD0|SF:289" +
                "|S:Poklon vaucer " + voucher.getCode() + " " + total + "EUR" +
                "|RO:97" + ref;
        String ipsQrDataUri = qrCodeGenerator.pngDataUri(ipsContent, 300);

        String buyerName  = voucher.getBuyerName() != null ? voucher.getBuyerName() : "";
        int spaceIdx = buyerName.indexOf(' ');
        String firstName = spaceIdx > 0 ? buyerName.substring(0, spaceIdx) : buyerName;
        String lastName  = spaceIdx > 0 ? buyerName.substring(spaceIdx + 1) : "";

        InvoiceData invoiceData = new InvoiceData(
                invoiceNum,
                today,
                today.plusDays(invoiceDueDays),
                "Poklon vaučer · Escapii putovanje iznenađenja",
                firstName,
                lastName,
                voucher.getBuyerEmail(),
                "",
                voucher.getCode(),
                null,
                null,
                null,
                total,
                0,
                null,
                total,
                companyName, companyAddress, companyPib, companyMb,
                companyAccount, companyBank, companyEmail, companyWebsite,
                ipsQrDataUri
        );

        byte[] pdf = invoicePdfService.generate(invoiceData);

        voucher.setInvoiceNumber(invoiceNum);
        voucher.setInvoiceSentAt(LocalDateTime.now());
        GiftVoucher savedVoucher = giftVoucherRepository.save(voucher);

        invoiceEmailService.sendVoucherInvoiceToClient(voucher, pdf, invoiceNum);
        log.info("[Invoice] Profaktura {} poslata za vaučer #{} na {}",
                invoiceNum, voucherId, voucher.getBuyerEmail());

        return new GiftVoucherResponse(savedVoucher);
    }

    private String buildIpsQrContent(Booking booking, int totalEur) {
        // IPS NBS QR standard (v01)
        // Iznos se ne upisuje (I:RSD0) jer se EUR preračunava po kursu NBS na dan uplate
        String account = companyAccount.replaceAll("[^0-9]", "");
        String formattedAccount = account.length() == 18
                ? account.substring(0, 3) + "-" + account.substring(3, 16) + "-" + account.substring(16)
                : companyAccount;
        String ref = booking.getBookingRef().replace("ESC-", "").replace("-", "");
        String desc = "Rezervacija " + booking.getBookingRef() + " " + totalEur + "EUR";
        String safeName = companyName.replace("|", "");

        return "K:PR|V:01|C:1" +
               "|R:" + formattedAccount +
               "|N:" + safeName +
               "|I:RSD0" +
               "|SF:289" +
               "|S:" + desc +
               "|RO:97" + ref;
    }
}
