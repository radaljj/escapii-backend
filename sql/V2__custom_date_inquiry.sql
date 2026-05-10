-- ============================================================
-- Escapii — Custom Date Inquiry Feature
-- Pokreni ručno na produkcijskoj PostgreSQL bazi (Render)
-- ============================================================

-- 1. Dodaj polja za privatni termin u available_dates
ALTER TABLE available_dates
    ADD COLUMN IF NOT EXISTS is_private     BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS private_token  VARCHAR(64)   UNIQUE,
    ADD COLUMN IF NOT EXISTS expires_at     TIMESTAMP;

-- 2. Kreiraj tabelu za custom-date upite
CREATE TABLE IF NOT EXISTS custom_date_inquiries (
    id                      BIGSERIAL       PRIMARY KEY,
    airport                 VARCHAR(10)     NOT NULL,
    travelers               INTEGER         NOT NULL,
    desired_departure_date  DATE            NOT NULL,
    nights                  INTEGER         NOT NULL,
    email                   VARCHAR(200)    NOT NULL,
    notes                   TEXT,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indeksi za brzo filtriranje
CREATE INDEX IF NOT EXISTS idx_inquiries_status      ON custom_date_inquiries (status);
CREATE INDEX IF NOT EXISTS idx_inquiries_created_at  ON custom_date_inquiries (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dates_private_token   ON available_dates (private_token) WHERE private_token IS NOT NULL;
