-- V18 — putovanje kao poklon: rezervacija dobija drugu kontakt adresu.
--
-- Kupac placa i dobija fakturu; obdareni putuje i dobija prognozu, otkrice
-- destinacije i putne dokumente. Do sada je sve islo na bookings.email.
--
-- NULL, ne prazan string: "nije poklon" i "poklon bez unetog mejla" moraju da
-- se razlikuju u bazi. Prazan string bi prosao kao adresa i mejl bi tiho otisao
-- u prazno.

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS is_gift BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS gift_recipient_name  VARCHAR(200);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS gift_recipient_email VARCHAR(180);

-- Postojece rezervacije nisu pokloni; DEFAULT FALSE ih je vec popunio. Nema
-- backfill-a jer nema sta da se izvede - podatak nije postojao.

-- Zastita od poklona bez primaoca. Aplikacija to vec validira, ali mejl koji
-- treba da ode obdarenom nema gde da ode ako polja nedostaju, a to se otkriva
-- tek na dan slanja - dva dana pre puta.
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_gift_recipient_present;
ALTER TABLE bookings ADD CONSTRAINT bookings_gift_recipient_present
  CHECK (
    is_gift = FALSE
    OR (gift_recipient_email IS NOT NULL AND gift_recipient_email <> ''
        AND gift_recipient_name IS NOT NULL AND gift_recipient_name <> '')
  );
