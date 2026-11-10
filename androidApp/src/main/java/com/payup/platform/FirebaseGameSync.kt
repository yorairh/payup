package com.payup.platform

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.payup.sync.GameSync
import com.payup.sync.GameSyncException
import com.payup.sync.OnlineGame
import com.payup.sync.RandomBytes
import com.payup.sync.SharedGame
import com.payup.sync.WatchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

val androidRandomBytes = RandomBytes { count -> ByteArray(count).also { SecureRandom().nextBytes(it) } }

/**
 * [GameSync] on the Firebase Android SDK. Games cross the seam as JSON text (see SharedGame), which is turned into the
 * maps Firestore wants here. Writes go through transactions, which fail when offline instead of waiting for a connection.
 */
class FirebaseGameSync(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : GameSync {
    private fun game(id: String): DocumentReference = db.collection("games").document(id)

    override suspend fun signIn(): String = wrap {
        auth.currentUser?.uid ?: checkNotNull(auth.signInAnonymously().await().user).uid
    }

    override suspend fun publish(id: String, hostKeyHash: String, game: SharedGame) {
        val uid = signIn()
        val content = jsonToMap(game.toJson())
        wrap {
            db.runTransaction { tx ->
                val ref = game(id)
                val existing = tx.get(ref)
                val doc = HashMap(content)
                doc["hostUid"] = uid
                if (existing.exists()) {
                    doc["revision"] = (existing.getLong("revision") ?: 0L) + 1
                    doc["hostKeyHash"] = existing.getString("hostKeyHash") ?: hostKeyHash
                } else {
                    doc["revision"] = 1L
                    doc["hostKeyHash"] = hostKeyHash
                }
                tx.set(ref, doc)
            }.await()
        }
    }

    override fun watch(id: String): Flow<WatchState> = callbackFlow {
        try {
            signIn()
        } catch (e: GameSyncException) {
            trySend(WatchState.Failed(e))
            close()
            return@callbackFlow
        }
        val registration = game(id).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            when {
                error != null -> trySend(WatchState.Failed(error.toSyncException()))
                snapshot == null -> Unit
                snapshot.exists() -> {
                    val state = try {
                        WatchState.Found(OnlineGame.fromJson(mapToJson(checkNotNull(snapshot.data))), snapshot.metadata.isFromCache)
                    } catch (e: Exception) {
                        WatchState.Failed(GameSyncException(GameSyncException.Kind.Other, "Unreadable game", e))
                    }
                    trySend(state)
                }
                // A missing document in the saved copy only means "not known yet" while the server has not answered.
                !snapshot.metadata.isFromCache -> trySend(WatchState.NotFound)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun delete(id: String) {
        signIn()
        wrap { db.runTransaction { tx -> tx.delete(game(id)) }.await() }
    }

    override suspend fun takeOver(id: String, hostKey: String) {
        val uid = signIn()
        val claim = game(id).collection("claims").document(uid)
        wrap { db.runTransaction { tx -> tx.set(claim, mapOf("key" to hostKey)) }.await() }
        wrap {
            db.runTransaction { tx ->
                val existing = tx.get(game(id))
                val doc = HashMap(checkNotNull(existing.data) { "The game is gone" })
                doc["revision"] = (existing.getLong("revision") ?: 0L) + 1
                doc["hostUid"] = uid
                tx.set(game(id), doc)
            }.await()
        }
        // Tidying up only; the rules do not depend on the claim being removed.
        runCatching { db.runTransaction { tx -> tx.delete(claim) }.await() }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: GameSyncException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        throw e.toSyncException()
    } catch (e: FirebaseNetworkException) {
        throw GameSyncException(GameSyncException.Kind.Offline, "No connection", e)
    } catch (e: Exception) {
        throw GameSyncException(GameSyncException.Kind.Other, e.message ?: "Sync failed", e)
    }

    private fun FirebaseFirestoreException.toSyncException() = GameSyncException(
        when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> GameSyncException.Kind.Offline
            FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED -> GameSyncException.Kind.Denied
            else -> GameSyncException.Kind.Other
        },
        message ?: code.name,
        this,
    )
}

/** JSON text to the nested maps and lists Firestore stores; whole numbers become Long. */
@Suppress("UNCHECKED_CAST")
private fun jsonToMap(json: String): Map<String, Any?> = JSONObject(json).toKotlin() as Map<String, Any?>

private fun Any?.toKotlin(): Any? = when (this) {
    is JSONObject -> keys().asSequence().associateWith { get(it).toKotlin() }
    is JSONArray -> List(length()) { get(it).toKotlin() }
    is Int -> toLong()
    JSONObject.NULL -> null
    else -> this
}

private fun mapToJson(map: Map<String, Any?>): String = JSONObject(map).toString()
