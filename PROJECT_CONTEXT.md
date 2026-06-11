# Escapii — Projektni kontekst (za novi Claude chat)

> Zalepi ceo ovaj fajl u novi chat da Claude odmah zna stack, flow, git i gde se šta nalazi.

---

## 1. Šta je Escapii

Platforma za **putovanja iznenađenja** — korisnik bira aerodrom, datum, broj putnika, budžet i dodatke; **destinacija ostaje tajna do 48h pre polaska**. Sajt je na srpskom + engleskom (i18n). Postoje i: poklon vaučeri (PDF), privatni termini, blog.

---

## 2. Arhitektura i stack

| Sloj | Tehnologija | Lokacija (lokalno) | Git remote | Deploy |
|------|-------------|--------------------|------------|--------|
| **Frontend** | WordPress tema `escapii-theme` (PHP template-i, sav JS inline u `front-page.php`) | `C:\Users\user\Local Sites\escapii\app\public\wp-content\themes\escapii-theme` | `github.com/radaljj/escapii-frontend.git` | auto CI/CD na push `main` |
| **Backend** | Java **Spring Boot**, package `com.escapii`, **PostgreSQL** | `D:\escapii\backend` | `github.com/radaljj/escapii-backend.git` | Render (`escapii-backend.onrender.com`), auto na push `main` |

- Frontend zove backend preko `escapii_api_url()` u `functions.php` (lokalno `http://localhost:8080`, produkcija `https://escapii-backend.onrender.com`).
- Domen: **escapii.rs**.
- **Git workflow:** uvek `git add` → `git commit` → `git push origin main`. Commit poruke završavaju sa `Co-Authored-By: Claude ...`. Push = automatski deploy.

---

## 3. Backend — gde se šta nalazi (`D:\escapii\backend\src\main\java\com\escapii\`)

### Booking (rezervacija) — glavni flow
- `model/Booking.java` — entitet rezervacije (svi podaci + reveal box polja: `hasRevealBox`, `deliveryAddress/City/Phone`, `revealBoxSent`).
- `dto/BookingRequest.java` — telo POST /api/booking (validacije, anti-bot honeypot+timing).
- `service/impl/BookingServiceImpl.java` — kreiranje rezervacije, validacija, primena vaučera, anti-bot, reveal box validacija.
- `controller/BookingController.java` — `/api/booking`, `/api/booking/price-preview`, `/api/booking/status`.
- `dto/BookingResponse.java`, `dto/BookingStatusResponse.java`, `dto/PricePreviewResponse.java`.

### Cenovnik
- `service/PriceCalculator.java` + `service/impl/PriceCalculatorImpl.java` — sva logika cena.
  - Konstante: `SUPERIOR_PP=100`, `CABIN_SUITCASE=100` (50€×2 smera), `INSURANCE_PP=12`, `BREAKFAST_PP=20`/noć, `SEATS_PP=24`, `EXCLUSION_PP=15`, `SOLO_SURCHARGE=60`, `REVEAL_BOX_FLAT=25`.
  - **Isključivanja:** BEG/ostali → max 4, 1. besplatno, 2–4. po 15€/os. **INI → max 1, nema besplatnog, 15€/os** (`calcExclusionCostINI`, grananje po `departureAirport`).

### Termini i destinacije
- `model/AvailableDate.java`, `repository/AvailableDateRepository.java`, `service/impl/AvailableDateServiceImpl.java`, `controller/DateController.java`.
- `model/Destination.java`, `service/impl/DestinationServiceImpl.java` (countries lista je static `List.of(...)`), `controller/DestinationController.java`.

### Reveal / Forecast / Scheduler
- `service/impl/BookingSchedulingServiceImpl.java` — šalje reveal (T‑2), forecast (T‑7..T‑4), auto-cancel stale PENDING, auto-complete. **Preskače auto-reveal ako `hasRevealBox=true`.**
- `config/DailyTaskScheduler.java` — cron `0 0 10 * * *` Europe/Belgrade; okida reveals/forecasts/cleanup + digest.
- `service/impl/RevealServiceImpl.java` + `controller/RevealController.java` — `/api/reveal` (magic-link token), scratch potvrda.
- `service/weather/WeatherServiceImpl.java` — Nominatim geocoding + Open-Meteo (samo u scheduled jobovima).

### Email
- `service/email/impl/BookingEmailServiceImpl.java` — tim + kupac mejlovi; **price table prikazuje vaučer kod + popust (precrtan međuzbir) i Reveal Box (+25€)**. Boarding-pass HTML blok.
- `service/email/impl/DigestEmailServiceImpl.java` — jutarnji ops digest; ima **sekciju „📦 Pošalji Reveal Box"** (polazak ≤5 dana, neposlat).
- Ostali: `RevealEmailServiceImpl`, `ForecastEmailServiceImpl`, `InquiryEmailServiceImpl`, `WaitlistEmailServiceImpl`, `GiftVoucherEmailServiceImpl`.
- HTML template-i: `resources/email/*.html` (`upit-primljen.html`, `potvrda-rezervacije.html`, `otkaz-rezervacije.html`).
- **Email se šalje preko Gmail SMTP** (`escapii.team@gmail.com`) — limit 500/dan (rizik za kampanju).

### Gift vaučeri
- `model/GiftVoucher.java` (indeks na `code`), `repository/GiftVoucherRepository.java`, `controller/GiftVoucherController.java` — `/api/gifts/vouchers`, `/validate`, `/reveal`.
- `service/voucher/VoucherPdfService.java` — generiše PDF (openhtmltopdf) iz `resources/templates/gift-voucher.html`; **`Semaphore(3)` ograničava paralelne PDF-ove**.
- `model/VoucherStatus.java` (ACTIVE → RESERVED → USED).

### Admin
- `controller/AdminController.java` — sve `/api/admin/**` (status, notes, destination, weather-city, airline-name/code, send-reveal, send-forecast, **reveal-box-sent**).
- `service/impl/AdminServiceImpl.java`, `dto/AdminBookingResponse.java`, `mapper/AdminBookingMapper.java`.
- `config/AdminKeyFilter.java` — auth preko `X-Admin-Key` header.

### Infrastruktura / config
- `config/RateLimitingFilter.java` — **in-memory per-IP rate limiting** po endpointu (booking 5/h, itd.).
- `config/AsyncConfig.java` — `taskExecutor` (5–10 threadova, queue 100) + `pdfExecutor` (3) za PDF.
- `config/SecurityConfig.java`, `config/GlobalExceptionHandler.java`, `config/IpUtils.java`.
- `service/impl/AppErrorServiceImpl.java` + `controller/AppErrorController.java` — log grešaka, vidljiv u admin „🚨 Greške".
- `resources/application.properties` — sve preko env varijabli; `ddl-auto=validate` (**shema se ne menja automatski — ALTER TABLE ide ručno na bazi**).

---

## 4. Frontend — gde se šta nalazi (`...\themes\escapii-theme\`)

| Fajl | URL | Sadržaj |
|------|-----|---------|
| `front-page.php` | `/` | **Glavni booking flow (8 koraka)**, sav JS inline, i18n SR/EN, cenovnik, gift modal, reveal box modal, DOB picker (3 selecta dan/mesec/godina + nativni `<input type=date>` na mobilnom). Najveći fajl. |
| `page-admin-panel.php` | `/admin-panel` | Admin dashboard (rezervacije, termini, vaučeri, greške). Reveal box sekcija renderuje se samo ako `hasRevealBox`. |
| `page-pokloni.php` | `/pokloni-putovanje-iznenadjenja` | Kupovina poklon vaučera. |
| `page-poklon.php` | `/poklon` | Aktivacija/redeem vaučera. |
| `page-blog.php` | `/blog` | Blog lista (featured + grid, kategorije, paginacija, animirani empty state). |
| `single.php` | `/blog/{post}` | Editorial prikaz posta (hero, progress bar, share, related, mobile share bar). |
| `page-hvala.php` | `/hvala` | Thank-you + boarding pass iz sessionStorage. |
| `page-otkrivanje.php` | `/otkrivanje` | Reveal stranica (scratch kartica). |
| `page-politika-privatnosti.php` / `page-privacy-policy.php` | privacy SR/EN | |
| `functions.php` | — | Theme setup, **robots.txt override**, **sitemap.xml**, auto-kreiranje stranica, favicon/OG meta, `escapii_api_url()`. |

- Booking koraci (front-page.php): 1 aerodrom → 2 putnici → 3 termin → 4 smeštaj → 5 dodaci (osiguranje, doručak, sedišta, **Reveal Box +25€**, presedanje) → 6 isključivanja → 7 putnici (ime, pol, DOB, viza, pasoš) → 8 kontakt + pregled.
- State objekat `S` drži ceo izbor; `submitBooking()` šalje POST. i18n preko `t('key')`, rečnici SR/EN inline.

---

## 5. Ključni API endpointi

| Method | Endpoint | Svrha |
|--------|----------|-------|
| POST | `/api/booking` | Kreira rezervaciju |
| GET | `/api/booking/price-preview` | Živi obračun cene |
| GET | `/api/booking/status` | Provera statusa (ref + prezime) |
| GET | `/api/dates`, `/api/dates/private` | Termini |
| GET | `/api/destinations/**` | Destinacije, countries |
| POST | `/api/waitlist` | Lista čekanja |
| GET/POST | `/api/reveal`, `/api/reveal/confirm` | Reveal magic-link |
| POST | `/api/inquiries/custom-date` | Upit za privatni termin |
| POST | `/api/gifts/vouchers`, `/validate`, `/reveal` | Vaučeri |
| * | `/api/admin/**` | Admin (header `X-Admin-Key`) |

Svi endpointi imaju rate-limit u `RateLimitingFilter.java`.

---

## 6. Reveal Box (fizička kutija, +25€) — kompletan tok

1. **Frontend** (front-page.php, korak 5): kartica „📦 Reveal Box"; klik otvara modal za adresu (ulica, grad, telefon — sva 3 obavezna). Tag „📦 Reveal Box" u koraku 8.
2. **Backend**: `BookingServiceImpl` validira adresu ako `hasRevealBox`; `PriceCalculator` dodaje 25€ flat.
3. **Scheduler**: `BookingSchedulingServiceImpl.sendReveals()` **preskače** auto-reveal mejl ako `hasRevealBox`.
4. **Digest**: `DigestEmailServiceImpl` prikazuje „📦 Pošalji Reveal Box" za polaske ≤5 dana koji nisu poslati (`BookingRepository.findPendingRevealBoxes`).
5. **Admin**: `page-admin-panel.php` prikazuje adresu + dugme „Označi kao poslan" → `POST /api/admin/bookings/{id}/reveal-box-sent` → `AdminServiceImpl.markRevealBoxSent`.

---

## 7. Deploy & operativa

- **Push = deploy.** Frontend i backend su odvojeni repoi; commituj u odgovarajući.
- **DB migracije ručno:** `ddl-auto=validate`, pa nove kolone ideš `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...` direktno na Postgresu pre/posle deploya.
- Render restart pri deployu = ~30–60s downtime na backendu.

---

## 8. Poznata ograničenja / skaliranje (pre kampanje 1000–5000)

- 🔴 **Email**: Gmail SMTP limit 500/dan → preći na SES/SendGrid.
- 🔴 **Nema caching-a** (`@Cacheable` nigde) → dodati Caffeine cache na `/api/destinations`, `/api/dates`.
- 🟡 **Hikari pool default 10**; deli Postgres sa WordPress-om → podići pool, dodati indekse (`bookings(status, created_at)`, `available_dates(departure_date, active)`).
- 🟡 **Rate limiting in-memory** → puca ako se skalira na >1 instancu.
- 🟡 **15 eksternih CDN skripti** u front-page.php → staviti Cloudflare ispred sajta.
- 🟢 Async (taskExecutor + pdfExecutor + PDF semafor) je dobro odrađen.
- Preporuka: pre kampanje uraditi load test (k6, ~300 vir. korisnika na booking flow).

---

## 9. Konvencije za rad

- Frontend: SR je primarni jezik, sve novo dodati i u EN i18n rečnik.
- Commit poruke jasne, sa `Co-Authored-By: Claude ...`.
- Backend kod komentarisan na srpskom.
- Pri izmeni cene/logike: ažurirati **i** `PriceCalculatorImpl` (backend) **i** prikaz u `front-page.php` (frontend) **i** email price table (`BookingEmailServiceImpl`).
