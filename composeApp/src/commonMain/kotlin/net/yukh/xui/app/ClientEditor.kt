package net.yukh.xui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.masqueEmailInvalid
import net.yukh.xui.shared.dto.withRenewal
import net.yukh.xui.shared.secureRandomIndex

private const val GBC = 1_073_741_824.0

/** Create/edit form for a client; the card ([ClientCardSheet]) holds the links and actions.
 *  On save the caller reconciles inbound membership (attach checked, detach unchecked). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientEditorScreen(
    source: Client,
    isNew: Boolean,
    availableInbounds: List<InboundSlim>,
    availableGroups: List<String> = emptyList(),
    saving: Boolean,
    error: String?,
    /** The panel's own time zone, when it names a real one — shown next to the
     *  expiry so the two clocks are never confused. */
    panelTimeZone: String = "",
    onSave: (ClientModel, List<Int>) -> Unit,
    onCancel: () -> Unit,
) {
    // Pre-select the client's current inbound membership (empty for a new client).
    var selectedInbounds by remember { mutableStateOf(source.inboundIds.toSet()) }
    var email by remember { mutableStateOf(source.email) }
    var enable by remember { mutableStateOf(source.enable) }
    var totalGb by remember { mutableStateOf(gbStr(source.totalGB)) }
    var limitIp by remember { mutableStateOf(if (source.limitIp > 0) source.limitIp.toString() else "") }
    var reset by remember { mutableStateOf(if (source.reset > 0) source.reset.toString() else "") }
    // Panel v3.7.0 client fields.
    var resetDay by remember { mutableStateOf(if (source.resetDay > 0) source.resetDay.toString() else "") }
    var resetMax by remember { mutableStateOf(if (source.resetMax > 0) source.resetMax.toString() else "") }
    var trafficReset by remember { mutableStateOf(source.trafficReset.ifBlank { "never" }) }
    var trafficResetDay by remember { mutableStateOf(source.trafficResetDay.coerceAtLeast(1).toString()) }
    var limitHwid by remember { mutableStateOf(if (source.limitHwid > 0) source.limitHwid.toString() else "") }
    var forwardedPorts by remember { mutableStateOf(source.forwardedPorts) }
    // Expiry is an instant, not a date: the panel stores it to the minute.
    var expiryTime by remember { mutableStateOf(source.expiryTime) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Holds the chosen day (UTC midnight, as the picker reports it) until the
    // time is chosen.
    var pendingDay by remember { mutableStateOf<Long?>(null) }
    var tgId by remember { mutableStateOf(if (source.tgId != 0L) source.tgId.toString() else "") }
    var group by remember { mutableStateOf(source.group) }
    var comment by remember { mutableStateOf(source.comment) }
    var secret by remember { mutableStateOf(source.secret) }
    var adTag by remember { mutableStateOf(source.adTag) }
    var allowedIps by remember { mutableStateOf(source.allowedIPs) }
    // PersistentKeepalive seconds for WireGuard/AmneziaWG peers. A new client gets 25,
    // like the panel's own form; 0 turns keepalives off.
    var keepAlive by remember { mutableStateOf(if (isNew) "25" else source.keepAlive.toString()) }

    // Show protocol-specific fields when a selected inbound is MTProto / WireGuard.
    val selectedProtocols = availableInbounds.filter { it.id in selectedInbounds }.map { it.protocol.lowercase() }
    val isMtproto = "mtproto" in selectedProtocols
    val isWireguard = "wireguard" in selectedProtocols
    val isAmneziawg = "amneziawg" in selectedProtocols
    // Panel v3.8.0 serves TUIC outside Xray, so per-client traffic and IP limits don't apply.
    val isTuic = "tuic" in selectedProtocols
    val emailInvalidForMasque = masqueEmailInvalid(email, selectedProtocols)

    val canSave = !saving && email.isNotBlank() && (!isNew || selectedInbounds.isNotEmpty()) &&
        !emailInvalidForMasque
    fun build(): ClientModel {
        val base = source.toModel().copy(
            email = email.trim(),
            enable = enable,
            totalGB = totalGb.trim().replace(',', '.').toDoubleOrNull()?.let { (it * GBC).toLong() } ?: 0L,
            limitIp = limitIp.toIntOrNull() ?: 0,
            resetMax = (resetMax.toIntOrNull() ?: 0).coerceAtLeast(0),
            trafficReset = trafficReset,
            trafficResetDay = (trafficResetDay.toIntOrNull() ?: 1).coerceIn(1, 31),
            limitHwid = (limitHwid.toIntOrNull() ?: 0).coerceAtLeast(0),
            expiryTime = expiryTime,
            tgId = tgId.toLongOrNull() ?: 0L,
            group = group.trim(),
            comment = comment.trim(),
        ).withRenewal(
            reset = reset.toIntOrNull() ?: 0,
            // 3.7.0: a day of 0 keeps the old rolling interval, so the two
            // coexist and the panel picks whichever is set.
            resetDay = (resetDay.toIntOrNull() ?: 0).coerceIn(0, 31),
        )
        return base.copy(
            // Mint the subscription id ourselves on create, in the panel's own
            // 16-char format. Sending it empty makes the panel fall back to a
            // uuid, which is what put a UUID-shaped id in the subscription URL.
            subId = if (isNew) randomSubId() else base.subId,
            secret = if (isMtproto) secret.trim() else base.secret,
            adTag = if (isMtproto) adTag.trim() else base.adTag,
            allowedIPs = if (isWireguard) {
                allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                base.allowedIPs
            },
            forwardedPorts = if (isAmneziawg) forwardedPorts.trim() else base.forwardedPorts,
            // A tunnel client sends the field, 0 included (panel v3.8.0 reads it as "off");
            // any other client keeps the rebuilt value: null, or a stored keepalive > 0.
            keepAlive = if (isWireguard || isAmneziawg) {
                (keepAlive.toIntOrNull() ?: 0).coerceIn(0, 65535)
            } else {
                base.keepAlive
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) { Text(tr("Cancel")) }
            Text(if (isNew) tr("New client") else tr("Edit client"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onSave(build(), selectedInbounds.toList()) }, enabled = canSave) { Text(if (saving) "…" else tr("Save")) }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(tr("Email / name")) },
                singleLine = true,
                isError = emailInvalidForMasque,
                supportingText = if (emailInvalidForMasque) {
                    { Text(tr("MASQUE: client email cannot contain \":\"")) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            CField(totalGb, { totalGb = it }, tr("Traffic limit (GB, 0 = unlimited)"), KeyboardType.Decimal)
            CField(limitIp, { limitIp = it.filter(Char::isDigit) }, tr("IP limit (0 = unlimited)"), KeyboardType.Number)
            if (isTuic) {
                Text(
                    tr("On a TUIC inbound the panel can't enforce a per-client traffic limit or IP limit — limit traffic on the TUIC inbound itself."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CField(reset, { reset = it.filter(Char::isDigit) }, tr("Traffic reset period (days, 0 = off)"), KeyboardType.Number)
            if (source.resetWeekday > 0) {
                Text(
                    tr("Weekly renewal is on (set in the web panel). A reset period or a renewal day turns it off."),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // ---- Renewal, reset cycle and devices (panel v3.7.0) ----
            CField(resetDay, { resetDay = it.filter(Char::isDigit).take(2) }, tr("Renew on day (0 = interval)"), KeyboardType.Number)
            CField(resetMax, { resetMax = it.filter(Char::isDigit).take(4) }, tr("Max renewals (0 = ∞)"), KeyboardType.Number)
            Text(tr("Traffic reset cycle"), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TRAFFIC_RESET_CYCLES.forEach { cycle ->
                    FilterChip(
                        selected = trafficReset == cycle,
                        onClick = { trafficReset = cycle },
                        label = { Text(tr(cycle.replaceFirstChar { c -> c.uppercase() })) },
                    )
                }
            }
            if (trafficReset == "monthly") {
                CField(trafficResetDay, { trafficResetDay = it.filter(Char::isDigit).take(2) }, tr("Day"), KeyboardType.Number)
            }
            Column(Modifier.fillMaxWidth()) {
                Text(tr("Expiry"), style = MaterialTheme.typography.labelMedium)
                Text(
                    if (expiryTime > 0) formatDateTime(expiryTime) else tr("Never"),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (expiryTime > 0) {
                    // Whose clock this is, spelled out: the panel may well sit
                    // in another zone than the device it is managed from.
                    Text(
                        localZoneLabel(expiryTime) + " · " + tr("your phone"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    formatDateTimeInZone(expiryTime, panelTimeZone)?.let { onPanel ->
                        Text(
                            tr("On the panel") + ": " + onPanel + " (" + panelTimeZone + ")",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Own line for the buttons: sharing a Row with this column squeezed it to a
                // sliver once "Never" appeared, and the wrapped text blew the row up.
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (expiryTime > 0) {
                        OutlinedButton(onClick = { expiryTime = 0 }) { Text(tr("Never")) }
                    }
                    Button(onClick = { showDatePicker = true }) { Text(tr("Pick date & time")) }
                }
            }
            CField(limitHwid, { limitHwid = it.filter(Char::isDigit).take(4) }, tr("Device limit (0 = unlimited)"), KeyboardType.Number)
            if (isAmneziawg) {
                CField(forwardedPorts, { forwardedPorts = it }, tr("Forwarded ports"))
            }
            CField(tgId, { tgId = it.filter(Char::isDigit) }, tr("Telegram ID (optional)"), KeyboardType.Number)
            CField(group, { group = it }, tr("Group (optional)"))
            // Pick from groups already in use (avoids typos that split a group in two).
            val groupSuggestions = availableGroups.filter { it.isNotBlank() && it != group.trim() }
            if (groupSuggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupSuggestions.forEach { g ->
                        FilterChip(selected = false, onClick = { group = g }, label = { Text(g) })
                    }
                }
            }
            CField(comment, { comment = it }, tr("Comment (optional)"))
            CToggle(tr("Enabled"), enable) { enable = it }

            // Inbound membership — editable for both new and existing clients.
            Text(tr("Attach to inbounds"), style = MaterialTheme.typography.labelMedium)
            availableInbounds.forEach { ib ->
                val on = ib.id in selectedInbounds
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedInbounds = if (on) selectedInbounds - ib.id else selectedInbounds + ib.id
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = {
                        selectedInbounds = if (it) selectedInbounds + ib.id else selectedInbounds - ib.id
                    })
                    Text(ib.remark.ifBlank { "inbound #${ib.id}" } + "  ·  ${ib.protocol.uppercase()}:${ib.port}")
                }
            }

            if (isMtproto) {
                Text(tr("MTProto"), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = secret,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(tr("MTProto secret")) },
                    trailingIcon = {
                        TextButton(onClick = {
                            val domain = mtprotoSecretDomain(secret).ifBlank { "www.cloudflare.com" }
                            secret = "ee" + randomHex(16) + domain.encodeToByteArray().toHex()
                        }) { Text(tr("Regenerate")) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = adTag,
                    onValueChange = { v -> adTag = v.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(32) },
                    label = { Text(tr("Ad-tag (sponsored channel, 32 hex)")) },
                    isError = adTag.isNotEmpty() && adTag.length != 32,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isWireguard) {
                OutlinedTextField(
                    value = allowedIps,
                    onValueChange = { allowedIps = it },
                    label = { Text(tr("WireGuard allowed IPs")) },
                    supportingText = { Text(tr("Comma-separated; leave empty to auto-assign.")) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isWireguard || isAmneziawg) {
                OutlinedTextField(
                    value = keepAlive,
                    onValueChange = { v -> keepAlive = v.filter(Char::isDigit).take(5) },
                    label = { Text(tr("Keepalive (seconds)")) },
                    supportingText = { Text(tr("How often the client sends a keepalive. 25 keeps NAT open and brings an idle peer back after any drop; 0 turns it off.")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showDatePicker) {
                ExpiryDatePickerDialog(
                    initialMillis = expiryTime.takeIf { it > 0 },
                    onPick = { showDatePicker = false; pendingDay = it },
                    onDismiss = { showDatePicker = false },
                )
            }
            pendingDay?.let { day ->
                ExpiryTimePickerDialog(
                    initialMillis = expiryTime.takeIf { it > 0 },
                    onPick = { h, m -> expiryTime = combineDateAndTime(day, h, m); pendingDay = null },
                    onDismiss = { pendingDay = null },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun gbStr(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes % 1_073_741_824L == 0L -> (bytes / 1_073_741_824L).toString()
    else -> ((bytes * 100 / 1_073_741_824L) / 100.0).toString()
}

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(digits[v ushr 4])
        sb.append(digits[v and 0xF])
    }
    return sb.toString()
}

private fun randomHex(bytes: Int): String {
    val b = ByteArray(bytes)
    kotlin.random.Random.nextBytes(b)
    return b.toHex()
}

/** Fronting domain encoded in a FakeTLS secret ("ee" + 16 bytes + domain hex),
 *  or "" if the secret is empty/malformed. */
private fun mtprotoSecretDomain(secret: String): String {
    var s = secret
    if (s.startsWith("ee") || s.startsWith("dd")) s = s.substring(2)
    if (s.length <= 32) return ""
    return runCatching {
        s.substring(32).chunked(2).map { (it.toInt(16) and 0xFF).toByte() }.toByteArray().decodeToString()
    }.getOrDefault("")
}

@Composable
private fun CField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


/**
 * A subscription id in the panel's own format: 16 lowercase letters/digits,
 * matching the web UI's `RandomUtil.randomLowerAndNum(16)`. Sent on create so
 * an app-made client gets the same kind of subId as a panel-made one — left
 * empty, the panel falls back to a raw `uuid.NewString()`, which puts a
 * UUID-shaped id in the subscription URL (reported as GitHub issue #3).
 */
private fun randomSubId(len: Int = 16): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(len) { repeat(len) { append(alphabet[secureRandomIndex(alphabet.length)]) } }
}

/** Panel-side cycles for a client's own traffic reset (panel v3.7.0). */
private val TRAFFIC_RESET_CYCLES = listOf("never", "hourly", "daily", "weekly", "monthly")


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePickerDialog(initialMillis: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onPick) }) { Text(tr("OK")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    ) { DatePicker(state = pickerState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryTimePickerDialog(initialMillis: Long?, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    // An expiry the operator hasn't timed yet ends the day rather than starting
    // it: 23:59 is what "expires on that date" means to a customer.
    val (hour, minute) = if (initialMillis != null && initialMillis > 0) localHourMinute(initialMillis) else 23 to 59
    val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Expiry time")) },
        text = { TimePicker(state = pickerState) },
        confirmButton = { TextButton(onClick = { onPick(pickerState.hour, pickerState.minute) }) { Text(tr("OK")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
    )
}
