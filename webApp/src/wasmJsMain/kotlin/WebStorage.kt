import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import com.payup.data.AppDatabase
import com.payup.data.CurrencyStore
import com.payup.data.ThemeStore
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import org.w3c.dom.Worker
import kotlin.js.JsBoolean
import kotlin.js.Promise
import kotlin.js.toBoolean

/** Theme and currency live in the browser's localStorage, under the same keys as on Android. */
object WebSettings {
    private val settings = StorageSettings()
    val themeStore = ThemeStore(settings)
    val currencyStore = CurrencyStore(settings)
    val language = WebAppLanguage(settings)
    val installHint = InstallHint(settings)
}

/**
 * The games live in a SQLite database in the browser's private file storage (OPFS), opened through a Web Worker
 * (webApp/worker/worker.js). The file keeps the Android name, and the web database starts at the current version,
 * so the migrations only matter once a later version exists.
 */
@OptIn(ExperimentalWasmJsInterop::class)
fun openWebDatabase(): AppDatabase =
    Room.databaseBuilder<AppDatabase>("payup.db")
        .setDriver(WebWorkerSQLiteDriver(sqliteWorker()))
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()

@OptIn(ExperimentalWasmJsInterop::class)
private fun sqliteWorker(): Worker = js("""new Worker(new URL("sqlite-wasm-worker/worker.js", import.meta.url))""")

/**
 * The database file can only be open in one tab at a time, so each tab asks the browser for a lock that it holds until
 * the tab closes. Returns false when another tab already has it.
 */
@JsFun(
    """() => new Promise(resolve => {
        if (!navigator.locks) { resolve(true); return; }
        navigator.locks.request('payup-database', { ifAvailable: true }, lock => {
            if (!lock) { resolve(false); return null; }
            resolve(true);
            return new Promise(() => {}); // never settles: the lock is held until the tab is closed
        });
    })""",
)
private external fun requestTabLock(): Promise<JsBoolean>

/** Waits a moment for the previous page to let go of the lock after a reload, before giving up. */
suspend fun acquireSingleTabLock(): Boolean {
    repeat(5) {
        if (requestTabLock().await<JsBoolean>().toBoolean()) return true
        delay(300)
    }
    return false
}

/**
 * The "Add to Home Screen" hint. Safari deletes a website's saved data after about a week without a visit, but not an
 * app added to the Home Screen, so the hint is shown on the first visit and once more a week after it was closed.
 */
class InstallHint(private val settings: Settings) {
    fun shouldShow(): Boolean {
        if (!isIosBrowserNotInstalled()) return false
        val closed = settings.getInt(CLOSED_COUNT, 0)
        val last = settings.getLong(LAST_CLOSED, 0L)
        return closed == 0 || (closed == 1 && nowMillis() - last > WEEK_MILLIS)
    }

    fun closed() {
        settings.putInt(CLOSED_COUNT, settings.getInt(CLOSED_COUNT, 0) + 1)
        settings.putLong(LAST_CLOSED, nowMillis().toLong())
    }

    private companion object {
        const val CLOSED_COUNT = "installHintClosed"
        const val LAST_CLOSED = "installHintLastClosed"
        const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000.0
    }
}
