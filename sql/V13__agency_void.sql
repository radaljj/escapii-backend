-- Grupa 3: VOIDED status za storno fakture. Broj fakture ostaje na bookingu
-- (audit), ali je oznaceno kao ponisteno.
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_voided_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_void_reason VARCHAR(255);
