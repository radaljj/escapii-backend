package com.escapii.service;

import com.escapii.dto.AgencyInvoiceBreakdown;
import com.escapii.dto.AgencyInvoicePreview;
import com.escapii.dto.AgencyInvoiceResponse;

import java.util.List;

/**
 * Zbirne fakture agencijama: umesto fakture po rezervaciji, Escapii povremeno (oko dva
 * puta mesečno, ručno iz panela) fakturiše agenciji zbir svoje zarade po završenim
 * putovanjima koja još nisu fakturisana. Vaučere plaća agencija, pa se ne odbijaju.
 */
public interface AgencyInvoiceService {

    /** Šta bi ušlo u fakturu sada, sa iznosom i razlozima zašto nešto ne ulazi. */
    AgencyInvoicePreview preview(Long agencyId);

    /** Pravi PDF, šalje ga agenciji i zaključava obuhvaćene rezervacije (INVOICED). */
    AgencyInvoiceResponse create(Long agencyId, String description);

    List<AgencyInvoiceResponse> listForAgency(Long agencyId);

    List<AgencyInvoiceResponse> listAll();

    AgencyInvoiceResponse markPaid(Long invoiceId);

    AgencyInvoiceResponse unmarkPaid(Long invoiceId);

    /** Storno: faktura ostaje u istoriji kao VOIDED, rezervacije se vraćaju u red za sledeću. */
    AgencyInvoiceResponse voidInvoice(Long invoiceId, String reason);

    /** Ponovo šalje sačuvani PDF na trenutni mejl agencije. */
    AgencyInvoiceResponse resend(Long invoiceId);

    record Pdf(String fileName, byte[] bytes) {}

    Pdf pdf(Long invoiceId);

    /**
     * Probni PDF fakture: isti obračun kao {@link #preview}, broj „PREGLED", bez upisa, bez
     * trošenja sekvence i bez mejla - da admin vidi kako bi faktura izgledala pre klika.
     * 409 kad nema šta da se fakturiše (isti razlog kao {@code blocker} u pregledu).
     *
     * @param description opis stavke; null = predlog iz pregleda
     */
    Pdf previewPdf(Long agencyId, String description);

    /** Obrazloženje po rezervaciji i stavci za ono što bi ušlo u fakturu sada. */
    AgencyInvoiceBreakdown previewBreakdown(Long agencyId);

    /** Obrazloženje po rezervaciji i stavci za već izdatu fakturu. */
    AgencyInvoiceBreakdown invoiceBreakdown(Long invoiceId);
}
