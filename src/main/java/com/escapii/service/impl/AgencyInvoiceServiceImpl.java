package com.escapii.service.impl;

import com.escapii.dto.AgencyInvoicePreview;
import com.escapii.dto.AgencyInvoiceResponse;
import com.escapii.dto.AgencySettlementResponse;
import com.escapii.model.Agency;
import com.escapii.model.AgencyInvoice;
import com.escapii.model.AgencyInvoiceSequence;
import com.escapii.model.AgencyInvoiceStatus;
import com.escapii.model.Booking;
import com.escapii.model.BookingStatus;
import com.escapii.model.SettlementStatus;
import com.escapii.repository.AgencyInvoiceRepository;
import com.escapii.repository.AgencyInvoiceSequenceRepository;
import com.escapii.repository.AgencyRepository;
import com.escapii.repository.BookingRepository;
import com.escapii.service.AgencyInvoiceService;
import com.escapii.service.AgencySettlementCalculator;
import com.escapii.service.email.InvoiceEmailService;
import com.escapii.service.invoice.AgencyInvoiceData;
import com.escapii.service.invoice.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zbirne fakture agencijama.
 *
 * <p><b>Šta ulazi:</b> rezervacije agencije sa statusom COMPLETED (putovanje završeno),
 * koje nisu ni u jednoj fakturi, i kojima kalkulator kaže da je obračun spreman (uneti
 * troškovi, stavke se slažu). Završene bez troškova se preskaču i prijave u pregledu -
 * ulaze u sledeću fakturu kad se troškovi unesu. Potvrđene kojima put još traje se ne
 * fakturišu (Markova odluka: samo završena, pa otkaz posle fakture ne postoji).
 *
 * <p><b>Iznos:</b> zbir {@code escapiiEarnings} po rezervaciji - Escapii deo marže plus
 * stavke koje su 100% Escapii. Vaučer se NE odbija: od 2026-09 sve uplate, i za vaučere,
 * idu agenciji, pa novac od vaučera nije kod Escapii-ja.
 *
 * <p><b>Redosled pri pravljenju:</b> zaključaj agenciju → obračun → broj iz sekvence →
 * PDF → mejl → tek onda upis fakture i zaključavanje rezervacija. Ako mejl ne ode, cela
 * transakcija se vraća (i broj u sekvenci), pa admin samo klikne ponovo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgencyInvoiceServiceImpl implements AgencyInvoiceService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy.");

    private final AgencyRepository                agencyRepository;
    private final BookingRepository               bookingRepository;
    private final AgencyInvoiceRepository         invoiceRepository;
    private final AgencyInvoiceSequenceRepository sequenceRepository;
    private final AgencySettlementCalculator      calculator;
    private final InvoicePdfService               pdfService;
    private final InvoiceEmailService             emailService;

    @Value("${app.company.name:Escapii d.o.o.}")           private String companyName;
    @Value("${app.company.address:Beograd, Srbija}")       private String companyAddress;
    @Value("${app.company.pib:000000000}")                 private String companyPib;
    @Value("${app.company.mb:00000000}")                   private String companyMb;
    @Value("${app.company.account:000-0000000000000-00}")  private String companyAccount;
    @Value("${app.company.bank:placeholder banka}")        private String companyBank;
    @Value("${app.company.email:info@escapii.rs}")         private String companyEmail;
    @Value("${app.company.website:escapii.rs}")            private String companyWebsite;

    /** Rok plaćanja fakture agenciji, u danima od izdavanja. */
    @Value("${app.invoice.agency-due-days:8}")
    private int dueDays;

    /** Radni obračun - isti za pregled i za pravljenje fakture. */
    record Obracun(Agency agency, List<Booking> included, List<AgencyInvoicePreview.Line> lines,
                   List<AgencyInvoicePreview.Skipped> skipped, int inProgress,
                   BigDecimal amount, LocalDate periodFrom, LocalDate periodTo) {}

    // ── Pregled ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AgencyInvoicePreview preview(Long agencyId) {
        Agency a = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agencija ne postoji: " + agencyId));
        return toPreview(obracun(a));
    }

    private Obracun obracun(Agency a) {
        List<Booking> kandidati = bookingRepository.findCompletedNotInvoiced(a.getId());
        List<Booking> included = new ArrayList<>();
        List<AgencyInvoicePreview.Line> lines = new ArrayList<>();
        List<AgencyInvoicePreview.Skipped> skipped = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Booking b : kandidati) {
            AgencySettlementResponse s = calculator.calculate(b);
            LocalDate polazak = b.getSelectedDate() != null ? b.getSelectedDate().getDepartureDate() : null;
            LocalDate povratak = b.getSelectedDate() != null ? b.getSelectedDate().getReturnDate() : null;
            if (!s.isReadyForInvoice()) {
                String razlog = s.getValidationErrors() == null || s.getValidationErrors().isEmpty()
                        ? "obračun nije spreman" : s.getValidationErrors().get(0);
                skipped.add(new AgencyInvoicePreview.Skipped(b.getId(), b.getBookingRef(), povratak, razlog));
                continue;
            }
            BigDecimal zarada = s.getEscapiiEarnings() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : s.getEscapiiEarnings().setScale(2, RoundingMode.HALF_UP);
            included.add(b);
            lines.add(new AgencyInvoicePreview.Line(b.getId(), b.getBookingRef(), polazak, povratak,
                    b.getNumberOfTravelers(), zarada));
            amount = amount.add(zarada);
        }

        int inProgress = (int) bookingRepository.countByAgencyIdSnapshotAndStatusAndAgencyInvoiceIsNull(
                a.getId(), BookingStatus.CONFIRMED);
        LocalDate from = lines.stream().map(AgencyInvoicePreview.Line::returnDate).filter(Objects::nonNull)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate to = lines.stream().map(AgencyInvoicePreview.Line::returnDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElse(null);
        return new Obracun(a, included, lines, skipped, inProgress, amount, from, to);
    }

    private AgencyInvoicePreview toPreview(Obracun o) {
        String blocker = blocker(o);
        String opis = o.periodFrom() == null
                ? "Marketinške usluge"
                : "Marketinške usluge za period " + DATUM.format(o.periodFrom()) + " – " + DATUM.format(o.periodTo());
        Agency a = o.agency();
        return new AgencyInvoicePreview(a.getId(), a.getName(), a.getContactEmail(), o.amount(),
                o.included().size(), o.lines(), o.skipped(), o.inProgress(),
                o.periodFrom(), o.periodTo(), opis, blocker == null, blocker);
    }

    /** null = može da se fakturiše; inače razlog koji panel prikaže umesto dugmeta. */
    private static String blocker(Obracun o) {
        String mejl = o.agency().getContactEmail();
        if (mejl == null || mejl.isBlank()) {
            return "Agencija nema upisan mejl - dodaj ga kroz „Izmeni“ pa pokušaj ponovo.";
        }
        if (o.included().isEmpty()) {
            return o.skipped().isEmpty()
                    ? "Nema završenih putovanja koja čekaju fakturu."
                    : "Nijedno završeno putovanje nema unete troškove - unesi ih pa pokušaj ponovo.";
        }
        if (o.amount().signum() <= 0) {
            return "Iznos za fakturu je 0 € - nema šta da se fakturiše.";
        }
        return null;
    }

    // ── Pravljenje ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AgencyInvoiceResponse create(Long agencyId, String description) {
        String opis = description == null ? "" : description.trim();
        if (opis.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stavka fakture je obavezna.");
        }
        if (opis.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stavka fakture može imati najviše 500 znakova.");
        }
        // Lock na agenciji: drugi istovremeni klik čeka, pa zatekne rezervacije već fakturisane.
        Agency a = agencyRepository.findByIdForUpdate(agencyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agencija ne postoji: " + agencyId));
        Obracun o = obracun(a);
        String blocker = blocker(o);
        if (blocker != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, blocker);
        }

        LocalDate danas = LocalDate.now();
        LocalDateTime sada = LocalDateTime.now();
        AgencyInvoice inv = new AgencyInvoice();
        inv.setInvoiceNumber(generateNumber());
        inv.setAgencyId(a.getId());
        inv.setAgencyName(a.getName());
        inv.setAgencyEmail(a.getContactEmail().trim());
        inv.setDescription(opis);
        inv.setPeriodFrom(o.periodFrom());
        inv.setPeriodTo(o.periodTo());
        inv.setAmount(o.amount());
        inv.setBookingCount(o.included().size());
        inv.setStatus(AgencyInvoiceStatus.SENT);
        inv.setIssuedAt(danas);
        inv.setDueDate(danas.plusDays(dueDays));
        inv.setCreatedAt(sada);

        byte[] pdf = pdfService.generateAgency(pdfData(inv, a));
        inv.setPdf(pdf);

        // Mejl PRE upisa: ako slanje pukne, ništa se ne beleži - ni broj (sekvenca se vraća
        // sa transakcijom), ni faktura, ni zaključavanje rezervacija.
        if (!emailService.sendAgencyInvoice(inv, pdf)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Slanje fakture nije uspelo. Faktura nije evidentirana - pokušaj ponovo.");
        }
        inv.setSentAt(sada);
        AgencyInvoice saved = invoiceRepository.save(inv);

        for (Booking b : o.included()) {
            b.setAgencyInvoice(saved);
            b.setSettlementStatus(SettlementStatus.INVOICED);
            b.setAgencyInvoicedAt(sada);
            bookingRepository.save(b);
        }
        log.info("[Faktura] {} poslata agenciji '{}' na {}: {} EUR, {} rezervacija",
                saved.getInvoiceNumber(), a.getName(), inv.getAgencyEmail(), saved.getAmount(), o.included().size());
        return AgencyInvoiceResponse.from(saved, refs(o.included()));
    }

    private AgencyInvoiceData pdfData(AgencyInvoice inv, Agency a) {
        return new AgencyInvoiceData(inv.getInvoiceNumber(), inv.getIssuedAt(), inv.getDueDate(),
                inv.getPeriodFrom(), inv.getPeriodTo(),
                a.getName(), a.getContactName(), inv.getAgencyEmail(), inv.getDescription(), inv.getAmount(),
                companyName, companyAddress, companyPib, companyMb, companyAccount, companyBank,
                companyEmail, companyWebsite);
    }

    /**
     * ESC-AG-YYYY-NNNN. ensureYearRow je idempotentan INSERT da prva faktura u novoj godini
     * ne pukne na PK kad dva poziva stignu istovremeno; findByYear je pod PESSIMISTIC_WRITE
     * pa se inkrement serijalizuje.
     */
    private String generateNumber() {
        int year = LocalDate.now().getYear();
        sequenceRepository.ensureYearRow(year);
        AgencyInvoiceSequence seq = sequenceRepository.findByYear(year)
                .orElseThrow(() -> new IllegalStateException("Sekvenca fakture za godinu " + year + " nije inicijalizovana"));
        seq.setLastSeq(seq.getLastSeq() + 1);
        sequenceRepository.save(seq);
        return "ESC-AG-" + year + "-" + String.format("%04d", seq.getLastSeq());
    }

    // ── Liste ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AgencyInvoiceResponse> listForAgency(Long agencyId) {
        return invoiceRepository.findByAgencyIdOrderByIssuedAtDescIdDesc(agencyId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgencyInvoiceResponse> listAll() {
        return invoiceRepository.findAllByOrderByIssuedAtDescIdDesc().stream()
                .map(this::toResponse).toList();
    }

    private AgencyInvoiceResponse toResponse(AgencyInvoice inv) {
        return AgencyInvoiceResponse.from(inv, refs(bookingRepository.findByAgencyInvoiceIdOrderByIdAsc(inv.getId())));
    }

    private static List<String> refs(List<Booking> bookings) {
        return bookings.stream().map(Booking::getBookingRef).toList();
    }

    // ── Plaćeno / nije plaćeno ───────────────────────────────────────────────

    @Override
    @Transactional
    public AgencyInvoiceResponse markPaid(Long invoiceId) {
        AgencyInvoice inv = zakljucaj(invoiceId);
        if (inv.getStatus() != AgencyInvoiceStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kao plaćena može da se označi samo poslata faktura (trenutno: " + inv.getStatus() + ").");
        }
        LocalDateTime sada = LocalDateTime.now();
        inv.setStatus(AgencyInvoiceStatus.PAID);
        inv.setPaidAt(sada);
        List<Booking> bs = bookingRepository.findByAgencyInvoiceIdOrderByIdAsc(invoiceId);
        for (Booking b : bs) {
            b.setSettlementStatus(SettlementStatus.PAID);
            b.setAgencyPaidAt(sada);
            bookingRepository.save(b);
        }
        invoiceRepository.save(inv);
        log.info("[Faktura] {} označena kao plaćena ({} rezervacija)", inv.getInvoiceNumber(), bs.size());
        return AgencyInvoiceResponse.from(inv, refs(bs));
    }

    @Override
    @Transactional
    public AgencyInvoiceResponse unmarkPaid(Long invoiceId) {
        AgencyInvoice inv = zakljucaj(invoiceId);
        if (inv.getStatus() != AgencyInvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Faktura nije označena kao plaćena (trenutno: " + inv.getStatus() + ").");
        }
        inv.setStatus(AgencyInvoiceStatus.SENT);
        inv.setPaidAt(null);
        List<Booking> bs = bookingRepository.findByAgencyInvoiceIdOrderByIdAsc(invoiceId);
        for (Booking b : bs) {
            b.setSettlementStatus(SettlementStatus.INVOICED);
            b.setAgencyPaidAt(null);
            bookingRepository.save(b);
        }
        invoiceRepository.save(inv);
        log.info("[Faktura] {} vraćena na 'nije plaćena'", inv.getInvoiceNumber());
        return AgencyInvoiceResponse.from(inv, refs(bs));
    }

    // ── Storno ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AgencyInvoiceResponse voidInvoice(Long invoiceId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razlog storna je obavezan.");
        }
        AgencyInvoice inv = zakljucaj(invoiceId);
        if (inv.getStatus() == AgencyInvoiceStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Faktura je označena kao plaćena - prvo je vrati na „nije plaćena“, pa storniraj.");
        }
        if (inv.getStatus() == AgencyInvoiceStatus.VOIDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Faktura je već stornirana.");
        }
        inv.setStatus(AgencyInvoiceStatus.VOIDED);
        inv.setVoidedAt(LocalDateTime.now());
        inv.setVoidReason(reason.trim());
        // Rezervacije se vraćaju u red: status po kalkulatoru (READY ili NEEDS_COSTS), bez veze
        // ka fakturi - sledeći klik na „Fakturiši" ih ponovo obuhvata. Broj fakture ostaje u
        // istoriji na samoj fakturi.
        List<Booking> bs = bookingRepository.findByAgencyInvoiceIdOrderByIdAsc(invoiceId);
        for (Booking b : bs) {
            b.setAgencyInvoice(null);
            b.setAgencyInvoicedAt(null);
            b.setAgencyPaidAt(null);
            b.setSettlementStatus(calculator.calculate(b).isReadyForInvoice()
                    ? SettlementStatus.READY_FOR_INVOICE : SettlementStatus.NEEDS_COSTS);
            bookingRepository.save(b);
        }
        invoiceRepository.save(inv);
        log.warn("[Faktura] {} STORNIRANA ({} rezervacija vraćeno u red): {}", inv.getInvoiceNumber(), bs.size(), reason);
        return AgencyInvoiceResponse.from(inv, refs(bs));
    }

    // ── Ponovo / PDF ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AgencyInvoiceResponse resend(Long invoiceId) {
        AgencyInvoice inv = zakljucaj(invoiceId);
        if (inv.getStatus() == AgencyInvoiceStatus.VOIDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stornirana faktura se ne šalje ponovo.");
        }
        if (inv.getPdf() == null || inv.getPdf().length == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PDF ove fakture nije sačuvan - ne može da se pošalje ponovo.");
        }
        // Na TRENUTNI mejl agencije (ako je u međuvremenu promenjen), pa snimak osveži.
        agencyRepository.findById(inv.getAgencyId())
                .map(Agency::getContactEmail)
                .filter(e -> e != null && !e.isBlank())
                .ifPresent(e -> inv.setAgencyEmail(e.trim()));
        if (inv.getAgencyEmail() == null || inv.getAgencyEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Agencija nema upisan mejl.");
        }
        if (!emailService.sendAgencyInvoice(inv, inv.getPdf())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Slanje fakture nije uspelo - pokušaj ponovo.");
        }
        inv.setSentAt(LocalDateTime.now());
        invoiceRepository.save(inv);
        log.info("[Faktura] {} poslata ponovo na {}", inv.getInvoiceNumber(), inv.getAgencyEmail());
        return toResponse(inv);
    }

    @Override
    @Transactional(readOnly = true)
    public Pdf pdf(Long invoiceId) {
        AgencyInvoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Faktura ne postoji: " + invoiceId));
        if (inv.getPdf() == null || inv.getPdf().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF ove fakture nije sačuvan.");
        }
        return new Pdf("escapii-faktura-" + inv.getInvoiceNumber() + ".pdf", inv.getPdf());
    }

    private AgencyInvoice zakljucaj(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Faktura ne postoji: " + invoiceId));
    }
}
