-- Turisticke agencije koje organizuju termine za Escapii
-- (kupuju karte i smestaj u paketu).
-- available_dates dobija opcionu vezu ka agenciji.

CREATE TABLE IF NOT EXISTS agencies (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    contact_name  VARCHAR(100),
    contact_email VARCHAR(200),
    contact_phone VARCHAR(50),
    notes         VARCHAR(1000),
    active        BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE available_dates
    ADD COLUMN IF NOT EXISTS agency_id BIGINT REFERENCES agencies(id) ON DELETE SET NULL;
