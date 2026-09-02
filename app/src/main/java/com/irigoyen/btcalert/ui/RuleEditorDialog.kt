package com.irigoyen.btcalert.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.irigoyen.btcalert.model.AlertRule
import com.irigoyen.btcalert.model.Direction
import com.irigoyen.btcalert.model.RuleType

@Composable
fun RuleEditorDialog(
    initial: AlertRule?,
    onDismiss: () -> Unit,
    onSave: (AlertRule) -> Unit,
    onTest: (AlertRule) -> Unit = {},
) {
    var tested by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(initial?.type ?: RuleType.CROSS_ABOVE) }
    var level by remember { mutableStateOf(initial?.level?.takeIf { it > 0 }?.toLong()?.toString() ?: "") }
    var percent by remember { mutableStateOf(initial?.percent?.toString() ?: "5") }
    var window by remember { mutableStateOf((initial?.windowMinutes ?: 60).toString()) }
    var direction by remember { mutableStateOf(initial?.direction ?: Direction.ANY) }
    var snooze by remember { mutableStateOf((initial?.snoozeMinutes ?: 60).toString()) }

    val levelOk = type != RuleType.CROSS_ABOVE && type != RuleType.CROSS_BELOW || (level.toDoubleOrNull() ?: 0.0) > 0
    val pctOk = type != RuleType.PERCENT_MOVE || (percent.toDoubleOrNull() ?: 0.0) > 0
    val windowOk = (window.toIntOrNull() ?: 0) > 0
    val snoozeOk = type == RuleType.PERIODIC || (snooze.toIntOrNull() ?: -1) >= 0
    val valid = levelOk && pctOk && windowOk && snoozeOk

    fun buildRule() = AlertRule(
        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
        type = type,
        enabled = initial?.enabled ?: true,
        level = level.toDoubleOrNull() ?: 0.0,
        percent = percent.toDoubleOrNull() ?: 5.0,
        windowMinutes = window.toIntOrNull() ?: 60,
        direction = direction,
        snoozeMinutes = snooze.toIntOrNull() ?: 60,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.White,
        textContentColor = Ink.White,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        title = { Text(if (initial == null) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Trigger")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == RuleType.CROSS_ABOVE, onClick = { type = RuleType.CROSS_ABOVE }, label = { Text("Above") })
                    FilterChip(selected = type == RuleType.CROSS_BELOW, onClick = { type = RuleType.CROSS_BELOW }, label = { Text("Below") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = type == RuleType.PERCENT_MOVE, onClick = { type = RuleType.PERCENT_MOVE }, label = { Text("% move") })
                    FilterChip(selected = type == RuleType.PERIODIC, onClick = { type = RuleType.PERIODIC }, label = { Text("Check-in") })
                }

                when (type) {
                    RuleType.CROSS_ABOVE, RuleType.CROSS_BELOW -> {
                        OutlinedTextField(
                            value = level, onValueChange = { level = it.filter { c -> c.isDigit() } },
                            label = { Text("Price level (USD)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    RuleType.PERCENT_MOVE -> {
                        OutlinedTextField(
                            value = percent, onValueChange = { percent = it },
                            label = { Text("Move size (%)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = window, onValueChange = { window = it.filter { c -> c.isDigit() } },
                            label = { Text("Within the last (minutes)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Direction.entries.forEach { d ->
                                FilterChip(selected = direction == d, onClick = { direction = d }, label = { Text(d.label) })
                            }
                        }
                    }
                    RuleType.PERIODIC -> {
                        OutlinedTextField(
                            value = window, onValueChange = { window = it.filter { c -> c.isDigit() } },
                            label = { Text("Every (minutes)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (type != RuleType.PERIODIC) {
                    OutlinedTextField(
                        value = snooze, onValueChange = { snooze = it.filter { c -> c.isDigit() } },
                        label = { Text("Snooze after firing (minutes)") },
                        supportingText = { Text("Won't fire again until this many minutes have passed. 0 = every check.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // One-tap preview: posts a real notification for this rule as currently configured.
                OutlinedButton(
                    enabled = valid,
                    onClick = { onTest(buildRule()); tested = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Ink.Outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Accent),
                ) {
                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (tested) "Sent — tap to test again" else "Test notification")
                }
                Text(
                    "Sends what this alert will look like, using the live price. Doesn't affect snooze.",
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faint,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSave(buildRule()) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Ink.White, contentColor = Ink.Black),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Muted) } },
    )
}
