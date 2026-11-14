package com.payup.sync

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
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

/**
 * Keeps the online copy of the game this device hosts equal to the local one. The local database stays the source of
 * truth: every change to the latest hosted game is uploaded whole after a short wait, and a failed upload is retried
 * every few seconds until it works. It also uploads once when it starts, which covers a change the app was closed before sending.
 */
class SharedGameSyncer(
    private val repository: GameRepository,
    private val sync: GameSync,
    private val shareIds: ShareIds,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(SyncStatus.Synced)
    val status: StateFlow<SyncStatus> = state

    /** Uploads and removals take turns, so a late upload can never bring back a game that was just removed. */
    private val lock = Mutex()
    private val removed = mutableSetOf<String>()

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
        // The query re-emits on every write to the database; only a real change to the hosted game is uploaded.
        repository.latestHostedGame
            .map { details -> details?.game?.shareId?.let { Upload(it, details.game.hostKey.orEmpty(), details.toSharedGame()) } }
            .distinctUntilChanged()
            .debounce(WAIT)
            .collectLatest { upload ->
            if (upload == null) {
                state.value = SyncStatus.Synced
                return@collectLatest
            }
            val id = upload.id
            state.value = SyncStatus.Syncing
            val hash = sha256Hex(upload.hostKey)
            var retry = RETRY
            while (true) {
                try {
                    lock.withLock { if (id !in removed) sync.publish(id, hash, upload.game) }
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
                    retry = (retry * 2).coerceAtMost(MAX_RETRY)
                }
            }
        }
    }

    /**
     * Puts game [current] online, unless it already is, and returns the link to hand out; null when that failed (nothing
     * was changed, try again later). The game is marked shared on this device only after the first upload worked, so a
     * game never looks shared when it is not.
     */
    suspend fun share(current: GameWithDetails): String? {
        current.game.shareId?.let { return ShareLinks.viewer(it) }
        val id = shareIds.newGameId()
        val key = shareIds.newHostKey()
        return try {
            sync.publish(id, sha256Hex(key), current.toSharedGame())
            repository.markShared(current.game.id, id, key)
            ShareLinks.viewer(id)
        } catch (e: GameSyncException) {
            null
        }
    }

    /**
     * Deletes game [gameId] from this device. If this device hosts it online, the online copy goes first, so the link
     * stops working; returns false (and changes nothing) when that is not possible without a connection.
     */
    suspend fun deleteGame(gameId: Long): Boolean {
        val id = repository.hostedShareId(gameId)
        if (id != null) {
            val gone = lock.withLock {
                try {
                    sync.delete(id)
                    true
                } catch (e: GameSyncException) {
                    // Refused: someone else is the host now (or it is already gone), so there is nothing of ours to remove.
                    e.kind == GameSyncException.Kind.Denied
                }.also { if (it) removed += id }
            }
            if (!gone) return false
        }
        repository.deleteGame(gameId)
        return true
    }

    /**
     * Removes the online copy of the unfinished game this device hosts, because it is about to be thrown away. Returns at
     * once: the removal runs in the background and never fails; offline, the online copy simply stays behind.
     */
    suspend fun discardActiveGameOnline() {
        val id = repository.activeHostedShareId() ?: return
        removed += id
        scope.launch {
            withTimeoutOrNull(DISCARD_WAIT) {
                lock.withLock {
                    try {
                        sync.delete(id)
                    } catch (e: GameSyncException) {
                        // Left behind online; nothing more to do.
                    }
                }
            }
        }
    }

    /** What goes online for one hosted game; two equal uploads mean nothing changed. */
    private data class Upload(val id: String, val hostKey: String, val game: SharedGame)

    private companion object {
        val WAIT = 1.seconds
        val RETRY = 15.seconds
        val MAX_RETRY = 5.minutes
        val DISCARD_WAIT = 5.seconds
    }
}
