package com.payup.sync

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/** What a live listener on one shared game reports. A listener that has not heard anything yet reports nothing. */
sealed interface WatchState {
    /** The game exists. [fromCache] is true while only a saved copy is known, so the device may be offline. */
    data class Found(val online: OnlineGame, val fromCache: Boolean) : WatchState

    /** There is no game with this id (never shared, or the host deleted it). */
    data object NotFound : WatchState

    /** The listener stopped: no connection to start with, or the game may not be read. */
    data class Failed(val error: GameSyncException) : WatchState
}

class GameSyncException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind {
        /** No connection to the service right now; trying again later can work. */
        Offline,

        /** The rules refused: this user is not the host, or the key is wrong. */
        Denied,

        Other,
    }
}

/**
 * The online copy of shared games. The local database stays the source of truth; this only moves whole games to and from
 * Firestore. Every call signs in anonymously first if needed. Failures are [GameSyncException]s.
 */
interface GameSync {
    /**
     * Makes the online game [id] equal to [game], in one transaction: a missing game is created with this user as host
     * (and [hostKeyHash], see [sha256Hex]); otherwise the next revision is written. Fails when offline or not the host.
     */
    suspend fun publish(id: String, hostKeyHash: String, game: SharedGame)

    /** Live updates of game [id], ending when the collector stops. */
    fun watch(id: String): Flow<WatchState>

    /** Removes the online game. Only its host can. */
    suspend fun delete(id: String)

    /** Becomes the host of game [id] by proving knowledge of the host key; the previous host turns into a viewer. */
    suspend fun takeOver(id: String, hostKey: String)
}

/**
 * [GameSync.watch] for a screen: if nothing is heard within [patience] it reports [WatchState.Failed] (offline), and a
 * thrown error is reported as [WatchState.Failed] too. A late answer still comes through afterwards.
 */
fun GameSync.watchPatiently(id: String, patience: Duration = 10.seconds): Flow<WatchState> = channelFlow {
    val silence = launch {
        delay(patience)
        send(WatchState.Failed(GameSyncException(GameSyncException.Kind.Offline, "No answer")))
    }
    try {
        watch(id).collect {
            silence.cancel()
            send(it)
        }
        silence.cancel()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        silence.cancel()
        send(WatchState.Failed(e as? GameSyncException ?: GameSyncException(GameSyncException.Kind.Other, "Watch failed", e)))
    }
}
