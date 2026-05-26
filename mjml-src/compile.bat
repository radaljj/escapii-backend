@echo off
REM Kompajlira sve MJML email template-ove u HTML
REM Pokrenuti iz D:\escapii\backend\

echo Kompajliram MJML template-ove...
mjml mjml-src\upit-primljen.mjml        -o src\main\resources\email\upit-primljen.html
mjml mjml-src\potvrda-rezervacije.mjml  -o src\main\resources\email\potvrda-rezervacije.html
mjml mjml-src\otkaz-rezervacije.mjml    -o src\main\resources\email\otkaz-rezervacije.html
echo Gotovo.
