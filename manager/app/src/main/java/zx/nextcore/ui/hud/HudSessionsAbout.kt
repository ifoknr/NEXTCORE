package zx.nextcore.ui.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import zx.nextcore.R
import zx.nextcore.ui.navigation.safePopBackStack
import zx.nextcore.ui.theme.BrandFontFamily
import zx.nextcore.ui.util.PerfData
import java.text.DateFormat
import java.util.Date

/** NextCore chip logo. */
@Composable
fun NcLogo(size: Dp) {
    Image(painterResource(R.drawable.avatar), "NextCore", Modifier.size(size).clip(Hud.tileShape))
}

/* ---------------- Sessions ---------------- */

@Composable
fun HudSessionsScreen(navController: NavController) {
    val context = LocalContext.current
    val perf = rememberPerfSnapshot()
    val totals = PerfData.totals(perf.sessions)
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    HudPage(title = stringResource(R.string.hud_sessions_title), onBack = { navController.safePopBackStack() }) {
        item(key = "note") { HudNote(stringResource(R.string.hud_sessions_note)) }
        if (perf.loaded && perf.sessions.isEmpty()) {
            item(key = "empty") { HudEmpty(stringResource(R.string.hud_sessions_empty)) }
            return@HudPage
        }
        item(key = "h_by_game") { HudSectionTitle(stringResource(R.string.hud_by_game)) }
        item(key = "by_game") {
            HudCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                totals.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Hud.line)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HudAppIcon(t.pkg, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(appLabel(context, t.pkg), color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${t.sessions} × · " + listOfNotNull(t.avgFps?.let { one(it) + " FPS" }, t.maxTempC?.let { one(it) + "°C" }).joinToString(" · "),
                                color = Hud.muted, fontSize = 12.sp,
                            )
                        }
                        Text(duration(t.seconds), color = hudAccent, fontFamily = BrandFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
        item(key = "h_history") { HudSectionTitle(stringResource(R.string.hud_history)) }
        perf.sessions.chunked(20).forEachIndexed { i, chunk ->
            item(key = "history_$i") {
                HudCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    chunk.forEachIndexed { j, s ->
                        if (j > 0) HorizontalDivider(color = Hud.line)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(appLabel(context, s.pkg), color = Hud.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fmt.format(Date(s.start * 1000)), color = Hud.muted, fontSize = 11.sp)
                            }
                            Text(
                                listOfNotNull(duration(s.seconds), s.avgFps?.let { one(it) + " FPS" }, s.avgTempC?.let { one(it) + "°" }).joinToString(" · "),
                                color = Hud.text, fontSize = 12.sp, fontFamily = BrandFontFamily,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- About ---------------- */

@Composable
fun HudAboutScreen(navController: NavController) {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    val licenses = remember {
        runCatching { context.assets.list("licenses")?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    HudPage(title = stringResource(R.string.hud_about_title), onBack = { navController.safePopBackStack() }) {
        item(key = "logo") {
            HudCard(Modifier.fillMaxWidth(), accent = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    NcLogo(88.dp)
                    Spacer(Modifier.height(10.dp))
                    Wordmark()
                    Text(appVersion(context), color = Hud.muted, fontSize = 12.sp, fontFamily = BrandFontFamily)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.hud_about_desc), color = Hud.muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                }
            }
        }
        item(key = "links") {
            HudGroup(rows = listOf<@Composable () -> Unit>(
                { HudRow(stringResource(R.string.hud_channel), icon = Icons.AutoMirrored.Rounded.Send, subtitle = stringResource(R.string.hud_channel_sub), onClick = { openUrl(context, CHANNEL_URL) }) },
                { HudRow(stringResource(R.string.hud_source), icon = Icons.Rounded.Code, subtitle = stringResource(R.string.hud_source_sub), onClick = { openUrl(context, SOURCE_URL) }) },
                { HudRow(stringResource(R.string.hud_licenses), icon = Icons.Rounded.Description, onClick = { showLicenses = !showLicenses }) },
            ))
        }
        if (showLicenses) {
            item(key = "licenses") {
                HudKeyValues(licenses.map { it.substringBefore('-') to it.substringAfter('-').substringBefore('.') } +
                    ("NextCore" to "Apache 2.0"))
            }
        }
        // Credit for the project NextCore is built on, last on the page.
        item(key = "credit") {
            Text(
                stringResource(R.string.hud_credit),
                color = Hud.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
        }
    }
}
