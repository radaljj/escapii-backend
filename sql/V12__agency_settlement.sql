-- Faza 1/2: obracun sa agencijom (settlement + snapshot + agencijska sekvenca fakture)

-- 1) Nove kolone na bookings
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS settlement_status VARCHAR(32) NOT NULL DEFAULT 'NEEDS_COSTS';
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_invoice_number VARCHAR(25);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_invoiced_at TIMESTAMP;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS agency_paid_at TIMESTAMP;

-- Unique constraint na broj fakture (agencijska sekvenca ne sme dublirati)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_bookings_agency_invoice_number'
    ) THEN
        ALTER TABLE bookings
            ADD CONSTRAINT uk_bookings_agency_invoice_number UNIQUE (agency_invoice_number);
    END IF;
END$$;

-- 2) Snapshot stavki
CREATE TABLE IF NOT EXISTS booking_financial_items (
    id                    BIGSERIAL PRIMARY KEY,
    booking_id            BIGINT       NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    item_type             VARCHAR(32)  NOT NULL,
    allocation_type       VARCHAR(32)  NOT NULL,
    description           VARCHAR(255),
    quantity              INTEGER      NOT NULL DEFAULT 1,
    unit_customer_price   NUMERIC(12,2) NOT NULL DEFAULT 0,
    customer_total        NUMERIC(12,2) NOT NULL DEFAULT 0,
    agency_cost           NUMERIC(12,2),
    flight_agency_cost    NUMERIC(12,2),
    hotel_agency_cost     NUMERIC(12,2),
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bfi_booking ON booking_financial_items(booking_id);
CREATE INDEX IF NOT EXISTS idx_bfi_type    ON booking_financial_items(item_type);

-- 3) Agencijska sekvenca fakture (ESC-AG-YYYY-NNNN)
CREATE TABLE IF NOT EXISTS agency_invoice_sequences (
    year     INTEGER PRIMARY KEY,
    last_seq INTEGER NOT NULL DEFAULT 0
);
