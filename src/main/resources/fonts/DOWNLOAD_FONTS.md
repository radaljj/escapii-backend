# Fontovi za PDF (vaučeri i profaktura)

openhtmltopdf renderuje SAMO fontove registrovane u kodu - sistemski Georgia/Arial
ne postoje u PDF-u. Svi fajlovi ovde su statični TTF, licenca SIL OFL 1.1
(dozvoljeno ugrađivanje i komercijalna upotreba).

| Fajl | Font | CSS familija (kod) | Koristi |
|------|------|--------------------|---------|
| `CormorantGaramond-Regular.ttf`    | Cormorant Garamond 400        | `VoucherSerif` | vaučeri |
| `CormorantGaramond-Italic.ttf`     | Cormorant Garamond 400 Italic | `VoucherSerif` | vaučeri |
| `CormorantGaramond-Bold.ttf`       | Cormorant Garamond 700        | `VoucherSerif` | vaučeri |
| `CormorantGaramond-BoldItalic.ttf` | Cormorant Garamond 700 Italic | `VoucherSerif` | vaučeri |
| `Manrope-Regular.ttf`              | Manrope 400                   | `VoucherSans`  | vaučeri |
| `Manrope-SemiBold.ttf`             | Manrope 600                   | `VoucherSans`  | vaučeri |
| `Manrope-ExtraBold.ttf`            | Manrope 800                   | `VoucherSans`  | vaučeri |
| `JetBrainsMono-Bold.ttf`           | JetBrains Mono 700            | `VoucherMono`  | vaučeri (kod, datumi) |
| `Inter-Regular.ttf`                | Inter 400                     | `InvoiceSans`  | profaktura |
| `Inter-Bold.ttf`                   | Inter 700                     | `InvoiceSans`  | profaktura |

Vaučeri koriste isti trio kao stranica /hvala (boarding pass na sajtu):
Cormorant Garamond za naslove i velike brojeve, Manrope za tekst, JetBrains Mono
za kodove. Preuzeti sa Google Fonts kao statične instance sa `latin-ext`
podskupom (š, đ, č, ć, ž su provereni), 2026-09-09.

Skidanje (statični TTF, ne varijabilni - PDFBox 2.x varijabilne fontove
ugrađuje samo u podrazumevanoj težini):
`curl "https://fonts.googleapis.com/css?family=Manrope:400,600,800&subset=latin,latin-ext"`
vraća .ttf linkove kad se pozove bez browser User-Agent-a.
