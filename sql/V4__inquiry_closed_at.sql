-- ============================================================
-- Escapii — Inquiry closedAt field
-- Pokreni ručno na produkcijskoj PostgreSQL bazi (Render)
-- ============================================================

ALTER TABLE custom_date_inquiries
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
