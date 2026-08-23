-- Broj pasoša putnika + dokaz prihvatanja pravnih dokumenata.
--
-- 1) passport_serial_number: postojeća kolona passport_number je uprkos nazivu
--    čuvala ZEMLJU pasoša (dropdown), pa je zadržana takva radi postojećih
--    podataka - pravi serijski broj ide u novu kolonu.
--
-- 2) consent_*: checkbox-evi za uslove/privatnost/GDPR postojali su samo na
--    frontu i zaobilazili se direktnim API pozivom. Sada ih backend validira,
--    a ovde se čuva dokaz - kada, koja verzija dokumenata i na kom jeziku.

ALTER TABLE booking_passengers ADD COLUMN IF NOT EXISTS passport_serial_number VARCHAR(50);

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS consent_accepted_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS consent_version     VARCHAR(20);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS consent_lang        VARCHAR(5);
