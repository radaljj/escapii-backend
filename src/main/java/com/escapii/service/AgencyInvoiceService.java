package com.escapii.service;

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
}
