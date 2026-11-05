package com.payup.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.payup.domain.Currency
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.payup.PayUpApp
import com.payup.R
import com.payup.data.GameRepository
import com.payup.domain.Money
import com.payup.domain.MoneyFormat
import kotlinx.coroutines.delay

/** Creates a screen ViewModel wired to the app's repository and the screen's navigation arguments. */
@Composable
inline fun <reified VM : ViewModel> payUpViewModel(
    crossinline create: (GameRepository, SavedStateHandle) -> VM,
): VM = viewModel(factory = viewModelFactory {
    initializer { create((this[APPLICATION_KEY] as PayUpApp).repository, createSavedStateHandle()) }
})

/**
 * The currency of the game (or screen) being shown; set once per screen with [ProvideCurrency] so every amount
 * picks it up. There is no default: a screen that shows money without one fails loudly instead of showing ₪.
 */
val LocalCurrency = compositionLocalOf<Currency> { error("No currency provided: wrap this screen in ProvideCurrency") }

/** Shows [content] with amounts in [currency], given as the game's stored code. */
@Composable
fun ProvideCurrency(code: String?, content: @Composable () -> Unit) =
    ProvideCurrency(Currency.fromCode(code), content)

@Composable
fun ProvideCurrency(currency: Currency, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalCurrency provides currency, content = content)

/** Formats an amount in agorot with the current currency: ₪50, ₪18.5, −₪3.25. */
@Composable
fun money(amount: Int): String = moneyText(LocalContext.current, amount, LocalCurrency.current)

@Composable
fun signedMoney(amount: Int): String = moneyText(LocalContext.current, amount, LocalCurrency.current, showPlus = true)

/** Same as [money] for code outside composition (snackbars, sharing); [showPlus] marks a positive amount with "+". */
fun moneyText(context: android.content.Context, amount: Int, currency: Currency, showPlus: Boolean = false): String {
    return MoneyFormat.format(amount, currency, context.isRtl(), showPlus)
}

/** True when the app's current language is written right to left (Hebrew, Arabic). */
fun android.content.Context.isRtl(): Boolean =
    resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL

/** Leaderboard amounts are summed across games without converting, so they show the latest game's currency. */
@Composable
fun WithLatestCurrency(content: @Composable () -> Unit) {
    val repository = (LocalContext.current.applicationContext as PayUpApp).repository
    val currency by repository.latestCurrency.collectAsStateWithLifecycle(null)
    currency?.let { ProvideCurrency(it, content) }
}

/** Keyboard for typing money: digits plus a decimal point. */
val MoneyKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayUpTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
fun AmountDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    initial: Int? = null,
) {
    var text by remember { mutableStateOf(initial?.let(Money::format).orEmpty()) }
    val amount = Money.parseAgorot(text) ?: 0
    TextInputDialog(
        title = title,
        value = text,
        onValueChange = { text = Money.sanitizeInput(it) },
        label = stringResource(R.string.amount),
        keyboardOptions = MoneyKeyboard,
        confirmLabel = confirmLabel,
        confirmEnabled = amount > 0,
        onConfirm = { onConfirm(amount) },
        onDismiss = onDismiss,
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    TextInputDialog(
        title = title,
        value = text,
        onValueChange = { text = it.take(30) },
        label = stringResource(R.string.player_name),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        confirmLabel = confirmLabel,
        confirmEnabled = text.isNotBlank(),
        onConfirm = { onConfirm(text.trim()) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
    LaunchedEffect(Unit) {
        delay(150) // let the dialog attach before asking for focus
        runCatching { focus.requestFocus() }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
