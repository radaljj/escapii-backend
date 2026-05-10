-- ============================================================
-- Escapii — Inquiry price field
-- Pokreni ručno na produkcijskoj PostgreSQL bazi (Render)
-- ============================================================

ALTER TABLE custom_date_inquiries
    ADD COLUMN IF NOT EXISTS price DECIMAL(10, 2);
