-- Sifrovan broj pasosa ("v1:" + base64) je duzi od 50 znakova.
-- Aplikacija ovo sama uradi pri startu (com.escapii.passport.PassportStorageInitializer),
-- idempotentno; fajl je tu radi evidencije i rucnog pokretanja ako startni korak ne prodje.
ALTER TABLE booking_passengers ALTER COLUMN passport_serial_number TYPE VARCHAR(255);
