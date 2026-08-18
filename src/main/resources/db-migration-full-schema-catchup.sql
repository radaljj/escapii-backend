-- ============================================================
-- Catchup migracija: sve kolone dodate od inicijalnog deploymenta.
-- Sve komande su idempotentne (IF NOT EXISTS) - bezbedan ponovljeni run.
-- Pokreni JEDNOM na produkcijskoj bazi pre restarta backenda.
-- ============================================================

-- ── bookings ────────────────────────────────────────────────

-- Vaučer podrška
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS applied_voucher_code VARCHAR(20);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS voucher_discount      INTEGER;

-- Admin interna napomena
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS admin_notes TEXT;

-- Prihvatanje presedanja (korisnički izabran flag)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS has_connecting_flights BOOLEAN NOT NULL DEFAULT FALSE;

-- Praćenje prethodnog statusa (za CANCELLED → CONFIRMED flow i vaučer re-lock)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS old_status VARCHAR(20);

-- Precizniji naziv za vremensku prognozu (admin popunjava ako assignedDestination nije dovoljno precizan)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS weather_city VARCHAR(200);

-- Reveal Box feature
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS has_reveal_box    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_address  VARCHAR(300);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_city     VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_phone    VARCHAR(50);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS delivery_apartment VARCHAR(150);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS reveal_box_sent   BOOLEAN NOT NULL DEFAULT FALSE;

-- Zvanični dokument rezervacije (PDF od agencije)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document           BYTEA;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document_filename  VARCHAR(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_document_uploaded_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS confirmation_sent_at            TIMESTAMP;

-- ── available_dates ──────────────────────────────────────────

-- Email klijenta čiji je upit doveo do privatnog termina
ALTER TABLE available_dates ADD COLUMN IF NOT EXISTS client_email VARCHAR(200);

-- ── gift_vouchers ────────────────────────────────────────────

-- Tabela se kreira ako ne postoji (cela gift voucher funkcionalnost)
CREATE TABLE IF NOT EXISTS gift_vouchers (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(20)    NOT NULL UNIQUE,
    amount         NUMERIC(10,2)  NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    buyer_email    VARCHAR(200)   NOT NULL,
    buyer_name     VARCHAR(200),
    gift_message   VARCHAR(500),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    activated_at   TIMESTAMP,
    expires_at     TIMESTAMP,
    used_at        TIMESTAMP,
    invoice_number VARCHAR(25)    UNIQUE,
    invoice_sent_at TIMESTAMP,
    used_in_booking_ref BIGINT,
    used_amount    NUMERIC(10,2)  NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_gift_voucher_code ON gift_vouchers(code);

-- Kolone dodate u gift_vouchers ako tabela već postoji
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS invoice_number       VARCHAR(25) UNIQUE;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS invoice_sent_at      TIMESTAMP;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS used_in_booking_ref  BIGINT;
ALTER TABLE gift_vouchers ADD COLUMN IF NOT EXISTS used_amount          NUMERIC(10,2) NOT NULL DEFAULT 0;

-- ── invoice_sequence ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS invoice_sequence (
    id               BIGSERIAL PRIMARY KEY,
    year             INTEGER NOT NULL UNIQUE,
    last_sequence_nr INTEGER NOT NULL DEFAULT 0
);

-- ── launch_subscribers ───────────────────────────────────────

CREATE TABLE IF NOT EXISTS launch_subscribers (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ── waitlist_entries ─────────────────────────────────────────

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id               BIGSERIAL PRIMARY KEY,
    email            VARCHAR(200) NOT NULL,
    departure_airport VARCHAR(10) NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    notified_at      TIMESTAMP,
    UNIQUE(email, departure_airport)
);

-- ── term_destination ─────────────────────────────────────────
-- (tabela postoji, ovo je sigurnosna mreža ako prethodna migracija nije pokrenuta)

CREATE TABLE IF NOT EXISTS term_destination (
    id           BIGSERIAL PRIMARY KEY,
    date_id      BIGINT NOT NULL REFERENCES available_dates(id),
    destination_id BIGINT NOT NULL REFERENCES destinations(id),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    connecting   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE(date_id, destination_id)
);
ALTER TABLE term_destination ADD COLUMN IF NOT EXISTS connecting BOOLEAN NOT NULL DEFAULT FALSE;
