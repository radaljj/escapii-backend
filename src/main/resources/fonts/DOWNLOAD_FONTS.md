# Fontovi za PDF vaučer

Ovaj direktorijum mora da sadrži sledeće TTF fajlove pre deploy-a:

| Fajl | Font | Preuzmi sa |
|------|------|------------|
| `PlayfairDisplay-Regular.ttf` | Playfair Display 400 | https://fonts.google.com/specimen/Playfair+Display |
| `PlayfairDisplay-Bold.ttf` | Playfair Display 700 | https://fonts.google.com/specimen/Playfair+Display |
| `PlayfairDisplay-Italic.ttf` | Playfair Display 400 Italic | https://fonts.google.com/specimen/Playfair+Display |
| `Inter-Regular.ttf` | Inter 400 | https://fonts.google.com/specimen/Inter |
| `Inter-Bold.ttf` | Inter 700 | https://fonts.google.com/specimen/Inter |

Fajlove postavi direktno u ovaj direktorijum (`src/main/resources/fonts/`).

openhtmltopdf renderuje SAMO eksplicitno registrovane fontove — sistemski Georgia/Arial ne rade u PDF-u.
