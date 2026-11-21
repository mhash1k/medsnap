@file:OptIn(ExperimentalMaterial3Api::class)

package com.pharmabill.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pharmabill.app.R
import com.pharmabill.app.ui.theme.Pb

/* ---------- Top bars ---------- */

/** Sub-screen app bar in the reference style (flat, page-coloured). Same slots as [TopAppBar]. */
@Composable
fun PbTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier) {
        TopAppBar(
            title = { ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() } },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Pb.bg,
                scrolledContainerColor = Pb.bg,
                titleContentColor = Pb.ink,
                navigationIconContentColor = Pb.iconInk,
                actionIconContentColor = Pb.iconInk,
            ),
        )
        HorizontalDivider(color = Pb.line.copy(alpha = 0.65f))
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
}

/** Brand bar shown above the four main tabs: logo tile, app name, shop name, two round icon buttons. */
@Composable
fun BrandTopBar(subtitle: String, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = Pb.bg) {
        Column {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandMark(38.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = Pb.ink)
                    Text(subtitle, fontSize = 11.sp, color = Pb.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                actions()
            }
            HorizontalDivider(color = Pb.line.copy(alpha = 0.65f))
        }
    }
}

/** Blue cross in a soft rounded tile — the app logo. */
@Composable
fun BrandMark(size: Dp) {
    Box(Modifier.size(size).background(Pb.primarySoft, RoundedCornerShape(size * 0.34f)), contentAlignment = Alignment.Center) {
        Text("✚", color = Pb.primary, fontSize = (size.value * 0.55f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(icon, description, tint = Pb.iconInk, modifier = Modifier.size(22.dp)) }
}

/* ---------- Page structure ---------- */

/** Big page title with grey eyebrow and subtitle, optional trailing element (pill, button). */
@Composable
fun PageHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, eyebrow: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 17.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow ?: stringResource(R.string.app_name).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = Pb.muted)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Pb.ink)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, fontSize = 13.sp, lineHeight = 19.sp, color = Pb.muted)
            }
        }
        trailing()
    }
}

/** Small white rounded pill, e.g. today's date. */
@Composable
fun DatePill(text: String) {
    Text(
        text,
        Modifier.background(Color.White, CircleShape).border(1.dp, Pb.line, CircleShape).padding(horizontal = 11.dp, vertical = 8.dp),
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF596273), maxLines = 1,
    )
}

/** Title row above a block, with an optional text action on the right ("View all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Pb.ink)
        if (count != null) Text(" ($count)", style = MaterialTheme.typography.titleSmall, color = Pb.muted)
        Spacer(Modifier.weight(1f))
        if (action != null) PbTextButton(action, onAction)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier.padding(start = 2.dp, top = 18.dp, bottom = 9.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp, color = Pb.muted)
}

/* ---------- Cards ---------- */

/** White rounded card with a hairline border (reference `.card`). */
@Composable
fun PbCard(
    modifier: Modifier = Modifier,
    container: Color = Color.White,
    border: Color = Pb.line,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth().clip(shape).background(container).border(1.dp, border, shape)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(contentPadding), content = content)
}

/** Card holding a list of [ItemRow]s (less vertical padding, rows draw their own dividers). */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    PbCard(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp), content = content)

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp, color = Pb.ink)
        if (sub != null) Text(sub, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Pb.muted, lineHeight = 17.sp)
    }
}

/** Info / warning tinted card with an icon tile. */
@Composable
fun NoticeCard(title: String, text: String, icon: ImageVector, warn: Boolean = false, modifier: Modifier = Modifier) {
    PbCard(modifier, container = if (warn) Pb.warnBg else Pb.infoBg, border = if (warn) Pb.warnBorder else Pb.infoBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon, bg = if (warn) Color(0xFFFFF0C7) else Pb.surface2, tint = if (warn) Pb.yellow else Pb.primary, size = 32.dp, radius = 11.dp)
            CardTitle(title, Modifier.weight(1f), sub = text)
        }
    }
}

/** Rounded square with an icon in the middle. */
@Composable
fun IconTile(icon: ImageVector, bg: Color = Pb.surface2, tint: Color = Pb.primary, size: Dp = 32.dp, radius: Dp = 11.dp, iconSize: Dp = size * 0.56f) {
    Box(Modifier.size(size).background(bg, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun TextTile(text: String, bg: Color = Pb.medIconBg, tint: Color = Pb.medIcon, size: Dp = 42.dp, radius: Dp = 14.dp) {
    Box(Modifier.size(size).background(bg, RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        Text(text, color = tint, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
    }
}

/** Stat tile: icon, big value, label and an optional coloured footnote. */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    foot: String? = null,
    footColor: Color = Pb.muted,
    valueColor: Color = Pb.ink,
    onClick: (() -> Unit)? = null,
) {
    PbCard(modifier, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(14.dp), onClick = onClick) {
        if (icon != null) {
            IconTile(icon)
            Spacer(Modifier.height(10.dp))
        } else {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Pb.muted, letterSpacing = 0.3.sp)
        }
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) Text(label, Modifier.padding(top = 3.dp), fontSize = 11.sp, color = Pb.muted)
        if (foot != null) Text(foot, Modifier.padding(top = 7.dp), fontSize = 11.sp, color = footColor)
    }
}

/** Slim rounded progress bar. */
@Composable
fun SlimProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = Pb.primary) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(9.dp)),
        color = color,
        trackColor = Color(0xFFEDF1F6),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/* ---------- Rows ---------- */

/**
 * List row from the reference: leading tile, bold title, grey meta line, trailing column.
 * Draws a light divider under itself unless [last].
 */
@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
    last: Boolean = false,
    titleSuffix: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (!titleSuffix.isNullOrBlank()) Text(" $titleSuffix", fontSize = 14.sp, color = Color(0xFF5F6368), maxLines = 1)
                }
                if (!meta.isNullOrBlank()) Text(meta, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Pb.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                below()
            }
            trailing()
        }
        if (!last) HorizontalDivider(color = Pb.lineSoft)
    }
}

/** Right-aligned amount with a small grey line under it. */
@Composable
fun PriceColumn(value: String, sub: String? = null, valueColor: Color = Pb.ink, subColor: Color = Pb.muted) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        if (sub != null) Text(sub, Modifier.padding(top = 3.dp), fontSize = 11.sp, color = subColor, maxLines = 1)
    }
}

/* ---------- Badges ---------- */

enum class Tone { GREEN, YELLOW, RED, BLUE, GREY }

@Composable
fun PillBadge(text: String, tone: Tone, modifier: Modifier = Modifier, dot: Boolean = false) {
    val (bg, fg) = when (tone) {
        Tone.GREEN -> Pb.greenSoft to Pb.green
        Tone.YELLOW -> Pb.yellowSoft to Pb.yellow
        Tone.RED -> Pb.redSoft to Pb.red
        Tone.BLUE -> Pb.primarySoft to Pb.primary
        Tone.GREY -> Pb.surface2 to Pb.tonalInk
    }
    Row(modifier.background(bg, CircleShape).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Box(Modifier.size(6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/* ---------- Home hero blocks ---------- */

@Composable
fun VoiceHero(title: String, subtitle: String, listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector, actionIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 78.dp).clip(shape)
            .background(if (listening) Color(0xFFDCEAFF) else Pb.voiceBg)
            .border(1.dp, Pb.voiceBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).background(if (listening) Pb.red else Pb.primary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp, color = Pb.voiceInk)
            Text(subtitle, Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = Pb.voiceSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).background(Pb.voiceAction, CircleShape), contentAlignment = Alignment.Center) {
            Icon(actionIcon, null, tint = Pb.voiceInk, modifier = Modifier.size(18.dp))
        }
    }
}

/** Big blue action tile ("Scan medicines"). */
@Composable
fun ActionPrimary(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 136.dp),
        shape = RoundedCornerShape(22.dp),
        color = Pb.primary,
        contentColor = Color.White,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.17f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp))
            }
            Spacer(Modifier.height(22.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f), lineHeight = 15.sp)
            }
        }
    }
}

/** Small white action tile beside the primary one. */
@Composable
fun ActionSmall(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Pb.line, shape).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, bg = Pb.primarySoft, tint = Pb.primary, size = 36.dp, radius = 12.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pb.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, Modifier.padding(top = 2.dp), fontSize = 11.sp, color = Pb.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---------- Chips, segments, search ---------- */

@Composable
fun DraftPill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(CircleShape).background(if (active) Pb.primarySoft else Pb.surface2).clickable(onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 11.dp),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (active) Pb.primary else Pb.iconInk, maxLines = 1,
    )
}

/** iOS-like segmented control from the reference (`.segment`). */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(Pb.surface2, RoundedCornerShape(13.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.background(Color.White) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Pb.primary else Color(0xFF697386), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Rounded white search field (`.searchbox`). */
@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.fillMaxWidth().height(50.dp).background(Color.White, shape).border(1.dp, Pb.line, shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = Color(0xFF7B8492), modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, fontSize = 14.sp, color = Color(0xFF98A1AE), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Pb.ink),
                cursorBrush = SolidColor(Pb.primary),
            )
        }
    }
}

/** Search-looking button that opens a picker. */
@Composable
fun SearchLauncher(placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(shape).background(Color.White).border(1.dp, Pb.line, shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = Color(0xFF7B8492), modifier = Modifier.size(20.dp))
        Text(placeholder, fontSize = 14.sp, color = Color(0xFF98A1AE), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------- Settings-style list ---------- */

@Composable
fun MoreGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Pb.line, shape), content = content)
}

@Composable
fun MoreItem(icon: ImageVector, title: String, desc: String?, onClick: () -> Unit, last: Boolean = false, trailing: @Composable () -> Unit = {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF98A1AE))
}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, bg = Pb.surface2, tint = Color(0xFF536274), size = 38.dp, radius = 13.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Pb.ink)
            if (!desc.isNullOrBlank()) Text(desc, Modifier.padding(top = 3.dp), fontSize = 12.sp, color = Pb.muted)
        }
        trailing()
    }
    if (!last) HorizontalDivider(color = Pb.lineSoft)
}

/* ---------- Buttons ---------- */

/** Corner radius for every button in the app (reference `.btn`). */
val PbBtnShape = RoundedCornerShape(14.dp)
private val BtnShape = PbBtnShape

@Composable
fun PbButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, tone: Tone = Tone.BLUE, filled: Boolean = true) {
    val colors = when {
        filled && tone == Tone.BLUE -> ButtonDefaults.buttonColors(containerColor = Pb.primary, contentColor = Color.White)
        tone == Tone.RED -> ButtonDefaults.buttonColors(containerColor = Pb.redSoft, contentColor = Pb.red)
        tone == Tone.GREY -> ButtonDefaults.buttonColors(containerColor = Pb.surface2, contentColor = Pb.tonalInk)
        tone == Tone.GREEN -> ButtonDefaults.buttonColors(containerColor = Pb.green, contentColor = Color.White)
        tone == Tone.YELLOW -> ButtonDefaults.buttonColors(containerColor = Pb.yellowSoft, contentColor = Pb.yellow)
        else -> ButtonDefaults.buttonColors(containerColor = Pb.primarySoft, contentColor = Pb.primary)
    }
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = BtnShape, colors = colors,
        elevation = if (filled && tone == Tone.BLUE) ButtonDefaults.buttonElevation(defaultElevation = 1.dp) else ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Light-blue secondary button (reference `.btn.secondary`). */
@Composable
fun PbSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) =
    PbButton(text, onClick, modifier, icon, enabled, Tone.BLUE, filled = false)

/** Small grey pill button (reference `.softbtn`). */
@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Pb.surface2).clickable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = Pb.tonalInk)
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pb.tonalInk, maxLines = 1)
    }
}

@Composable
fun PbTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pb.primary, icon: ImageVector? = null) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/* ---------- Empty state ---------- */

@Composable
fun EmptyBlock(title: String, text: String? = null, icon: ImageVector? = null, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            IconTile(icon, bg = Pb.surface2, tint = Color(0xFF7B8796), size = 54.dp, radius = 18.dp, iconSize = 26.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pb.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (text != null) Text(text, Modifier.padding(top = 5.dp, bottom = 10.dp).width(260.dp), fontSize = 13.sp, lineHeight = 19.sp, color = Pb.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action()
    }
}

/** One row of a white card split across several LazyColumn items (rounded only at the ends). */
fun Modifier.groupItem(first: Boolean, last: Boolean): Modifier {
    val r = 20.dp
    val shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp, bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp)
    return this.fillMaxWidth().clip(shape).background(Color.White).padding(start = 16.dp, end = 16.dp, top = if (first) 3.dp else 0.dp, bottom = if (last) 3.dp else 0.dp)
}

/** For code that still uses Material [androidx.compose.material3.Card]: white fill + hairline border. */
val PbCardBorder = androidx.compose.foundation.BorderStroke(1.dp, Pb.line)
val PbCardShape = RoundedCornerShape(20.dp)

@Composable
fun pbCardColors(container: Color = Color.White) = androidx.compose.material3.CardDefaults.cardColors(containerColor = container)

/** Standard page padding used by every screen (reference: 18dp sides). */
val PagePadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp)

/* ---------- Bangladeshi phone number ---------- */

/**
 * Phone field that only takes a Bangladeshi mobile number: digits only (Bangla digits converted),
 * "+88" shown in front, capped at 11 digits, with a live hint or error under it.
 */
@Composable
fun BdPhoneField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.shop_phone)) {
    val problem = com.pharmabill.app.util.BdPhone.problem(value)
    val showError = problem == com.pharmabill.app.util.BdPhone.Problem.NOT_01 || problem == com.pharmabill.app.util.BdPhone.Problem.BAD_OPERATOR
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { onChange(com.pharmabill.app.util.BdPhone.clean(it)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("+88 ", color = Pb.muted) },
        placeholder = { Text("01XXXXXXXXX") },
        singleLine = true,
        isError = showError,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
        supportingText = {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    when (problem) {
                        com.pharmabill.app.util.BdPhone.Problem.NOT_01 -> stringResource(R.string.phone_err_01)
                        com.pharmabill.app.util.BdPhone.Problem.BAD_OPERATOR -> stringResource(R.string.phone_err_operator)
                        null -> stringResource(R.string.phone_ok)
                        else -> stringResource(R.string.phone_hint)
                    },
                    Modifier.weight(1f),
                    color = when {
                        showError -> Pb.red
                        problem == null -> Pb.green
                        else -> Pb.muted
                    },
                )
                Text("${value.length}/${com.pharmabill.app.util.BdPhone.LENGTH}", color = Pb.muted)
            }
        },
    )
}
