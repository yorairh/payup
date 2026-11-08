package com.payup.platform

import androidx.compose.runtime.staticCompositionLocalOf
import com.payup.data.GameWithDetails
import com.payup.ui.theme.PayUpPalette

/** Dates in the format the device's own language and region use. */
interface DateFormatter {
    /** Like "Nov 7, 2026". */
    fun date(millis: Long): String

    /** Like "Nov 7, 2026, 9:41 PM". */
    fun dateTime(millis: Long): String

    /** Like "9:41 PM". */
    fun time(millis: Long): String
}

/** Hands a result to the system share menu (or whatever the platform offers instead). */
interface ShareService {
    suspend fun shareText(text: String)

    /** Draws the results card for [game] with [palette] and shares it as a picture; [rtl] is the layout direction. */
    suspend fun shareResultImage(game: GameWithDetails, palette: PayUpPalette, rtl: Boolean)
}

/** What came back from asking the user to pick a backup file. */
sealed interface PickedFile {
    /** The user closed the file dialog without choosing. */
    data object Cancelled : PickedFile

    /** A file was chosen but could not be read, or it is too big to be a backup. */
    data object Unreadable : PickedFile

    data class Text(val text: String) : PickedFile
}

/** The files the backup feature moves around; each call opens whatever dialog the platform needs. */
interface BackupFiles {
    /** Offers [text] as a file named [fileName] to the share menu. */
    suspend fun share(fileName: String, text: String)

    /** Lets the user choose where to save [text]. True if it was saved, false if writing failed, null if cancelled. */
    suspend fun saveAs(fileName: String, text: String): Boolean?

    suspend fun pick(): PickedFile
}

/** The parts of the screens that only the platform can do. Each platform builds one and provides it at the root. */
class PlatformServices(
    val dates: DateFormatter,
    val share: ShareService,
    val backupFiles: BackupFiles,
)

val LocalPlatform = staticCompositionLocalOf<PlatformServices> {
    error("No PlatformServices provided: set LocalPlatform at the root of the app")
}
