ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_cost_snapshot INTEGER;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_id_snapshot BIGINT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_name_snapshot VARCHAR(100);
