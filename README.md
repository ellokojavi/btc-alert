<p align="center"><img src="docs/icon.png" width="128" alt="BTC Alert icon"></p>

# BTC Alert

A small, free, open-source Android app that sends you a notification when Bitcoin does something you care about — crosses a price, moves more than X% in a window, or just as a periodic check-in. Dark, minimal UI. No account, no server, no API keys, no ads, no tracking.

**[⬇ Download the latest APK](https://github.com/ellokojavi/btc-alert/releases/latest)** — Android 12 or newer (Pixel, Samsung Galaxy, etc.)

## What it does

**Alert rules** — add as many as you like, each independently switchable:

| Rule | Fires when |
|---|---|
| **Above** `$L` | the previous check was below `L` and this one is at or above it |
| **Below** `$L` | the previous check was above `L` and this one is at or below it |
| **% move** | the price differs by ≥ `X`% from what it was `N` minutes ago (up, down, or either) |
| **Check-in** | every `N` minutes, a quiet notification with the current price |

**Snooze** — every rule has its own snooze in minutes. After it fires, it stays silent until that many minutes have passed, no matter how many times the price wobbles back and forth across the line. That's the whole point of the app: the alert you asked for, once, not twenty times.

**Test notification** — in the rule editor, one tap sends the exact notification that rule would produce, using the live price, so you can see and hear it before you rely on it.

**Live price & chart** — a big animated price that refreshes every 10 seconds while the app is open, with pull-to-refresh, a smooth price chart, and tappable change pills for **1h · 24h · 7d · 30d · 1y · 5y** that switch the chart timeframe (1-min candles for 1h, 5-min for 24h, hourly for 7d, 6-hourly for 30d, daily for 1y, weekly for 5y).

**Two background modes** (Settings):

- *Battery saver* — Android's background scheduler checks every ~15 minutes. Invisible, negligible battery.
- *Real-time* — a small persistent notification shows the live price and polls every 30 s – 5 min. Roughly 1–3% battery per day.

**Quiet hours** — silence notifications overnight; snoozes still count down so you don't get a burst in the morning.

## Install

1. Download the APK from the [latest release](https://github.com/ellokojavi/btc-alert/releases/latest) on your phone.
2. Open it. Android will say it can't install unknown apps from this source → **Settings** → allow → back → **Install**. If Play Protect asks, **Scan** or **Install anyway** — both are fine.
3. Open BTC Alert and **allow notifications**.
4. Tap **New alert**, e.g. *Above* · `80000` · snooze `60`.
5. **Settings → Battery optimization → Request exemption.** Without it, Android can hold background checks for up to an hour while the phone is idle.

**Samsung / One UI users:** Samsung's battery manager is aggressive. Also go to the phone's *Settings → Battery → Background usage limits* and add BTC Alert to **Never sleeping apps**. Real-time mode is the more dependable choice on Samsung.

**Updating:** install the new APK over the old one. Rules and settings are kept.

## Where the prices come from

Free, keyless public endpoints, tried in order until one answers: **Coinbase → CoinGecko → Kraken → Binance**. The home screen shows which one answered last. The 7d/30d/1y/5y change pills use hourly candles from Coinbase Exchange, refreshed every 30 minutes; the 1h and 24h pills use the app's own samples once it has enough of them.

## Privacy

Nothing leaves your phone except the price requests to the exchanges above. Rules, settings, and price history live in one JSON file in the app's private storage. No analytics, no crash reporting, no accounts.

## Building it yourself

**GitHub Actions (no toolchain):** fork the repo, add the four signing secrets below, push to `main` — every push builds a signed APK you can download from the Actions tab; pushing a tag like `v1.4` also publishes a GitHub Release with the APK attached.

| Secret | Value |
|---|---|
| `KEYSTORE_B64` | `base64 -w0 your.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create a keystore with `keytool -genkeypair -keystore btcalert.jks -alias btcalert -keyalg RSA -keysize 2048 -validity 36500`. Keep it: Android only lets an APK update an installed app if both are signed with the same key. Without secrets the build still succeeds, but the release APK is unsigned.

**Android Studio:** open the folder (Ladybug or newer), let it sync, press Run. Debug builds use the package `com.irigoyen.btcalert.debug` so they install alongside the release app.

**Command line:** JDK 17+ and an Android SDK with platform 35, then `./gradlew testReleaseUnitTest assembleRelease`.

## Code map

```
app/src/main/java/com/irigoyen/btcalert/
  model/Models.kt           AlertRule, Settings, PriceSample, Horizon, AppState (JSON-serializable)
  engine/AlertEngine.kt     pure rule logic: crossings, % moves, periodic, snooze, quiet hours, previews
  data/PriceFetcher.kt      spot price from four free APIs with fallback
  data/HistoricalPrices.kt  7d/30d/1y/5y reference prices from Coinbase Exchange hourly candles
  data/ChartData.kt         chart series per timeframe (granularity chosen for ~150–300 points)
  data/Store.kt             single-file JSON persistence + StateFlow for the UI
  data/PriceChecker.kt      fetch → evaluate → notify → save (shared by every polling path)
  work/PriceCheckWorker.kt  WorkManager job (battery-saver mode)
  work/PriceService.kt      foreground service loop (real-time mode)
  work/Scheduler.kt         switches between the two
  work/BootReceiver.kt      re-arms after reboot or app update
  notify/Notifier.kt        channels + notification builders
  ui/Theme.kt               always-dark palette, shapes, type
  ui/MainActivity.kt        home (pull-to-refresh, live price, pills), settings, alert log
  ui/RuleEditorDialog.kt    add / edit / test a rule
app/src/test/…/AlertEngineTest.kt   JVM tests for the engine
```

Stack: Kotlin, Jetpack Compose (Material 3), WorkManager, OkHttp, kotlinx.serialization. minSdk 31, targetSdk 35.

## Roadmap ideas

Alert history with prices · price sparkline · home-screen widget · export/import rules · more coins · per-rule sounds.

## License

MIT — see [LICENSE](LICENSE).
