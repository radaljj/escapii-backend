-- Faza 3 cleanup: legacy jedinstveni agency_cost na bookingu vise ne postoji.
-- Novi obracun ide per-stavku kroz booking_financial_items.agency_cost (posebna
-- tabela, ne dira se). Kolona se drop-uje idempotentno.
ALTER TABLE bookings DROP COLUMN IF EXISTS agency_cost;
