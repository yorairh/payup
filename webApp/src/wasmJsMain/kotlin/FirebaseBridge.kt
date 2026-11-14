@file:JsModule("payup-firebase")

import kotlin.js.JsModule
import kotlin.js.JsName
import kotlin.js.JsString
import kotlin.js.Promise

// The functions of webApp/firebase-bridge/bridge.js, renamed here so they do not clash with the GameSync methods. Only strings and numbers cross; see that file for the answers' shape.

@JsName("randomHex")
external fun bridgeRandomHex(count: Int): String

@JsName("publish")
external fun bridgePublish(id: String, hostKeyHash: String, contentJson: String): Promise<JsString>

@JsName("deleteGame")
external fun bridgeDeleteGame(id: String): Promise<JsString>

@JsName("takeOver")
external fun bridgeTakeOver(id: String, hostKey: String): Promise<JsString>

@JsName("watch")
external fun bridgeWatch(id: String, onEvent: (JsString) -> Unit): Int

@JsName("unwatch")
external fun bridgeUnwatch(handle: Int)
