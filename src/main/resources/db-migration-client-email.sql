-- Migration: client_email na available_dates
-- Pokrenuti JEDNOM pre restarta backenda sa novim kodom.
-- Čuva email klijenta čiji je upit doveo do privatnog termina,
-- da admin zna kome da pošalje privatni link (vidi AvailableDate.clientEmail).

ALTER TABLE available_dates
    ADD COLUMN IF NOT EXISTS client_email VARCHAR(200);
