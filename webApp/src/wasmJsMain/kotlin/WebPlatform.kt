import com.payup.data.GameWithDetails
import com.payup.domain.AppLanguage
import com.payup.domain.BackupFile
import com.payup.platform.BackupFiles
import com.payup.platform.DateFormatter
import com.payup.platform.PickedFile
import com.payup.platform.ShareService
import com.payup.resources.Res
import com.payup.resources.copied_to_clipboard
import com.payup.resources.share_chooser
import com.payup.ui.theme.PayUpPalette
import com.russhwolf.settings.Settings
import kotlinx.coroutines.await
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlin.js.JsBoolean

// What the browser does for the platform pieces of the app: dates, the share sheet, file download and pick, language.

/** The browser's first language, like "he" or "en-US". index.html already applied the language chosen in Settings. */
@JsFun("() => (navigator.languages && navigator.languages[0]) || navigator.language || 'en'")
external fun browserLanguage(): String

@JsFun(
    """(ms, locale, kind) => {
        const options = kind === 0 ? { dateStyle: 'medium' }
            : kind === 1 ? { dateStyle: 'medium', timeStyle: 'short' }
            : { timeStyle: 'short' };
        try { return new Intl.DateTimeFormat(locale, options).format(new Date(ms)); }
        catch (e) { return new Date(ms).toLocaleString(); }
    }""",
)
private external fun formatDate(ms: Double, locale: String, kind: Int): String

/** Dates in the browser's own formats, for the language the app is showing. */
class WebDateFormatter : DateFormatter {
    override fun date(millis: Long) = formatDate(millis.toDouble(), browserLanguage(), 0)
    override fun dateTime(millis: Long) = formatDate(millis.toDouble(), browserLanguage(), 1)
    override fun time(millis: Long) = formatDate(millis.toDouble(), browserLanguage(), 2)
}

/** The language picked in Settings is saved in localStorage; index.html applies it at the next load, so picking reloads. */
class WebAppLanguage(private val settings: Settings) : AppLanguage {
    override fun current(): String = settings.getStringOrNull(KEY).orEmpty()

    override fun set(tag: String) {
        if (tag.isEmpty()) settings.remove(KEY) else settings.putString(KEY, tag)
        reloadPage()
    }

    private companion object {
        const val KEY = "language" // also read by index.html
    }
}

@JsFun("() => location.reload()")
private external fun reloadPage()

// "shared", "cancelled", "unsupported" or "failed".
@JsFun(
    """(text) => new Promise(resolve => {
        if (!navigator.share) { resolve('unsupported'); return; }
        navigator.share({ text: text }).then(
            () => resolve('shared'),
            e => resolve(e && e.name === 'AbortError' ? 'cancelled' : 'failed'));
    })""",
)
private external fun nativeShareText(text: String): Promise<JsString>

@JsFun(
    """(text) => new Promise(resolve => {
        if (!navigator.clipboard) { resolve(false); return; }
        navigator.clipboard.writeText(text).then(() => resolve(true), () => resolve(false));
    })""",
)
private external fun copyToClipboard(text: String): Promise<JsBoolean>

/** Shares [data] (text, or base64 when [isBase64]) as a file through the share sheet when the browser can, else says "unsupported". */
@JsFun(
    """(data, isBase64, name, type, title) => new Promise(resolve => {
        let content = data;
        if (isBase64) {
            const bin = atob(data);
            content = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) content[i] = bin.charCodeAt(i);
        }
        const file = new File([content], name, { type: type });
        if (!navigator.share || !navigator.canShare || !navigator.canShare({ files: [file] })) { resolve('unsupported'); return; }
        navigator.share({ files: [file], title: title }).then(
            () => resolve('shared'),
            e => resolve(e && e.name === 'AbortError' ? 'cancelled' : 'failed'));
    })""",
)
private external fun nativeShareFile(data: String, isBase64: Boolean, name: String, type: String, title: String): Promise<JsString>

/** Saves [data] (text, or base64 when [isBase64]) as a downloaded file. */
@JsFun(
    """(data, isBase64, name, type) => {
        let content = data;
        if (isBase64) {
            const bin = atob(data);
            content = new Uint8Array(bin.length);
            for (let i = 0; i < bin.length; i++) content[i] = bin.charCodeAt(i);
        }
        const url = URL.createObjectURL(new Blob([content], { type: type }));
        const a = document.createElement('a');
        a.href = url;
        a.download = name;
        a.style.display = 'none';
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 10000);
    }""",
)
private external fun downloadFile(data: String, isBase64: Boolean, name: String, type: String)

@JsFun(
    """(text) => {
        const d = document.createElement('div');
        d.textContent = text;
        d.style.cssText = 'position:fixed;left:50%;bottom:48px;transform:translateX(-50%);background:#f5f0e1;color:#0b3d2e;' +
            'padding:12px 20px;border-radius:24px;font:16px system-ui,sans-serif;z-index:10;max-width:80%;text-align:center';
        document.body.appendChild(d);
        setTimeout(() => d.remove(), 2500);
    }""",
)
private external fun showToast(text: String)

// "C" cancelled, "U" unreadable or too big, "T" followed by the file's text.
@JsFun(
    """(maxBytes) => new Promise(resolve => {
        const input = document.createElement('input');
        input.type = 'file';
        input.style.display = 'none';
        let done = false;
        const finish = value => { if (!done) { done = true; input.remove(); resolve(value); } };
        input.addEventListener('change', () => {
            const file = input.files && input.files[0];
            if (!file) { finish('C'); return; }
            if (file.size > maxBytes) { finish('U'); return; }
            file.text().then(text => finish('T' + text), () => finish('U'));
        });
        input.addEventListener('cancel', () => finish('C'));
        document.body.appendChild(input);
        input.click();
    })""",
)
private external fun pickTextFile(maxBytes: Int): Promise<JsString>

/** The share sheet where the browser has one (iPhone, Android); otherwise the text is copied or the picture downloaded. */
class WebShareService(
    private val renderImage: suspend (GameWithDetails, PayUpPalette, Boolean) -> ByteArray,
) : ShareService {
    override suspend fun shareText(text: String) {
        val result = nativeShareText(text).await<JsString>().toString()
        if (result == "unsupported" || result == "failed") {
            if (copyToClipboard(text).await<JsBoolean>().toBoolean()) showToast(getString(Res.string.copied_to_clipboard))
        }
    }

    override suspend fun shareResultImage(game: GameWithDetails, palette: PayUpPalette, rtl: Boolean) {
        val base64 = Base64.encode(renderImage(game, palette, rtl))
        val name = "payup-${game.game.id}.png"
        val result = nativeShareFile(base64, true, name, "image/png", getString(Res.string.share_chooser)).await<JsString>().toString()
        if (result == "unsupported" || result == "failed") downloadFile(base64, true, name, "image/png")
    }
}

/** Backups go out through the share sheet or as a download, and come back through the browser's file picker. */
class WebBackupFiles : BackupFiles {
    override suspend fun share(fileName: String, text: String) {
        val result = nativeShareFile(text, false, fileName, "application/json", getString(Res.string.share_chooser)).await<JsString>().toString()
        if (result == "unsupported" || result == "failed") downloadFile(text, false, fileName, "application/json")
    }

    // Browsers do not offer a save-as dialog everywhere, so "save to the device" is a download into the Downloads folder.
    override suspend fun saveAs(fileName: String, text: String): Boolean? {
        downloadFile(text, false, fileName, "application/json")
        return true
    }

    override suspend fun pick(): PickedFile {
        val result = pickTextFile(BackupFile.MAX_BYTES).await<JsString>().toString()
        return when {
            result.startsWith("T") -> PickedFile.Text(result.substring(1))
            result == "C" -> PickedFile.Cancelled
            else -> PickedFile.Unreadable
        }
    }
}

/** True on an iPhone or iPad that shows the site in a browser, not as an app added to the Home Screen. */
@JsFun(
    """() => {
        const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
        const installed = navigator.standalone === true || (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches);
        return ios && !installed;
    }""",
)
external fun isIosBrowserNotInstalled(): Boolean

@JsFun("() => Date.now()")
external fun nowMillis(): Double
