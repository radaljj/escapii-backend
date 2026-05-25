-- ============================================================
-- Escapii — Dodaj 4. isključenu destinaciju na bookings tabelu
-- Pokreni ručno na produkcijskoj PostgreSQL bazi (Render)
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS excluded_dest4_id BIGINT REFERENCES destinations(id);
