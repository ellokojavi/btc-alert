# BTC Alert — Build Plan

A personal Android app for a Pixel 9 that fires notifications on Bitcoin price events. Single user, zero cost, no accounts, no servers.

## 1. What it does

**Alert rules** (any number, each independently enabled):

| Rule type | Fires when | Example |
|---|---|---|
| Crosses above | price goes from below → at/above a level | "BTC rises through $80,000" |
| Crosses below | price goes from above → at/below a level | "BTC drops through $70,000" |
| Percent move | price changes ≥ X% versus the price N minutes ago (either direction, or one direction) | "moves >5% in 60 min" |
| Periodic check-in | a quiet, low-priority notification with the current price every N hours | "price every 6 h" |

**Snooze**: every rule has its own snooze window in minutes. Once a rule fires, it cannot fire again until the window has passed, no matter how many times the price re-crosses. A global "quiet hours" setting (optional) suppresses everything overnight.

**Polling mode** (selectable in Settings):

- *Battery saver* — Android WorkManager runs the check every 15 min (the OS minimum for periodic background work). No persistent notification, no measurable battery cost.
- *Real-time* — a foreground service polls every 60 s (adjustable 30–300 s) and shows a small persistent status-bar notification with the live price. Costs roughly 1–3 % battery per day.

## 2. Data source (free, no API key)

Primary: **Coinbase public spot price** `https://api.coinbase.com/v2/prices/BTC-USD/spot` — no key, no documented hard rate limit for this endpoint, sub-second response.
Fallback: **CoinGecko** `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd` — free tier is ~30 requests/min, plenty for 1 poll/min.
Second fallback: **Binance** `https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT` (USDT, not USD, but within a few dollars).

The fetcher tries each in order and records which one succeeded; the price history (last 24 h of samples) is kept locally for the percent-move rule.

## 3. Architecture

```
app/
 ├─ data/          PriceFetcher (OkHttp), PriceSample history, AlertRule model, Room DB
 ├─ engine/        AlertEngine — pure Kotlin, given (rules, history, now) → list of notifications to send
 ├─ work/          PriceCheckWorker (WorkManager, 15 min) and PriceService (foreground, 60 s)
 ├─ notify/        NotificationChannels + builder
 └─ ui/            Jetpack Compose: rules list, rule editor, settings
```

The **AlertEngine** is deliberately pure (no Android imports) so it can be unit-tested on the JVM: cross-detection, snooze, percent-move windows, and periodic timing are all covered by tests before the app ever touches the phone.

**Stack**: Kotlin, Jetpack Compose (Material 3), Room (SQLite), WorkManager, OkHttp, kotlinx.serialization. minSdk 31, targetSdk 35. All open-source, all free.

**Android 14/15 specifics for Pixel 9**: `POST_NOTIFICATIONS` runtime permission; foreground service type `dataSync` declared in the manifest; app asks to be exempted from battery optimization so the real-time mode isn't killed by Doze.

## 4. Phases

**Phase 1 — MVP (this session)**: all four rule types, per-rule snooze, both polling modes, notification tap opens the app, unit-tested engine, signed APK.

**Phase 2 — polish (next sessions, as you want them)**: quiet hours, price-history chart on the home screen, alert log ("fired at 14:32, price $80,012"), export/import rules as JSON, home-screen widget with live price, multiple coins.

**Phase 3 — optional**: sound/vibration per rule, "notify once then auto-disable" rules, Wear OS mirror.

## 5. How you get it on the phone

**Now**: I compile a signed release APK here and send it to you. On the Pixel 9: open the file → Android asks to allow installs from this source → Install. No developer mode, no cable, no Android Studio.

**Every later iteration**: two options, both free.

- *Option A — I rebuild it.* You describe the change in a new session, I edit the source, rebuild, and send a new APK. Installing over the old one keeps your rules (same signing key + same package name = in-place update).
- *Option B — GitHub Actions builds it.* The source ships with a `.github/workflows/build.yml`. Push to a private GitHub repo and every commit produces a downloadable APK under the Actions tab — no local toolchain. Free for private repos within GitHub's monthly minutes (a build takes ~5 min).
- *Option C — Android Studio on your own computer.* Full IDE, live preview, on-device debugging. Instructions in `README.md`.

The signing keystore (`keystore/btcalert.jks`) ships with the source. Keep it: any future build signed with it can update the installed app without uninstalling; a build signed with a different key cannot.

## 6. Risks and mitigations

- *API changes or rate limits* → three independent sources with automatic fallback; the app shows which source it last used and when.
- *OS kills the background job* → WorkManager survives reboots; the foreground service is restarted on boot via `BOOT_COMPLETED`; the app asks for battery-optimization exemption.
- *Spam if price oscillates around a level* → snooze is per rule and is the core of the design, not an afterthought; default snooze 60 min.
- *Missed cross while phone is offline* → cross detection compares against the last *known* price, so a level crossed while offline still fires on the next successful fetch.
