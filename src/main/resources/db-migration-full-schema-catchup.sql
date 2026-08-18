-- ============================================================
-- Catchup migracija: kolone dodate od inicijalnog deploymenta.
-- Sve komande su idempotentne (IF NOT EXISTS) - bezbedan ponovljeni run.
-- Tabele (gift_vouchers, invoice_sequences, launch_subscribers, waitlist)
-- već postoje u produkciji - ovde se samo dodaju eventualno nedostajuće kolone.
-- ============================================================

-- ── bookings ────────────────────────────────────────────────

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS applied_voucher_code VARCHAR(20);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS voucher_discount      INTEGER;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS admin_notes           TEXT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS has_connecting_flights BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS old_status            VARCHAR(20);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS weather_city          VARCHAR(200);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS has_reveal_box        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_address      VARCHAR(300);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_city         VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_phone        VARCHAR(50);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_apartment    VARCHAR(150);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS reveal_box_sent       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document               BYTEA;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document_filename      VARCHAR(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document_uploaded_at   TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_sent_at                TIMESTAMP;

-- ── available_dates ──────────────────────────────────────────

ALTER TABLE available_dates ADD COLUMN IF NOT EXISTS client_email VARCHAR(200);

-- ── gift_vouchers ────────────────────────────────────────────

ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS invoice_number       VARCHAR(25) UNIQUE;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS invoice_sent_at      TIMESTAMP;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS used_in_booking_ref  BIGINT;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS used_amount          NUMERIC(10,2) NOT NULL DEFAULT 0;

-- ── term_destination ─────────────────────────────────────────

ALTER TABLE term_destination ADD COLUMN IF NOT EXISTS connecting BOOLEAN NOT NULL DEFAULT FALSE;
