-- V19 — poruka na vaučeru poklonjenog putovanja.
--
-- Kupac je pri rezervaciji poklona može upisati (opciono, do 200 znakova); ide na
-- PDF vaučer u prilogu potvrde i na /poklon stranicu. NULL = bez poruke, blok se
-- ne prikazuje. Nema backfill-a: podatak ranije nije postojao.
--
-- Nullable namerno, da se doda bez obzira na postojeće redove. Kod koji je čita
-- stiže sa deployom ODMAH POSLE ove izmene - zato ovaj SQL ide PRE deploya, inače
-- svaki upit nad rezervacijama pada sa "column gift_message does not exist".

ALTER TABLE bookings ADD COLUMN IF NOT EXISTS gift_message VARCHAR(200);
