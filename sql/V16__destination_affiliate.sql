-- Partnerski dodaci na reveal stranici (GetYourGuide / Airalo / Bounce).
--
-- Tri sluga su identifikatori na strani partnera i popunjavaju se skriptom iz
-- njihovih javnih sitemapa, ne rucno. Ostaju NULL dok se skript ne pusti - kod
-- to tretira kao "nema linka" i tu karticu ne prikazuje.
--
-- bounce_covered je odvojen od bounce_slug jer slug ume biti tacan a grad
-- nepokriven (Memingen i Fridrihshafen nemaju nijednu lokaciju, Kipar nije
-- pokriven uopste). Default false je namerno konzervativan: dok skript ne
-- potvrdi pokrivenost, kartica se ne prikazuje.

ALTER TABLE destinations ADD COLUMN IF NOT EXISTS gyg_slug       VARCHAR(120);
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS airalo_slug    VARCHAR(120);
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS bounce_slug    VARCHAR(120);
ALTER TABLE destinations ADD COLUMN IF NOT EXISTS bounce_covered BOOLEAN NOT NULL DEFAULT FALSE;
