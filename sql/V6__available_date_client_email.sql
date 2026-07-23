-- Email klijenta iz upita na osnovu kog je privatni termin napravljen.
-- Bez ovoga admin nije znao kome da pošalje privatni link - veza ka upitu
-- se gubila jer se zatvoreni upiti brišu.
ALTER TABLE available_date ADD COLUMN IF NOT EXISTS client_email VARCHAR(200);
