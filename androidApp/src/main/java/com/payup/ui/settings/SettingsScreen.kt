package com.payup.ui.settings

import org.jetbrains.compose.resources.StringResource
import com.payup.resources.Res
import com.payup.resources.backup_summary
import com.payup.resources.backup_title
import com.payup.resources.color_theme
import com.payup.resources.language
import com.payup.resources.settings
import com.payup.resources.system_default
import com.payup.resources.theme_color_blind
import com.payup.resources.theme_dark
import com.payup.resources.theme_original
import com.payup.resources.theme_rich
import com.payup.resources.theme_white_black
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.payup.PayUpApp
import com.payup.ui.common.PayUpTopBar
import com.payup.domain.AppLanguage
import com.payup.domain.AppTheme
import com.payup.ui.theme.Gold
import com.payup.ui.theme.palette

private fun AppTheme.label(): StringResource = when (this) {
    AppTheme.ORIGINAL -> Res.string.theme_original
    AppTheme.COLOR_BLIND -> Res.string.theme_color_blind
    AppTheme.RICH -> Res.string.theme_rich
    AppTheme.WHITE_BLACK -> Res.string.theme_white_black
    AppTheme.DARK -> Res.string.theme_dark
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val store = (LocalContext.current.applicationContext as PayUpApp).themeStore
    val selected by store.theme.collectAsStateWithLifecycle()

    Scaffold(topBar = { PayUpTopBar(stringResource(Res.string.settings), onBack) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Both sections start collapsed; the header shows what is currently chosen.
            CollapsibleSection(stringResource(Res.string.color_theme), stringResource(selected.label())) {
                AppTheme.entries.forEach { theme ->
                    ThemeCard(theme, selected = theme == selected, onClick = { store.set(theme) })
                }
            }
            val language = (LocalContext.current.applicationContext as PayUpApp).appLanguage
            var tag by remember { mutableStateOf(language.current()) }
            val systemDefault = stringResource(Res.string.system_default)
            CollapsibleSection(
                stringResource(Res.string.language),
                AppLanguage.OPTIONS.firstOrNull { it.tag == tag }?.nativeName ?: systemDefault,
            ) {
                // Each language is shown in its own script so it can be found whatever the current language is.
                OptionCard(systemDefault, selected = tag == "") {
                    tag = ""
                    language.set("")
                }
                AppLanguage.OPTIONS.forEach { (code, name) ->
                    OptionCard(name, selected = tag == code) {
                        tag = code
                        language.set(code)
                    }
                }
            }
            CollapsibleSection(stringResource(Res.string.backup_title), stringResource(Res.string.backup_summary)) {
                BackupActions()
            }
            Spacer(Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun CollapsibleSection(title: String, current: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(current, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
    AnimatedVisibility(expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun OptionCard(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = if (selected) BorderStroke(2.dp, Gold) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ThemeCard(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val p = theme.palette()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = if (selected) BorderStroke(2.dp, Gold) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(theme.label()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // A tiny preview: background, card, accent, winner and loser colors.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(p.feltDark, p.felt, p.gold, p.win, p.lose).forEach { Swatch(it) }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Spacer(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}
