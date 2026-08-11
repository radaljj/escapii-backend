-- Migration: connecting flag na term_destination
-- Pokrenuti JEDNOM pre restarta backenda sa novim kodom.
-- Označava destinacije koje za dati termin idu sa presedanjem.

ALTER TABLE term_destination
    ADD COLUMN IF NOT EXISTS connecting BOOLEAN NOT NULL DEFAULT FALSE;
