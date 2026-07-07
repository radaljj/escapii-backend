-- Dodaje pol nosioca rezervacije (prvi putnik) na booking
-- Koristi se za generirani pozdrav u mejlovima (Dragi/Draga)
ALTER TABLE bookings ADD COLUMN lead_passenger_gender VARCHAR(1) NULL;
