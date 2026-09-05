# BTC Alert — working notes for Claude Code

Personal Android app that notifies on Bitcoin price events. Kotlin + Jetpack Compose,
single module, no backend, no API keys. Owner: Javier (@ellokojavi). Repo:
https://github.com/ellokojavi/btc-alert · Releases: `/releases/latest`

## Commands

```bash
./gradlew testReleaseUnitTest        # JVM tests for the alert engine (fast, run this first)
./gradlew lintRelease                # must stay at 0 errors
./gradlew assembleRelease            # → app/build/outputs/apk/release/app-release.apk
./gradlew installRelease             # to a connected device (adb)
```

Needs JDK 17+ and an Android SDK with platform 35 + build-tools 35 (`local.properties`
holds `sdk.dir`, untracked). If the toolchain isn't installed locally, push a branch and
let CI build — the workflow runs the same three commands.

## Architecture

Data flows one way: **fetch → evaluate → notify → persist → UI reads state**.

- `engine/AlertEngine.kt` — the only place rule semantics live. Pure Kotlin, zero Android
  imports, fully unit-tested. Anything that decides *whether an alert fires* belongs here,
  not in a worker, service, or composable.
- `data/PriceChecker.runOnce()` — the single entry point every polling path calls
  (WorkManager worker, foreground service, pull-to-refresh, the 10 s foreground loop).
  Never duplicate the fetch→notify sequence elsewhere; call this.
- `data/Store.kt` — one JSON file (`filesDir/state.json`), mutex-guarded, exposed as a
  `StateFlow<AppState>`. All writes go through `store.update { }`. No Room, no DataStore.
- `data/PriceFetcher.kt` — spot price, four keyless sources tried in order
  (Coinbase → CoinGecko → Kraken → Binance). Add sources here, not at call sites. Each failure is
  a `SourceFailure` carrying whether it was a transport error (no reply) or a bad reply.
- `data/Connectivity.kt` — the device's `NetworkStatus`; the only Android piece of offline handling.
  `model/FetchError.kt` holds the pure `classifyFetchError()` that turns it into user-facing copy.
- `data/ChartData.kt` / `data/HistoricalPrices.kt` — Coinbase Exchange candles.
- `ui/` — Compose. `Theme.kt` owns the palette (`Ink`); the app is always dark and does
  **not** use dynamic color. `MainActivity.kt` is home + settings + log; the rule editor
  is its own file.

## Invariants — don't break these

1. **Signing key.** `keystore/btcalert.jks` is gitignored and lives in CI secrets
   (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Every release must
   be signed with that same key or the APK won't install over an existing copy. Never
   commit the keystore — the repo is public.
2. **Bump `versionCode` and `versionName`** in `app/build.gradle.kts` for any build the
   user will install. `versionCode` is a monotonic integer; the tag is `v<versionName>`.
3. **Snooze is the product.** A rule that fires must not fire again until its snooze has
   elapsed, however many times the price re-crosses. Quiet hours suppress the *notification*
   but still consume the snooze, so a night of crossings can't burst at 07:00.
4. **No alert on the first sample.** Cross rules need a previous price; with no history
   there is no crossing. Tests cover this.
5. **State is serializable.** Everything in `AppState` is `@Serializable` with defaults —
   adding a field must not break an installed app's existing `state.json`.
6. **The engine stays pure.** If a change needs Android APIs inside `AlertEngine`, the
   design is wrong; pass the value in instead.

## Domain gotchas discovered the hard way

- **Chart y-axis is auto-zoomed** to the window's min/max, so a 0.08 % hour fills a third
  of the plot. That's why there's a dashed baseline at the opening price and a "span N%"
  label. Don't remove them without replacing the reference they provide.
- **Coinbase candles cap at 300 per request.** `ChartData` chunks accordingly. Granularity
  per timeframe targets ~150–300 points: 1h→60 s, 24h→300 s, 7d→3600 s, 30d→21600 s,
  1y→86400 s, 5y→86400 s keeping 1 in 7. Only 60/300/900/3600/21600/86400 are valid.
- **Polling aliases the market.** Alerts see discrete samples; the market is continuous. A
  2-minute spike through a level is missed ~87% of the time by 15-minute polling — this
  actually happened at $82k. `IntervalExtremes` fetches the exchange high/low for the gap
  between samples and cross rules test against those, so detection no longer depends on the
  poll interval. Only requested when a cross rule is armed and the gap ≥ 90 s.
- **Change pills read the chart series' first point** when it exists, so a pill and the
  chart baseline can never disagree. Local samples are the fallback.
- **Price history is thinned**, not capped naively: every sample from the last 10 min,
  then one per minute, 48 h max. 10 s polling would otherwise blow the cap in ~11 h.
- **Offline is a state, not an error.** With no active network `PriceChecker.runOnce()` doesn't
  attempt the four requests at all — it records `FetchErrorKind.OFFLINE` and returns. Nothing needs
  to schedule a retry: WorkManager's `CONNECTED` constraint, the real-time loop and the app's 10 s
  foreground tick all come back around on their own. `NetworkStatus.UNVALIDATED` (captive portal)
  still gets a fetch attempt; it only changes the wording. Never surface raw source errors in the
  hero — they go to `FetchError.detail` and the log screen.
- **Samsung/One UI** kills background work aggressively; battery-optimization exemption
  plus "Never sleeping apps" is the documented workaround. Real-time mode is a foreground
  service with `specialUse` type — the manifest property explaining why is required by
  Android 14+.
- **The androidx versions are pinned by AGP 8.7.3 / compileSdk 35, not by neglect.** `lintRelease`
  reports five `GradleDependency` warnings; the versions it suggests (core-ktx 1.19, activity-compose
  1.13, lifecycle 2.11, work 2.11.2) require **compileSdk 37 and AGP 9.1+**. Upgrading is a toolchain
  migration with a `targetSdk` bump attached, not a dependency bump — don't start it casually, and
  don't bump to intermediate versions either: lint compares against the newest release, so the
  warnings stay regardless.
- **The four remaining lint suppressions are deliberate**, each with its reason in a comment:
  `InlinedApi` ×3 (API 33/34 constants that inline harmlessly at minSdk 31) and `BatteryLife` (a Play
  Store policy that doesn't apply to a sideloaded app whose whole job needs the exemption). Lint is at
  zero errors and zero warnings other than `GradleDependency`; keep it that way.
- **Renaming a resource directory needs `./gradlew clean`.** After `mipmap-anydpi-v26` became
  `mipmap-anydpi`, incremental builds kept linking the old merged output and failed with
  "resource mipmap/ic_launcher not found" against a tree that was perfectly correct.
- **The live dot must tell the truth.** It animates only when the last sample is under 90 s old and
  the last failure wasn't a connectivity one (a single source hiccup doesn't make a 20-second-old
  price stale); otherwise the motion stops and the dot dims. An indicator that keeps pulsing while
  nothing arrives is worse than no indicator. A `now` value ticks once a second inside the hero so
  it goes quiet on time even when no other state changes.
- **Subtle motion reads as no motion on a phone.** v1.8's dot was a single ring travelling 3.5→9 dp
  while fading from 45% — invisible in practice, and the core never moved, so the whole thing looked
  static. What works: a core that breathes, two ripples half a cycle apart so one is always in
  flight, and a flare keyed to the arriving sample timestamp. Motion tied to real data beats motion
  on a timer.
- **GitHub Actions:** the `secrets` context is not allowed in a step-level `if:`. Check
  inside the shell instead (this silently produced "No jobs were run").

## Release process

```bash
# after bumping versionCode/versionName
./gradlew testReleaseUnitTest lintRelease assembleRelease
git commit -am "vX.Y: ..." && git tag vX.Y
git push origin main vX.Y      # the tag build publishes the Release with the APK attached
```

Pushing to `main` builds and uploads an artifact; only a `v*` tag publishes a Release.
The README's download link always points at `/releases/latest`, so a published tag is what
makes a new version reachable by people the link was shared with.

## Conventions

- Comments explain *why*, and are worth writing where a choice looks arbitrary (granularity
  tables, the snooze/quiet-hours interaction, the 300-candle chunking). Don't narrate what
  the code already says.
- New engine behaviour ships with a test in `AlertEngineTest`. UI changes don't need tests
  but must keep `lintRelease` at zero errors.
- Keep the README user-facing (install, features, privacy); implementation detail goes here.
