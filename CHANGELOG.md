# Historia zmian

## 1.0.1

- Poprawka: na Androidzie od 10 do 13 zegary świata i odliczanie do wydarzeń wywoływały metodę
  dostępną dopiero od Androida 14, co kończyło się zamknięciem aplikacji. Obliczenia dat liczą się
  teraz tak samo na każdej wersji systemu.
- Minutnik: ustawienie „Pełny ekran po czasie” naprawdę działa – wyłączone zostawia samo
  powiadomienie z przyciskami i nie budzi ekranu.
- Nowe: zegar domowy. W podróży, gdy strefa telefonu różni się od domowej, zakładka Zegar pokazuje
  godzinę w domu; strefę domową można zmienić w ustawieniach.
- Odbiornik rozruchu reaguje tylko na znane rozgłoszenia systemowe, a analiza statyczna (lint)
  jest teraz częścią wydania, więc takie pułapki wychodzą przed publikacją.

## 1.0.0

Pierwsze wydanie OpenTimera.

- Zegar cyfrowy i analogowy, data, najbliższy budzik i zegary świata z podziałem na dzień i noc.
- Budziki: powtarzanie po dniach tygodnia, własny dźwięk lub plik audio, narastanie głośności, wibracja, drzemka z limitem, pominięcie najbliższego dzwonienia, zapowiedź przed budzikiem oraz zadanie (działania lub przepisanie kodu) przed wyłączeniem.
- Stoper z okrążeniami: odcinki, czasy łączne, wyróżnienie najszybszego i najwolniejszego okrążenia, kopiowanie wyników.
- Minutniki: dowolna liczba naraz, klawiatura czasu, nazwy, zapisane szybkie czasy, „+1 min” i pełny ekran po czasie.
- Pomodoro z historią sesji, trening interwałowy (Tabata, HIIT, własne plany) z zapowiedziami i piknięciami, metronom, odliczanie do wydarzeń oraz kalkulator czasu i dat.
- Zegar nocny: przyciemnienie, kolor i powolne przesuwanie treści chroniące ekran OLED.
- Widżet na ekran główny, skróty aplikacji i obsługa poleceń systemowych (ustaw budzik, ustaw minutnik).
- Polski i angielski, format 12/24 h, aktualizacje pobierane i instalowane przez samą aplikację.
