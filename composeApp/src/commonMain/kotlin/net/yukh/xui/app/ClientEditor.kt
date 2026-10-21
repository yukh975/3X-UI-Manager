package net.yukh.xui.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.QrCodePainter
import io.github.alexzhirkevich.qrose.options.QrOptions
import net.yukh.xui.shared.dto.Client
import net.yukh.xui.shared.dto.ClientIpInfo
import net.yukh.xui.shared.dto.ClientModel
import net.yukh.xui.shared.dto.HAPP_SOURCE_TOO_LONG
import net.yukh.xui.shared.dto.InboundSlim
import net.yukh.xui.shared.dto.QR_MAX_BYTES
import net.yukh.xui.shared.dto.SubInfo
import net.yukh.xui.shared.dto.isPostQuantumLink
import net.yukh.xui.shared.dto.masqueEmailInvalid
import net.yukh.xui.shared.dto.withRenewal
import net.yukh.xui.shared.secureRandomIndex

private const val GBC = 1_073_741_824.0

/** Edit a client's basic fields + inbound membership + view its connection links
 *  + delete. Mirrors the Android client editor. The inbound multi-select shows for
 *  both new and existing clients; on save the caller reconciles the membership
 *  (attach the newly-checked inbounds, detach the unchecked ones). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClientEditorScreen(
    source: Client,
    isNew: Boolean,
    availableInbounds: List<InboundSlim>,
    availableGroups: List<String> = emptyList(),
    saving: Boolean,
    error: String?,
    links: List<String>,
    linksLoading: Boolean,
    subUrl: String? = null,
    /** JSON subscription URL (null when the panel doesn't serve one): MASQUE is only there. */
    jsonSubUrl: String? = null,
    subInfo: SubInfo? = null,
    onShowLinks: () -> Unit,
    ips: List<ClientIpInfo>,
    ipsLoading: Boolean,
    ipsLoaded: Boolean = false,
    onShowIps: () -> Unit,
    onClearIps: () -> Unit,
    onShowDevices: () -> Unit = {},
    /** The panel's own time zone, when it names a real one — shown next to the
     *  expiry so the two clocks are never confused. */
    panelTimeZone: String = "",
    // Encrypted Happ link under the subscription (panel v3.8.0); a null switch means
    // the panel's setting hasn't been read, so the block isn't offered.
    happLinkEnabled: Boolean? = null,
    happLink: String? = null,
    happLinkLoading: Boolean = false,
    happLinkError: String? = null,
    onGenerateHappLink: () -> Unit = {},
    onSave: (ClientModel, List<Int>) -> Unit,
    onDelete: () -> Unit,
    onResetTraffic: () -> Unit,
    onCancel: () -> Unit,
) {
    // Pre-select the client's current inbound membership (empty for a new client).
    var selectedInbounds by remember { mutableStateOf(source.inboundIds.toSet()) }
    var email by remember { mutableStateOf(source.email) }
    var confirmReset by remember { mutableStateOf(false) }
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
            if (!isNew) {
                OutlinedButton(onClick = onShowDevices, modifier = Modifier.fillMaxWidth()) { Text(tr("Devices")) }
            }
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

            if (!isNew) {
                OutlinedButton(onClick = onShowLinks, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Show connection links"))
                }
                if (linksLoading) Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Subscription — one auto-updating link for all of the client's
                // configs (resolved from panel settings; null on old panels).
                subUrl?.let { sub ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(tr("Subscription"), style = MaterialTheme.typography.titleMedium)
                            Text(
                                tr("Auto-updating link for all configs"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            QrImage(sub)
                            SelectionContainer { Text(sub, style = MaterialTheme.typography.bodySmall) }
                            LinkActions(sub)
                            subInfo?.let { SubStatusRow(it) }
                            jsonSubUrl?.let { JsonSubBlock(it) }
                            if (happLinkEnabled != null) {
                                HappLinkBlock(
                                    enabled = happLinkEnabled,
                                    link = happLink,
                                    loading = happLinkLoading,
                                    error = happLinkError,
                                    onGenerate = onGenerateHappLink,
                                )
                            }
                        }
                    }
                }
                links.forEach { link ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QrImage(link)
                            SelectionContainer { Text(link, style = MaterialTheme.typography.bodySmall) }
                            LinkActions(link)
                        }
                    }
                }
                // Per-client IP log (panel v3.4): list the source IPs the panel
                // recorded for this email, with a clear-log action.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onShowIps, modifier = Modifier.weight(1f)) {
                        Text(tr("IP log"))
                    }
                    if (ips.isNotEmpty()) {
                        OutlinedButton(onClick = onClearIps) { Text(tr("Clear")) }
                    }
                }
                if (ipsLoading) Text(tr("Loading…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ipsLoaded && !ipsLoading && ips.isEmpty()) {
                    Text(tr("No IPs logged."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        tr("The panel keeps an address for 30 minutes after the last connection, so this stays empty for anyone who hasn't connected recently."),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ips.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ips.forEach { rec ->
                                val suffix = listOfNotNull(
                                    rec.node.ifBlank { null },
                                    rec.time.ifBlank { null },
                                ).joinToString(" · ")
                                Text(
                                    if (suffix.isBlank()) rec.ip else "${rec.ip}  ·  $suffix",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
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

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmReset = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Reset traffic"))
                }
                Button(
                    onClick = onDelete,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(tr("Delete client")) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(tr("Reset this client's traffic?")) },
            text = { Text("${source.email}\n\n${tr("Used traffic starts from zero, and a client its quota switched off is enabled again.")}") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetTraffic() }) {
                    Text(tr("Reset traffic"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(tr("Cancel")) } },
        )
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

/** Copy / system-share actions under a QR link card. */
@Composable
private fun LinkActions(content: String) {
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(content)) }) {
            Text(tr("Copy"))
        }
        OutlinedButton(onClick = { platformShareText(content) }) {
            Text(tr("Share"))
        }
    }
}

/** QR code for [content]. qrose encodes in the painter's constructor and throws when the
 *  data exceeds a QR code's capacity, so the painter is built inside a catch and a link
 *  that doesn't fit gets the copy/share note instead of crashing the screen. */
@Composable
private fun QrImage(content: String) {
    // Post-quantum keys fit a QR only at a density phones can't scan; the panel hides it too.
    if (isPostQuantumLink(content)) {
        Text(
            tr("This link carries post-quantum keys and is too dense for a QR code — copy or share it instead."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val painter = remember(content) { runCatching { QrCodePainter(content, QrOptions {}) }.getOrNull() }
    if (painter != null) {
        Image(painter = painter, contentDescription = null, modifier = Modifier.size(200.dp))
    } else {
        QrTooLongNote()
    }
}

@Composable
private fun QrTooLongNote() {
    Text(
        tr("Too long for a QR code — copy or share the link instead."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The JSON subscription (Xray JSON configs); MASQUE is only offered there (panel 3.10.0). */
@Composable
private fun JsonSubBlock(url: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(tr("JSON subscription"), style = MaterialTheme.typography.titleSmall)
        Text(
            tr("Xray JSON configs. MASQUE connections are only in this one."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QrImage(url)
        SelectionContainer { Text(url, style = MaterialTheme.typography.bodySmall) }
        LinkActions(url)
    }
}

/** Encrypted Happ link for the subscription (panel v3.8.0). The panel builds it on
 *  demand; the switch that allows it lives in the panel's subscription settings. */
@Composable
private fun HappLinkBlock(
    enabled: Boolean,
    link: String?,
    loading: Boolean,
    error: String?,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(tr("Encrypted Happ link"), style = MaterialTheme.typography.titleSmall)
        val errorText = error?.let {
            if (it == HAPP_SOURCE_TOO_LONG) tr("The subscription URL is longer than the panel allows for Happ links (8192 bytes).")
            else "${tr("Couldn't create the Happ link")}: $it"
        }
        when {
            !enabled -> Text(
                tr("Turned off on the panel. Enable \"Encrypted subscription links\" in the panel's settings: Subscription → Happ → Subscription links."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            link != null -> {
                // The link stays valid at any length; past QR level M's capacity it is offered
                // for copy/share only, the same as when the encoder refuses it.
                if (link.encodeToByteArray().size <= QR_MAX_BYTES) QrImage(link) else QrTooLongNote()
                SelectionContainer {
                    Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                LinkActions(link)
                Text(
                    tr("Only the Happ app opens this link, but anyone who has it may still recover or share the subscription URL."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A failed regenerate keeps showing the previous link, so name the failure too.
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onGenerate) { Text(tr("Regenerate")) }
            }
            else -> {
                errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onGenerate) {
                    Text(if (error != null) tr("Retry") else tr("Create encrypted link"))
                }
            }
        }
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

/** What the subscriber's own client app shows when it refreshes the link. */
@Composable
private fun SubStatusRow(info: SubInfo) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            tr("As the subscriber sees it"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (info.isOnline) Color(0xFF34C759)
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
            )
            Text(
                if (info.isOnline) tr("Online") else tr("Offline"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val usage = listOfNotNull(
            info.used.takeIf { it.isNotBlank() },
            info.total.takeIf { it.isNotBlank() }?.let { "${tr("of")} $it" },
        ).joinToString(" ")
        if (usage.isNotBlank()) {
            Text(
                usage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
