package com.payup.sync

import com.payup.data.GameRepository
import com.payup.data.GameWithDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How the online copy of the game this device hosts is doing. */
enum class SyncStatus {
    /** Online copy is up to date (or there is nothing shared). */
    Synced,

    /** A change is on its way. */
    Syncing,

    /** No connection; the change is kept and tried again every few seconds. */
    Offline,

    /** The online copy refuses this device (someone else took over hosting, or the game was removed). */
    Stopped,
}

/** What happened when the host asked to share a game. */
sealed interface ShareResult {
    /** The game is online; [link] is what to hand out. */
    data class Shared(val link: String) : ShareResult

    /** Nothing was shared and nothing was changed on this device; try again later. */
    data object Failed : ShareResult
}

/**
 * Keeps the online copy of the game this device hosts equal to the local one. The local database stays the source of
 * truth: every change to the latest hosted game is uploaded whole after [wait], and a failed upload is retried every
 * [retry] until it works. It also uploads once when it starts, which covers a change the app was closed before sending.
 */
class SharedGameSyncer(
    private val repository: GameRepository,
    private val sync: GameSync,
    private val shareIds: ShareIds,
    scope: CoroutineScope,
    private val wait: Duration = 1.seconds,
    private val retry: Duration = 15.seconds,
) {
    private val state = MutableStateFlow(SyncStatus.Synced)
    val status: StateFlow<SyncStatus> = state

    private val lostGame = MutableStateFlow<String?>(null)

    /** The online id of a game this device used to host but someone else took over; null when there is none. */
    val lostHosting: StateFlow<String?> = lostGame

    /** The person has been told that hosting moved away; clears [lostHosting]. */
    fun acknowledgeLostHosting() {
        lostGame.value = null
    }

    init {
        scope.launch { watchLocalChanges() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun watchLocalChanges() {
        repository.latestHostedGame.debounce(wait.inWholeMilliseconds.coerceAtLeast(1).milliseconds).collectLatest { details ->
            val id = details?.game?.shareId
            if (details == null || id == null) {
                state.value = SyncStatus.Synced
                return@collectLatest
            }
            state.value = SyncStatus.Syncing
            val hash = sha256Hex(details.game.hostKey.orEmpty())
            while (true) {
                try {
                    sync.publish(id, hash, details.toSharedGame())
                    state.value = SyncStatus.Synced
                    return@collectLatest
                } catch (e: GameSyncException) {
                    if (e.kind == GameSyncException.Kind.Denied) {
                        state.value = SyncStatus.Stopped
                        lostGame.value = id
                        return@collectLatest
                    }
                    state.value = SyncStatus.Offline
                    delay(retry)
                }
            }
        }
    }

    /**
     * Puts game [gameId] online, unless it already is. The game is marked shared on this device only after the first
     * upload worked, so a game never looks shared when it is not.
     */
    suspend fun share(gameId: Long, current: GameWithDetails): ShareResult {
        current.game.shareId?.let { return ShareResult.Shared(ShareLinks.viewer(it)) }
        val id = shareIds.newGameId()
        val key = shareIds.newHostKey()
        return try {
            sync.publish(id, sha256Hex(key), current.toSharedGame())
            repository.markShared(gameId, id, key)
            ShareResult.Shared(ShareLinks.viewer(id))
        } catch (e: GameSyncException) {
            ShareResult.Failed
        }
    }
}
