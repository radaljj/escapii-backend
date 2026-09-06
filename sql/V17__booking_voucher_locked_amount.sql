-- Koliko je OVA rezervacija stvarno zakljucala na vauceru.
--
-- Do sada se oslobadjalo po voucher_discount, tj. po iznosu koji je rezervacija
-- TRAZILA. To nije isto sto i iznos koji je stvarno zakljucan: vracanje iz
-- otkazanog stanja zakljucava samo min(preostalo, popust), jer je neko drugi u
-- medjuvremenu mogao potrositi deo istog vaucera. Sledece otkazivanje je onda
-- vracalo vise nego sto je uzeto i vaucer je dobijao novac ni iz cega.
--
-- Sa ovom kolonom vazi invarijanta:
--   gift_vouchers.used_amount == SUM(voucher_locked_amount) svih zivih rezervacija
--
-- NULL znaci "ova rezervacija ne drzi nista na vauceru" - ili nikad nije ni
-- zakljucala, ili je vec oslobodila. Zato je i dvostruko oslobadjanje sada
-- nemoguce: drugi poziv vidi NULL i nema sta da oduzme.

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS voucher_locked_amount NUMERIC(12,2);

-- Popuna za postojece redove. Zive rezervacije sa vaucerom drze tacno onoliko
-- koliko im je upisano kao popust (do sada su ta dva iznosa bila ista pri
-- kreiranju). Otkazane namerno ostaju NULL - njima je iznos vec vracen, pa bi
-- popunjavanje znacilo da se moze osloboditi jos jednom.
UPDATE bookings
   SET voucher_locked_amount = voucher_discount
 WHERE applied_voucher_code IS NOT NULL
   AND voucher_discount IS NOT NULL
   AND voucher_discount > 0
   AND status <> 'CANCELLED'
   AND voucher_locked_amount IS NULL;
