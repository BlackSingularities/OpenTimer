# OpenTimer

Zegar, budzik, stoper, minutnik i narzędzia czasu w jednej aplikacji na Androida. Czarny interfejs
AMOLED, bez reklam, bez śledzenia i bez konta – wszystko działa w telefonie.

| Zakładka | Co potrafi |
|---|---|
| Zegar | zegar cyfrowy i analogowy z płynną sekundą, data, najbliższy budzik, zegary świata z różnicą czasu i dniem na miejscu |
| Budzik | powtarzanie po dniach tygodnia, własny dzwonek lub plik audio, narastanie głośności, wibracja, drzemka z limitem, pominięcie najbliższego dzwonienia, zapowiedź przed budzikiem, zadanie przed wyłączeniem (działania albo przepisanie kodu) |
| Stoper | okrążenia z czasem odcinka i czasem łącznym, wyróżnienie najszybszego i najwolniejszego, setne sekundy, kopiowanie wyników |
| Minutnik | dowolna liczba minutników naraz, klawiatura czasu („130” to 1 min 30 s), nazwy, zapisane szybkie czasy, „+1 min”, pełny ekran po czasie |
| Więcej | pomodoro z historią sesji, trening interwałowy (Tabata, HIIT, własne plany) z zapowiedziami i piknięciami, metronom z wystukiwaniem tempa, odliczanie do wydarzeń, kalkulator czasu i dat, zegar nocny, szybka drzemka |

Dodatkowo: widżet z godziną, datą i najbliższym budzikiem, skróty aplikacji, obsługa poleceń
systemowych (ustaw budzik, ustaw minutnik, pokaż budziki), polski i angielski, format 12/24 h,
tryb nocny chroniący ekran OLED.

Budziki i minutniki opierają się na dokładnych alarmach systemu i pamięci chronionej urządzeniem,
więc dzwonią także po restarcie telefonu, przed jego odblokowaniem i przy zamkniętej aplikacji.

## Instalacja

Pobierz najnowszy plik `OpenTimer-x.y.z.apk` ze strony aplikacji na
[redzikowski.com](https://redzikowski.com) (Android 10 lub nowszy). Kolejne wersje aplikacja
sprawdza, pobiera i instaluje sama.

## Budowanie

```bash
gradle :app:assembleDebug
gradle testDebugUnitTest
```

Wydanie powstaje wyłącznie po wypchnięciu tagu wersji `vX.Y.Z`: GitHub Actions uruchamia testy,
buduje podpisany APK i publikuje wydanie z opisem z pliku `CHANGELOG.md`.

## Licencja

MIT

---

**Pomysł i wykonanie: Adam Rędzikowski · [redzikowski.com](https://redzikowski.com)**

<a href="https://buycoffee.to/adamredzikowski" target="_blank"><img src="https://buycoffee.to/static/img/share/share-button-dark--pl.png" style="width: 351px; height: 92px" width="351" height="92" alt="Postaw kawę dla adamredzikowski na buycoffee.to"></a>
