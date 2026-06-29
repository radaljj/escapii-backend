-- Migration: term_destination tabela sa per-termin active flagom
-- Pokrenuti JEDNOM pre restarta backenda sa novim kodom

CREATE TABLE IF NOT EXISTS term_destination (
    id               BIGSERIAL    PRIMARY KEY,
    available_date_id BIGINT      NOT NULL REFERENCES available_dates(id) ON DELETE CASCADE,
    destination_id    BIGINT      NOT NULL REFERENCES destinations(id),
    active            BOOLEAN     NOT NULL DEFAULT true,
    CONSTRAINT uq_term_dest UNIQUE (available_date_id, destination_id)
);

-- Migracija postojecih podataka iz stare join tabele
INSERT INTO term_destination (available_date_id, destination_id, active)
SELECT available_date_id, destination_id, true
FROM available_date_destinations
ON CONFLICT (available_date_id, destination_id) DO NOTHING;

-- Verifikacija
SELECT COUNT(*) AS migrirano FROM term_destination;
