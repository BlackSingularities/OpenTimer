# Historia zmian

## 1.0.4

- Płynne przewijanie list w zakładkach Zegar, Stoper i Minutnik – tyknięcie zegara nie przerysowuje już całej listy, więc szybkie przeciągnięcie znów leci z bezwładnością.
- Zakładki bez dużego tytułu – nazwę pokazuje dolny pasek.
- Zegar: nowy styl „Wyświetlacz LCD”; dotknięcie zegara przełącza cyfrowy → analogowy → LCD.
- Stoper: dotknięcie tarczy przełącza pierścień → tarcza analogowa (z małą tarczą minut) → LCD.
- Szybkie budziki: 15 min, 30 min, 1 h i 2 h.
- Minutnik: nowe szybkie czasy 1 h, 1 h 30 min i 2 h.
- Metronom: pojedyncze kliknięcia zamiast podwójnego piknięcia (przy 1/4 brzmiało jak dwa uderzenia) i równe tempo bez zwalniania.
- Zakładka Więcej bez zegara nocnego i zegarów świata – są w zakładce Zegar.

## 1.0.3

- Poprawka: przywrócone ciemne tło interfejsu – jasne tło z 1.0.2 sprawiało, że biały tekst był nieczytelny.
- Ekran uruchamiania z logo na ciemnym tle.

## 1.0.2

- Nowa profesjonalna ikona aplikacji i dopasowane kolorystycznie jasne tło.
- Spójne ikony adaptacyjne, monochromatyczne i ekran uruchamiania.

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
