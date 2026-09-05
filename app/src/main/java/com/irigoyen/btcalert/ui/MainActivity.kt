package com.irigoyen.btcalert.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.irigoyen.btcalert.data.ChainData
import com.irigoyen.btcalert.data.ChartData
import com.irigoyen.btcalert.data.Connectivity
import com.irigoyen.btcalert.model.ChainInfo
import com.irigoyen.btcalert.model.ChartSeries
import com.irigoyen.btcalert.model.blockWaitNote
import com.irigoyen.btcalert.model.isFreshBlock
import com.irigoyen.btcalert.model.paceLabel
import com.irigoyen.btcalert.model.FetchError
import com.irigoyen.btcalert.model.usd
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.irigoyen.btcalert.data.PriceChecker
import com.irigoyen.btcalert.data.Store
import com.irigoyen.btcalert.engine.AlertEngine
import com.irigoyen.btcalert.model.AlertRule
import com.irigoyen.btcalert.model.AppState
import com.irigoyen.btcalert.model.Horizon
import androidx.compose.ui.unit.sp
import com.irigoyen.btcalert.model.PollMode
import com.irigoyen.btcalert.model.RuleType
import com.irigoyen.btcalert.model.Settings
import com.irigoyen.btcalert.model.usd2
import com.irigoyen.btcalert.notify.Notifier
import com.irigoyen.btcalert.work.Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** How often the price refreshes on its own while the app is on screen. */
private const val FOREGROUND_REFRESH_MS = 10_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { BtcAlertTheme { App() } }
    }
}

private enum class Screen { HOME, SETTINGS, LOG }

// POST_NOTIFICATIONS is API 33 and the constant is inlined at compile time, so requesting it on
// Android 12 is a harmless no-op that returns granted. Nothing to guard.
@SuppressLint("InlinedApi")
@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editing by remember { mutableStateOf<AlertRule?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Scheduler.apply(ctx)
    }

    // Live refresh every 10 s while the app is visible; pauses automatically in the background.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                PriceChecker.runOnce(ctx)
                delay(FOREGROUND_REFRESH_MS)
            }
        }
    }

    // Chart data for the selected timeframe: fetch when missing/stale, re-check every 30 s.
    val chartHorizon = Horizon.entries.firstOrNull { it.name == state.settings.chartHorizon } ?: Horizon.D1
    LaunchedEffect(chartHorizon) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                if (!Connectivity.isOffline(ctx) &&
                    ChartData.isStale(store.state.value.charts[chartHorizon.name], chartHorizon, now)
                ) {
                    try {
                        val series = ChartData.fetch(chartHorizon, now)
                        store.update { it.copy(charts = it.charts + (chartHorizon.name to series)) }
                    } catch (_: Exception) { }
                }
                delay(30_000)
            }
        }
    }
    // Chain state for the block card. Only while the app is on screen — nothing about this card
    // is worth a background wakeup, and a stale card is never a missed alert.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                if (!Connectivity.isOffline(ctx) && ChainData.isStale(store.state.value.chain, now)) {
                    try {
                        val info = ChainData.fetch(now)
                        store.update { it.copy(chain = info) }
                    } catch (_: Exception) { }
                }
                delay(10_000)
            }
        }
    }

    val onSelectHorizon: (Horizon) -> Unit = { h ->
        scope.launch { store.update { it.copy(settings = it.settings.copy(chartHorizon = h.name)) } }
    }

    val refreshNow: () -> Unit = {
        scope.launch {
            refreshing = true
            val started = System.currentTimeMillis()
            PriceChecker.runOnce(ctx)
            // Keep the spinner visible long enough to read as a deliberate animation.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 600) delay(600 - elapsed)
            refreshing = false
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            state = state,
            chartHorizon = chartHorizon,
            onSelectHorizon = onSelectHorizon,
            refreshing = refreshing,
            onRefresh = refreshNow,
            onAdd = { editing = null; showEditor = true },
            onEdit = { editing = it; showEditor = true },
            onToggle = { rule, on -> scope.launch { store.update { s -> s.copy(rules = s.rules.map { if (it.id == rule.id) it.copy(enabled = on) else it }) } } },
            onDelete = { rule -> scope.launch { store.update { s -> s.copy(rules = s.rules.filter { it.id != rule.id }, ruleStates = s.ruleStates - rule.id) } } },
            onSettings = { screen = Screen.SETTINGS },
            onLog = { screen = Screen.LOG },
        )
        Screen.SETTINGS -> SettingsScreen(
            state = state,
            onBack = { screen = Screen.HOME },
            onChange = { newSettings ->
                scope.launch {
                    store.update { it.copy(settings = newSettings) }
                    Scheduler.apply(ctx)
                }
            },
        )
        Screen.LOG -> LogScreen(state = state, onBack = { screen = Screen.HOME })
    }

    if (showEditor) {
        RuleEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { rule ->
                scope.launch {
                    store.update { s ->
                        val exists = s.rules.any { it.id == rule.id }
                        s.copy(
                            rules = if (exists) s.rules.map { if (it.id == rule.id) rule else it } else s.rules + rule,
                            ruleStates = s.ruleStates - rule.id,
                        )
                    }
                }
                showEditor = false
            },
            onTest = { rule ->
                val price = state.history.lastOrNull()?.price
                Notifier.postAlert(ctx, AlertEngine.previewFiring(rule, price))
            },
        )
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
private val dayTimeFmt = SimpleDateFormat("EEE HH:mm", Locale.US)

// ---------------------------------------------------------------- Home

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: AppState,
    chartHorizon: Horizon,
    onSelectHorizon: (Horizon) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AlertRule) -> Unit,
    onToggle: (AlertRule, Boolean) -> Unit,
    onDelete: (AlertRule) -> Unit,
    onSettings: () -> Unit,
    onLog: () -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    Scaffold(
        containerColor = Ink.Black,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Ink.White,
                contentColor = Ink.Black,
                shape = CircleShape,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New alert", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().padding(pad),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Ink.SurfaceHigh,
                    color = Ink.Accent,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Header(onLog, onSettings) }
                item { PriceHero(state, chartHorizon, onSelectHorizon) }
                item { BlockCard(state.chain, offline = state.lastFetchError?.kind?.isConnectivity == true) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Alerts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${state.rules.count { it.enabled }} active",
                            style = MaterialTheme.typography.labelMedium, color = Ink.Muted,
                        )
                    }
                }
                if (state.rules.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().background(Ink.Surface, MaterialTheme.shapes.medium).padding(20.dp),
                        ) {
                            Text(
                                "No alerts yet. Tap New alert — for example, \"above $80,000\" with a 60-minute snooze.",
                                style = MaterialTheme.typography.bodyMedium, color = Ink.Muted,
                            )
                        }
                    }
                }
                items(state.rules, key = { it.id }) { rule ->
                    RuleCard(rule, state, onEdit = { onEdit(rule) }, onToggle = { onToggle(rule, it) }, onDelete = { onDelete(rule) })
                }
            }
        }
    }
}

@Composable
private fun Header(onLog: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(Ink.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("₿", color = Ink.Black, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text("Bitcoin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onLog) { Icon(Icons.Default.History, "Alert log", tint = Ink.Muted) }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Ink.Muted) }
    }
}

@Composable
private fun PriceHero(state: AppState, chartHorizon: Horizon, onSelectHorizon: (Horizon) -> Unit) {
    val last = state.history.lastOrNull()
    val target = last?.price?.toFloat() ?: 0f
    val animated by animateFloatAsState(targetValue = target, animationSpec = tween(700), label = "price")
    val prev = state.history.dropLast(1).lastOrNull()
    val tickColor by animateColorAsState(
        targetValue = when {
            last == null || prev == null || last.price == prev.price -> Ink.White
            last.price > prev.price -> Ink.Up
            else -> Ink.Down
        },
        animationSpec = tween(400), label = "tick",
    )

    // "Live" means the price on screen is actually current. The app polls every 10 s while open,
    // so a sample older than 90 s means data has stopped arriving. A connectivity failure kills it
    // outright; a single source hiccup doesn't, because a 20-second-old price is still live.
    // now ticks on its own so the dot goes quiet on time even when nothing else recomposes.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1_000) }
    }
    val live = last != null && now - last.time < 90_000L &&
        state.lastFetchError?.kind?.isConnectivity != true

    Column(Modifier.padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BTC / USD", style = MaterialTheme.typography.labelMedium, color = Ink.Muted)
            Spacer(Modifier.width(7.dp))
            LiveDot(live = live, sampleTime = last?.time ?: 0L)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (last == null) "—" else usd2(animated.toDouble()),
            style = MaterialTheme.typography.displayLarge,
            color = tickColor,
        )
        Spacer(Modifier.height(16.dp))
        PriceChart(
            series = state.charts[chartHorizon.name],
            live = last,
            horizon = chartHorizon,
            offline = state.lastFetchError?.kind?.isConnectivity == true,
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
        Spacer(Modifier.height(12.dp))
        // Six equal-width pills on one line; each gets weight(1f) so they can never wrap or overflow.
        // Tapping one selects the chart timeframe.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Horizon.entries.forEach { h ->
                ChangeChip(
                    label = h.label,
                    pct = changePct(state, last, h),
                    selected = h == chartHorizon,
                    onClick = { onSelectHorizon(h) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val err = state.lastFetchError
        val status = when {
            last != null -> "${last.source} · ${timeFmt.format(Date(last.time))} · ${state.settings.pollMode.label}"
            err != null -> "No price yet"
            else -> "Fetching first price…"
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = Ink.Faint)
        if (err != null) {
            Spacer(Modifier.height(10.dp))
            ConnectionBanner(err, last)
        }
    }
}

/**
 * A failed fetch is a normal part of carrying a phone around, so it gets a calm card saying what
 * happened and how old the price on screen is — not a red line of hostname errors. The per-source
 * detail stays available on the log screen for when it's actually wanted.
 */
@Composable
private fun ConnectionBanner(err: FetchError, last: com.irigoyen.btcalert.model.PriceSample?) {
    Row(
        Modifier.fillMaxWidth().background(Ink.Surface, MaterialTheme.shapes.small).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (err.kind.isConnectivity) Icons.Default.WifiOff else Icons.Default.CloudOff,
            contentDescription = null, tint = Ink.Accent, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(err.kind.headline, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            val hint = if (last == null) err.kind.hint
            else "${err.kind.hint} Last price ${ago(err.at - last.time)}."
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
        }
    }
}

/** Coarse on purpose: "just now", "4 min ago", "3 h ago", "2 d ago". */
private fun ago(ms: Long): String {
    val d = ms.coerceAtLeast(0L)
    return when {
        d < 60_000L -> "just now"
        d < 3_600_000L -> "${d / 60_000L} min ago"
        d < 86_400_000L -> "${d / 3_600_000L} h ago"
        else -> "${d / 86_400_000L} d ago"
    }
}

/**
 * Percent change over [h]. Prefers the locally sampled history when it reaches back far
 * enough (exact to the poll interval); otherwise uses the fetched exchange reference price.
 */
private fun changePct(state: AppState, last: com.irigoyen.btcalert.model.PriceSample?, h: Horizon): Double? {
    if (last == null) return null
    val target = last.time - h.millis
    // The chart series is the source of truth when we have it, so the pill and the chart's
    // baseline always tell the same story; local samples and the cached ref are fallbacks.
    val chartRef = state.charts[h.name]?.points?.firstOrNull()?.price
    val local = AlertEngine.referenceSample(state.history.dropLast(1), target)?.price
    val refPrice = chartRef ?: local ?: state.historical[h.name]?.price ?: return null
    return (last.price - refPrice) / refPrice * 100.0
}

/**
 * Smooth line chart of a [ChartSeries] with the live price appended as the final point.
 * Colour follows the direction over the timeframe; the path animates in when the timeframe changes.
 */
@Composable
private fun PriceChart(
    series: ChartSeries?,
    live: com.irigoyen.btcalert.model.PriceSample?,
    horizon: Horizon,
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    val points = remember(series, live) {
        val base = series?.points.orEmpty()
        if (live != null && (base.isEmpty() || live.time > base.last().time)) base + live else base
    }
    val reveal = remember(horizon) { Animatable(0f) }
    LaunchedEffect(horizon, series == null) {
        if (series != null) { reveal.snapTo(0f); reveal.animateTo(1f, tween(650)) }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (points.size < 2) {
            Text(
                when {
                    series != null -> "Not enough data"
                    offline -> "Chart unavailable offline"
                    else -> "Loading ${horizon.label} chart…"
                },
                style = MaterialTheme.typography.bodySmall, color = Ink.Faint,
            )
            return@Box
        }
        val first = points.first().price
        val lastP = points.last().price
        val lineColor = if (lastP >= first) Ink.Up else Ink.Down
        val minP = points.minOf { it.price }
        val maxP = points.maxOf { it.price }
        val labelStyle = MaterialTheme.typography.labelMedium.copy(color = Ink.Faint, letterSpacing = 0.sp)
        val measurer = rememberTextMeasurer()
        val hiText = remember(maxP) { "H ${usd(maxP)}" }
        val loText = remember(minP) { "L ${usd(minP)}" }
        // The y-axis is zoomed to min..max, so a 0.2%-wide window fills the whole box. Saying how
        // wide the window is stops a flat timeframe from looking like a crash.
        val spanText = remember(minP, maxP) { "span ${fmtChange((maxP - minP) / minP * 100).removePrefix("+")}" }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padTop = 14.dp.toPx()
            val padBottom = 14.dp.toPx()
            val padRight = 4.dp.toPx()
            val t0 = points.first().time
            val t1 = points.last().time
            val span = (t1 - t0).coerceAtLeast(1L).toFloat()
            val range = (maxP - minP).let { if (it <= 0.0) maxP * 0.01 else it }
            fun x(t: Long) = (t - t0) / span * (w - padRight)
            fun y(p: Double) = padTop + ((maxP - p) / range).toFloat() * (h - padTop - padBottom)

            // Smooth path: cubic segments with horizontal control points at the x-midpoint.
            // Monotone (no overshoot), reads as a curve even at 60–300 points.
            val line = Path().apply {
                moveTo(x(points[0].time), y(points[0].price))
                for (i in 1 until points.size) {
                    val px = x(points[i - 1].time); val py = y(points[i - 1].price)
                    val cx = x(points[i].time); val cy = y(points[i].price)
                    val mx = (px + cx) / 2f
                    cubicTo(mx, py, mx, cy, cx, cy)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(t1), h); lineTo(x(t0), h); close()
            }

            // Dashed baseline at the opening price: the visual zero for this timeframe.
            val yOpen = y(first)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()), 0f)
            drawLine(
                color = Ink.Outline,
                start = Offset(0f, yOpen), end = Offset(w, yOpen),
                strokeWidth = 1.dp.toPx(), pathEffect = dash,
            )

            val revealW = w * reveal.value
            clipRect(right = revealW) {
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        0f to lineColor.copy(alpha = 0.28f), 1f to lineColor.copy(alpha = 0f),
                        startY = padTop, endY = h,
                    ),
                )
                drawPath(line, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (reveal.value >= 1f) {
                val ex = x(t1); val ey = y(lastP)
                drawCircle(lineColor.copy(alpha = 0.25f), radius = 7.dp.toPx(), center = Offset(ex, ey))
                drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(ex, ey))
                drawCircle(Ink.Black, radius = 1.2.dp.toPx(), center = Offset(ex, ey))
            }
            // High / low labels, tucked into the corners so they never collide with the line ends.
            val hi = measurer.measure(hiText, labelStyle)
            val lo = measurer.measure(loText, labelStyle)
            drawText(hi, topLeft = Offset(0f, 0f))
            drawText(lo, topLeft = Offset(0f, h - lo.size.height))
            val sp = measurer.measure(spanText, labelStyle)
            drawText(sp, topLeft = Offset(w - sp.size.width - padRight, h - sp.size.height))
        }
    }
}

/** Compact, fixed-width-friendly percent: +4.2%  −12.8%  +156%  +1.2k% */
private fun fmtChange(pct: Double): String {
    val a = abs(pct)
    val sign = if (pct >= 0) "+" else "−"
    val body = when {
        a >= 1000 -> "%.1fk%%".format(Locale.US, a / 1000)
        a >= 100 -> "%.0f%%".format(Locale.US, a)
        else -> "%.1f%%".format(Locale.US, a)
    }
    return sign + body
}

/** How long one full run of "tick tock next block" takes. */
private const val PHRASE_MS = 5400

/**
 * Chain status: block height, when the next one is due, and the phrase building itself a word at
 * a time. Only the source text is tappable — the card sits inside the app, so there is nothing
 * else for a tap to usefully do.
 */
@Composable
private fun BlockCard(chain: ChainInfo?, offline: Boolean) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }

    val minutesSince = chain?.takeIf { it.minedAt > 0 }?.let { (now - it.minedAt) / 60_000L }
    val known = chain != null && chain.height > 0L
    val live = known && !offline
    val alpha = if (live) 1f else 0.66f

    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.Surface, MaterialTheme.shapes.small)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlockCube(Modifier.size(24.dp), alpha = if (live) 0.42f else 0.18f)
            Spacer(Modifier.width(9.dp))
            Text(
                if (known) "%,d".format(Locale.US, chain!!.height) else "—",
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.9).sp),
                color = if (live) Ink.White else Ink.Muted,
            )
            if (minutesSince != null && live && isFreshBlock(minutesSince)) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "NEW",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Ink.Up,
                    modifier = Modifier
                        .border(1.dp, Ink.Up.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            TickTockPhrase(running = live)
        }

        val lead = if (!known) "next —"
        else "next ${paceLabel(chain!!.difficultyChangePct)}${minutesSince?.let { blockWaitNote(it) } ?: ""}"
        val rest = buildList {
            if (known) {
                if (chain!!.txCount > 0) add("%,d txs".format(Locale.US, chain.txCount))
                if (chain.feeSatVb > 0) add("${chain.feeSatVb} sat/vB")
                if (chain.pool.isNotBlank()) add(chain.pool)
            } else add("no chain data")
        }.joinToString(" · ")

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lead,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (live) Color(0xFFC7CAD1) else Ink.Muted,
                maxLines = 1,
            )
            Text(
                "  ·  $rest  ·  ",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val url = if (known) "https://mempool.space/block/${chain!!.height}" else "https://mempool.space"
            Text(
                "mempool.space ↗",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Faint,
                maxLines = 1,
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }
    }
}

/** Translucent isometric block. Three faces at different alphas so it reads as glass, not a hexagon. */
@Composable
private fun BlockCube(modifier: Modifier = Modifier, alpha: Float) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        fun face(points: List<Offset>, a: Float) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Ink.Accent.copy(alpha = a * alpha))
        }
        face(listOf(p(.5f, .06f), p(.94f, .31f), p(.5f, .55f), p(.06f, .31f)), 0.95f)   // top
        face(listOf(p(.06f, .31f), p(.5f, .55f), p(.5f, .95f), p(.06f, .71f)), 0.45f)   // left
        face(listOf(p(.94f, .31f), p(.5f, .55f), p(.5f, .95f), p(.94f, .71f)), 0.68f)   // right
    }
}

/**
 * "tick tock next block", one word at a time: each fades in at full accent, then eases back to
 * faint as the next arrives, and the line clears before starting over. The words keep their slots
 * throughout so nothing reflows. Stops on the word it reached when the data isn't live.
 */
@Composable
private fun TickTockPhrase(running: Boolean) {
    val words = listOf("tick", "tock", "next", "block")
    val phase by if (running) {
        rememberInfiniteTransition(label = "phrase").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PHRASE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "phrasePhase",
        )
    } else {
        remember { mutableFloatStateOf(0.45f) }   // frozen mid-phrase: "tick tock" showing
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        words.forEachIndexed { i, word ->
            val start = i * 0.16f
            val appeared = smoothRamp(phase, start, start + 0.07f)
            val faded = if (i == words.lastIndex) 0f else smoothRamp(phase, start + 0.16f, start + 0.24f)
            val cleared = smoothRamp(phase, 0.86f, 0.96f)
            Text(
                word,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.9.sp,
                ),
                color = lerp(Ink.Accent, Ink.Faint, faded)
                    .copy(alpha = (appeared * (1f - cleared)).coerceIn(0f, 1f)),
            )
        }
    }
}

/** 0 before [from], 1 after [to], smoothstepped between — no hard edges anywhere in the phrase. */
private fun smoothRamp(x: Float, from: Float, to: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Orange "live" dot: a breathing core, two ripples half a cycle apart so a ring is always in
 * flight, and a flare each time a new price actually lands. When the price isn't live the whole
 * thing stops and the dot dims — a pulse that keeps going while nothing arrives would be the one
 * thing this indicator must never do.
 *
 * @param sampleTime timestamp of the newest price, so the flare fires on real data rather than a timer.
 */
@Composable
private fun LiveDot(live: Boolean, sampleTime: Long, modifier: Modifier = Modifier) {
    val box = 26.dp
    val coreD = 8.dp

    if (!live) {
        Box(modifier.size(box), contentAlignment = Alignment.Center) {
            Box(Modifier.size(coreD).background(Ink.Faint, CircleShape))
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "live")
    // One linear 0→1 sweep drives both ripples; the second is read half a phase ahead.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )
    val breath by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    // A visible kick the moment a new sample lands, decaying back to the resting size.
    val flare = remember { Animatable(1f) }
    LaunchedEffect(sampleTime) {
        flare.snapTo(1.55f)
        flare.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }

    Canvas(modifier.size(box)) {
        val coreR = coreD.toPx() / 2f
        val maxR = size.minDimension / 2f
        repeat(2) { i ->
            val p = (phase + i * 0.5f) % 1f
            // Hold most of the opacity through the middle of the travel, where the ring is big
            // enough to read, instead of spending it while it's still hidden behind the core.
            val alpha = 0.55f * (1f - p) * (1f - p * 0.35f)
            drawCircle(
                color = Ink.Accent.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = coreR + (maxR - coreR) * p,
                center = center,
            )
        }
        drawCircle(Ink.Accent, radius = coreR * breath * flare.value, center = center)
    }
}

@Composable
private fun ChangeChip(label: String, pct: Double?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = when {
        pct == null -> Ink.Faint
        pct >= 0 -> Ink.Up
        else -> Ink.Down
    }
    val bg by animateColorAsState(if (selected) Ink.SurfaceHigh else Ink.Surface, tween(200), label = "chipBg")
    val border by animateColorAsState(if (selected) Ink.White.copy(alpha = 0.6f) else Color.Transparent, tween(200), label = "chipBorder")
    Column(
        modifier
            .background(bg, RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Ink.White else Ink.Muted, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        Text(
            if (pct == null) "—" else fmtChange(pct),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            color = color, maxLines = 1, softWrap = false,
        )
    }
}

@Composable
private fun RuleCard(rule: AlertRule, state: AppState, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val st = state.ruleStates[rule.id]
    val accent = when (rule.type) {
        RuleType.CROSS_ABOVE -> Ink.Up
        RuleType.CROSS_BELOW -> Ink.Down
        RuleType.PERCENT_MOVE -> Ink.Accent
        RuleType.PERIODIC -> Ink.Muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.Surface, MaterialTheme.shapes.medium)
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (rule.enabled) accent else Ink.Faint, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                rule.describe(), style = MaterialTheme.typography.titleMedium,
                color = if (rule.enabled) Ink.White else Ink.Muted,
            )
            val snooze = if (rule.type == RuleType.PERIODIC) null else "snooze ${rule.snoozeMinutes} min"
            val fired = st?.lastFiredAt?.let { "fired ${dayTimeFmt.format(Date(it))}" } ?: "not fired yet"
            Text(
                listOfNotNull(snooze, fired).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
            )
        }
        Switch(
            checked = rule.enabled, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Black, checkedTrackColor = Ink.White,
                uncheckedThumbColor = Ink.Muted, uncheckedTrackColor = Ink.SurfaceHigh, uncheckedBorderColor = Ink.Outline,
            ),
        )
        IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Ink.Faint) }
    }
}

// ---------------------------------------------------------------- Shared

@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = Ink.Black) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Ink.White) }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content(PaddingValues(0.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink.Muted, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) Ink.White else Ink.Surface, CircleShape)
            .border(1.dp, if (selected) Ink.White else Ink.Outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink.Black else Ink.White) }
}

// ---------------------------------------------------------------- Log

@Composable
private fun LogScreen(state: AppState, onBack: () -> Unit) {
    SubScreen("Alert log", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
            state.lastFetchError?.let { err ->
                item {
                    Column(Modifier.padding(bottom = 18.dp)) {
                        SectionLabel("Connection")
                        Spacer(Modifier.height(6.dp))
                        Text(err.kind.headline, style = MaterialTheme.typography.bodyMedium, color = Ink.Accent)
                        if (err.detail.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(err.detail, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Last attempt ${dayTimeFmt.format(Date(err.at))}",
                            style = MaterialTheme.typography.bodySmall, color = Ink.Faint,
                        )
                    }
                }
            }
            if (state.log.isEmpty()) item { Text("Nothing has fired yet.", color = Ink.Muted) }
            items(state.log) { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                HorizontalDivider(color = Ink.Outline)
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

// Lint flags REQUEST_IGNORE_BATTERY_OPTIMIZATIONS as a Play Store policy violation. This app is
// installed from a GitHub release, not Play, and the exemption is the difference between a 15-minute
// alert and an hour-late one — it's the documented setup step in the README.
@SuppressLint("BatteryLife")
@Composable
private fun SettingsScreen(state: AppState, onBack: () -> Unit, onChange: (Settings) -> Unit) {
    val ctx = LocalContext.current
    val s = state.settings
    val pm = ctx.getSystemService(PowerManager::class.java)
    var ignoringBattery by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        ignoringBattery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    SubScreen("Settings", onBack) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Background polling")
            PollMode.entries.forEach { mode ->
                val selected = s.pollMode == mode
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Ink.Surface, MaterialTheme.shapes.medium)
                        .border(1.dp, if (selected) Ink.White else Color.Transparent, MaterialTheme.shapes.medium)
                        .clickable { onChange(s.copy(pollMode = mode)) }
                        .padding(16.dp),
                ) {
                    Text(mode.label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(mode.blurb, style = MaterialTheme.typography.bodySmall, color = Ink.Muted)
                }
            }
            Text(
                "While the app is open, the price refreshes every 10 seconds regardless of this setting.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Faint,
            )
            if (s.pollMode == PollMode.REALTIME) {
                SectionLabel("Real-time interval")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 300).forEach { sec ->
                        Pill(if (sec < 60) "${sec}s" else "${sec / 60} min", s.realtimeIntervalSec == sec) {
                            onChange(s.copy(realtimeIntervalSec = sec))
                        }
                    }
                }
            }

            SectionLabel("Battery optimization")
            Text(
                if (ignoringBattery) "Exempt — Android won't throttle background checks."
                else "Not exempt. Recommended: otherwise Doze can delay background checks by up to an hour.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Muted,
            )
            if (!ignoringBattery) Pill("Request exemption", selected = true) {
                batteryLauncher.launch(
                    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                )
            }

            SectionLabel("Quiet hours")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.quietHoursEnabled) "Silenced %02d:00 → %02d:00".format(s.quietStartHour, s.quietEndHour) else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = s.quietHoursEnabled, onCheckedChange = { onChange(s.copy(quietHoursEnabled = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink.Black, checkedTrackColor = Ink.White,
                        uncheckedThumbColor = Ink.Muted, uncheckedTrackColor = Ink.SurfaceHigh, uncheckedBorderColor = Ink.Outline,
                    ),
                )
            }
            if (s.quietHoursEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start", color = Ink.Muted)
                    HourStepper(s.quietStartHour) { onChange(s.copy(quietStartHour = it)) }
                    Spacer(Modifier.width(12.dp))
                    Text("End", color = Ink.Muted)
                    HourStepper(s.quietEndHour) { onChange(s.copy(quietEndHour = it)) }
                }
            }
        }
    }
}

@Composable
private fun HourStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("−", false) { onChange((value + 23) % 24) }
        Text("%02d".format(value), style = MaterialTheme.typography.titleMedium)
        Pill("+", false) { onChange((value + 1) % 24) }
    }
}
