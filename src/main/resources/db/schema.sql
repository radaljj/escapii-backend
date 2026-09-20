-- Idempotentne naredbe koje SchemaBootstrap izvršava pri SVAKOM startu, pre nego što
-- server primi prvi zahtev. Produkcija ima DDL_AUTO=none, pa je ovo jedini put da
-- nova tabela ili kolona stigne u bazu bez ručnog SQL-a pre deploya.
-- Pravila: samo CREATE/ALTER ... IF [NOT] EXISTS, bez DO blokova (deli se po tacka-zarez),
-- naredba koja pukne se loguje i beleži kao AppError, ostale se izvršavaju.

-- Zbirne fakture agencijama (2026-09)
CREATE TABLE IF NOT EXISTS agency_invoices (
    id             BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(25)   NOT NULL UNIQUE,
    agency_id      BIGINT        NOT NULL,
    agency_name    VARCHAR(100)  NOT NULL,
    agency_email   VARCHAR(200),
    description    VARCHAR(500)  NOT NULL,
    period_from    DATE          NOT NULL,
    period_to      DATE          NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    booking_count  INTEGER       NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    issued_at      DATE          NOT NULL,
    due_date       DATE          NOT NULL,
    sent_at        TIMESTAMP,
    paid_at        TIMESTAMP,
    voided_at      TIMESTAMP,
    void_reason    VARCHAR(255),
    pdf            BYTEA,
    created_at     TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agency_invoices_agency ON agency_invoices (agency_id);

ALTER TABLE IF EXISTS bookings ADD COLUMN IF NOT EXISTS agency_invoice_id BIGINT;

-- Zastarela CHECK ogranicenja koja je Hibernate (ddl-auto=update) napravio nad enum kolonama
-- pre nego sto su enumi dobili nove vrednosti (BookingStatus.COMPLETED od 2026-05-20,
-- VoucherStatus.RESERVED). Enum vrednosti proverava aplikacija; baza ne sme da ih zamrzne.
-- Lokalno je bas bookings_status_check srusio prelazak u COMPLETED (E2E 2026-09-17).
ALTER TABLE IF EXISTS bookings DROP CONSTRAINT IF EXISTS bookings_status_check;
ALTER TABLE IF EXISTS bookings DROP CONSTRAINT IF EXISTS bookings_old_status_check;
ALTER TABLE IF EXISTS bookings DROP CONSTRAINT IF EXISTS bookings_accommodation_type_check;
ALTER TABLE IF EXISTS gift_vouchers DROP CONSTRAINT IF EXISTS gift_vouchers_status_check;
ALTER TABLE IF EXISTS custom_date_inquiries DROP CONSTRAINT IF EXISTS custom_date_inquiries_status_check;
ALTER TABLE IF EXISTS gift_trip_inquiries DROP CONSTRAINT IF EXISTS gift_trip_inquiries_status_check;
ALTER TABLE IF EXISTS booking_financial_items DROP CONSTRAINT IF EXISTS booking_financial_items_item_type_check;
ALTER TABLE IF EXISTS booking_financial_items DROP CONSTRAINT IF EXISTS booking_financial_items_allocation_type_check;

-- Jutarnji krug (2026-09-17): slanja idu na svakih 30 min od 10 do 22h (idempotentno),
-- a dnevni koraci (digest, zavrsavanje, ciscenje) se prijave ovde da idu tacno jednom dnevno
-- i to na prvom krugu posle 10h koji je server docekao
CREATE TABLE IF NOT EXISTS scheduler_runs (
    job           VARCHAR(40) PRIMARY KEY,
    last_run_date DATE,
    last_run_at   TIMESTAMP
);
ALTER TABLE IF EXISTS scheduler_runs ADD COLUMN IF NOT EXISTS last_run_at TIMESTAMP;

-- Koordinate destinacija za prognozu: jednom geokodirano, jutarnji krug ne zavisi od geokodera
CREATE TABLE IF NOT EXISTS geo_cache (
    city_query  VARCHAR(200)     PRIMARY KEY,
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL,
    source      VARCHAR(20),
    resolved_at TIMESTAMP        NOT NULL
);

-- Promo "besplatno iskljucivanje destinacija" (2026-09-20): podesavanja koja admin menja iz panela
-- bez deploya (kod, datum isteka, prekidac) - vidi ExclusionPromo
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(80)  PRIMARY KEY,
    setting_value VARCHAR(500),
    updated_at    TIMESTAMP
);
ALTER TABLE IF EXISTS bookings ADD COLUMN IF NOT EXISTS promo_code VARCHAR(40);
ALTER TABLE IF EXISTS bookings ADD COLUMN IF NOT EXISTS promo_saved_eur INTEGER;
