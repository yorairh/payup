import com.payup.sync.GameSync
import com.payup.sync.GameSyncException
import com.payup.sync.OnlineGame
import com.payup.sync.RandomBytes
import com.payup.sync.SharedGame
import com.payup.sync.WatchState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.JsString
import kotlin.js.Promise

val webRandomBytes = RandomBytes { count ->
    val hex = bridgeRandomHex(count)
    ByteArray(count) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** [GameSync] on the Firebase JavaScript SDK, through webApp/firebase-bridge. Games cross as JSON text. */
class WebGameSync : GameSync {
    override suspend fun signIn(): String = answer(bridgeSignIn()).jsonPrimitive.content

    override suspend fun publish(id: String, hostKeyHash: String, game: SharedGame) {
        answer(bridgePublish(id, hostKeyHash, game.toJson()))
    }

    override fun watch(id: String): Flow<WatchState> = callbackFlow {
        val handle = bridgeWatch(id) { text -> trySend(event(text.toString())) }
        awaitClose { bridgeUnwatch(handle) }
    }

    override suspend fun delete(id: String) {
        answer(bridgeDeleteGame(id))
    }

    override suspend fun takeOver(id: String, hostKey: String) {
        answer(bridgeTakeOver(id, hostKey))
    }

    /** Waits for a bridge call and returns its value, or throws what it reported. */
    private suspend fun answer(call: Promise<JsString>): JsonElement {
        val reply = Json.parseToJsonElement(call.await<JsString>().toString()).jsonObject
        if (reply.getValue("ok").jsonPrimitive.boolean) return reply["value"] ?: JsonPrimitive(null as String?)
        throw failure(reply)
    }

    private fun failure(reply: JsonObject) = GameSyncException(
        when (reply["kind"]?.jsonPrimitive?.contentOrNull) {
            "offline" -> GameSyncException.Kind.Offline
            "denied" -> GameSyncException.Kind.Denied
            else -> GameSyncException.Kind.Other
        },
        reply["message"]?.jsonPrimitive?.contentOrNull ?: "Sync failed",
    )

    private fun event(text: String): WatchState {
        val event = Json.parseToJsonElement(text).jsonObject
        return when (event.getValue("state").jsonPrimitive.content) {
            "found" -> try {
                WatchState.Found(OnlineGame.fromJson(event.getValue("doc").toString()), event.getValue("fromCache").jsonPrimitive.boolean)
            } catch (e: Exception) {
                WatchState.Failed(GameSyncException(GameSyncException.Kind.Other, "Unreadable game", e))
            }
            "notfound" -> WatchState.NotFound
            else -> WatchState.Failed(failure(event))
        }
    }
}
