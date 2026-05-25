package com.metrolist.music.discordrpc

import com.metrolist.music.discordrpc.entities.ClientState
import com.metrolist.music.discordrpc.entities.HeartbeatResponse
import com.metrolist.music.discordrpc.entities.Identify
import com.metrolist.music.discordrpc.entities.IdentifyProperties
import com.metrolist.music.discordrpc.entities.OpCode
import com.metrolist.music.discordrpc.entities.Payload
import com.metrolist.music.discordrpc.entities.Presence
import com.metrolist.music.discordrpc.entities.Ready
import com.metrolist.music.discordrpc.entities.Resume
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import timber.log.Timber
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GatewayWebSocket(
    private val token: String,
    private val os: String,
    private val browser: String,
    private val device: String,
) : CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = job + Dispatchers.Default
    private val tag = "DiscordWS"

    private val client = HttpClient {
        install(WebSockets)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var session: DefaultClientWebSocketSession? = null
    private var sessionId: String? = null
    private var sequence = 0
    private var resumeUrl: String? = null
    private var heartbeatInterval = 0L
    private var heartbeatJob: Job? = null
    private var connected = false
    private var sessionEstablished = false
    private var reconnectionJob: Job? = null
    private var currentReconnectDelay = INITIAL_RECONNECT_DELAY
    private var intentionalClose = false
    private var lastHeartbeatAckReceivedAt = 0L
    private var heartbeatWatchdogJob: Job? = null
    private var lastPresence: Presence? = null
    private var totalConnectAttempts = 0
    private var totalPresenceUpdates = 0
    private var totalHeartbeats = 0

    init {
        Timber.tag(tag).d("GatewayWebSocket created: os=$os browser=$browser device=$device")
        Timber.tag(tag).d("Token length=${token.length}")
    }

    fun isSessionEstablished(): Boolean {
        val established = sessionEstablished
        Timber.tag(tag).v("isSessionEstablished: $established (connected=$connected, sessionId=${sessionId != null})")
        return established
    }

    fun connect() {
        totalConnectAttempts++
        Timber.tag(tag).i("connect() called (attempt #$totalConnectAttempts, connected=$connected, sessionEstablished=$sessionEstablished)")
        if (connected) {
            Timber.tag(tag).d("connect() called but already connected, sessionEstablished=$sessionEstablished")
            return
        }
        if (!isActive) {
            Timber.tag(tag).w("connect() called but scope is not active — was close() called?")
            return
        }
        Timber.tag(tag).i("Connecting to Gateway (attempt #$totalConnectAttempts)...")
        Timber.tag(tag).d("State before connect: sessionId=$sessionId seq=$sequence resumeUrl=$resumeUrl")
        intentionalClose = false
        reconnectionJob?.cancel()
        reconnectionJob = launch {
            try {
                establishConnection()
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "establishConnection() threw unhandled exception (attempt #$totalConnectAttempts)")
                scheduleReconnection()
            }
        }
    }

    private suspend fun establishConnection() {
        val url = resumeUrl ?: GATEWAY_URL
        val systemLocale = Locale.getDefault().toString().replace('_', '-')
        Timber.tag(tag).d("establishConnection: connecting to $url locale=$systemLocale (resume=${resumeUrl != null})")

        session = try {
            Timber.tag(tag).d("Establishing WebSocket session to $url...")
            client.webSocketSession(url) {
                Timber.tag(tag).d("Setting WS headers: User-Agent=$USER_AGENT, Accept-Language=$systemLocale")
                header("User-Agent", USER_AGENT)
                header("Accept-Language", systemLocale)
            }.also {
                Timber.tag(tag).d("WebSocket session created successfully")
            }
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "WebSocket connection failed to $url (attempt #$totalConnectAttempts)")
            connected = false
            throw e
        }

        connected = true
        currentReconnectDelay = INITIAL_RECONNECT_DELAY
        Timber.tag(tag).i("WebSocket connected to $url (attempt #$totalConnectAttempts)")

        try {
            Timber.tag(tag).d("Starting incoming frame collection...")
            session!!.incoming.receiveAsFlow().collect { frame ->
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        try {
                            val payload = json.decodeFromString<Payload>(text)
                            Timber.tag(tag).v("Received frame: op=${payload.op} t=${payload.t} s=${payload.s} (${text.length} bytes)")
                            handlePayload(payload)
                        } catch (e: Exception) {
                            Timber.tag(tag).w(e, "Failed to decode payload (${text.length} bytes): ${text.take(200)}")
                        }
                    }
                    is Frame.Close -> {
                        Timber.tag(tag).d("Received Close frame from server")
                        // Let the flow end naturally, handleDisconnect will process the close reason
                    }
                    else -> {
                        Timber.tag(tag).v("Ignored frame type: ${frame::class.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(tag).e(e, "WebSocket receive flow ended with exception (connected=$connected intentionalClose=$intentionalClose)")
        }

        if (!intentionalClose) {
            Timber.tag(tag).d("WebSocket receive flow ended, handling disconnect")
            handleDisconnect()
        } else {
            Timber.tag(tag).d("WebSocket receive flow ended (intentional close)")
        }
    }

    private suspend fun handlePayload(payload: Payload) {
        payload.s?.let {
            sequence = it
            Timber.tag(tag).v("Updated sequence to $it")
        }

        when (payload.op) {
            OpCode.DISPATCH -> {
                Timber.tag(tag).d("DISPATCH | seq=$sequence | event=${payload.t}")
                handleDispatch(payload)
            }
            OpCode.HEARTBEAT -> {
                totalHeartbeats++
                Timber.tag(tag).d("<- HEARTBEAT (server-requested #$totalHeartbeats)")
                sendHeartbeat()
            }
            OpCode.RECONNECT -> {
                Timber.tag(tag).w("<- RECONNECT — server requested reconnect (seq=$sequence)")
                handleReconnect()
            }
            OpCode.INVALID_SESSION -> {
                val canResume = payload.d?.let { json.decodeFromJsonElement<Boolean>(it) } ?: false
                Timber.tag(tag).w("<- INVALID_SESSION | canResume=$canResume sessionId=$sessionId seq=$sequence")
                handleInvalidSession(payload)
            }
            OpCode.HELLO -> {
                Timber.tag(tag).d("<- HELLO — starting handshake")
                handleHello(payload)
            }
            OpCode.HEARTBEAT_ACK -> {
                val now = System.currentTimeMillis()
                val elapsed = now - lastHeartbeatAckReceivedAt
                lastHeartbeatAckReceivedAt = now
                Timber.tag(tag).v("<- HEARTBEAT_ACK (${elapsed}ms since last ACK)")
            }
            else -> {
                Timber.tag(tag).d("<- op=${payload.op} (${payload.op?.name})")
            }
        }
    }

    private suspend fun handleHello(payload: Payload) {
        val hello = json.decodeFromJsonElement<HeartbeatResponse>(payload.d!!)
        heartbeatInterval = hello.heartbeatInterval
        Timber.tag(tag).i("HELLO received: heartbeat_interval=${heartbeatInterval}ms")

        val jitter = (0..<heartbeatInterval).random()
        Timber.tag(tag).d("First heartbeat with jitter=${jitter}ms, will fire at ${jitter}ms")
        delay(jitter)
        Timber.tag(tag).d("Jitter delay done, sending first heartbeat")
        totalHeartbeats++
        sendHeartbeat()

        Timber.tag(tag).d("Starting heartbeat loop and watchdog...")
        startHeartbeatLoop()
        startHeartbeatWatchdog()

        if (sessionId != null && sequence > 0) {
            Timber.tag(tag).i("Resuming session: sessionId=$sessionId seq=$sequence")
            sendResume(sessionId!!)
        } else {
            Timber.tag(tag).i("No existing session to resume, sending Identify (sessionId=$sessionId seq=$sequence)")
            sendIdentify()
        }
    }

    private suspend fun handleDispatch(payload: Payload) {
        when (payload.t) {
            "READY" -> {
                val ready = json.decodeFromJsonElement<Ready>(payload.d!!)
                sessionId = ready.sessionId
                resumeUrl = ready.resumeGatewayUrl?.let { "$it/?v=9&encoding=json" }
                sessionEstablished = true
                Timber.tag(tag).i("READY received: sessionId=$sessionId resumeUrl=$resumeUrl")
                Timber.tag(tag).d("Session established (ready)")
                resendLastPresence()
            }
            "RESUMED" -> {
                sessionEstablished = true
                Timber.tag(tag).i("RESUMED — session re-established (sessionId=$sessionId)")
                Timber.tag(tag).d("Session established (resumed)")
                resendLastPresence()
            }
            else -> {
                Timber.tag(tag).d("Unhandled dispatch event: ${payload.t}")
            }
        }
    }

    private suspend fun handleReconnect() {
        Timber.tag(tag).w("handleReconnect: closing session with code 4000")
        session?.close(CloseReason(4000, "Reconnect requested"))
    }

    private suspend fun handleInvalidSession(payload: Payload) {
        val canResume = payload.d?.let { json.decodeFromJsonElement<Boolean>(it) } ?: false
        val sid = sessionId
        Timber.tag(tag).i("handleInvalidSession: canResume=$canResume sid=$sid seq=$sequence")
        delay(1500)
        if (canResume && sid != null) {
            Timber.tag(tag).i("INVALID_SESSION: can resume, sending Resume with sid=$sid seq=$sequence")
            sendResume(sid)
        } else {
            Timber.tag(tag).i("INVALID_SESSION: cannot resume (canResume=$canResume sid=$sid), sending fresh Identify")
            Timber.tag(tag).d("Clearing session state: sessionId=$sessionId -> null, seq=$sequence -> 0, resumeUrl=$resumeUrl -> null")
            sessionId = null
            sequence = 0
            resumeUrl = null
            sessionEstablished = false
            Timber.tag(tag).d("Sending fresh Identify...")
            sendIdentify()
        }
    }

    private suspend fun handleDisconnect() {
        Timber.tag(tag).d("handleDisconnect: cancelling heartbeat jobs")
        heartbeatJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        connected = false
        sessionEstablished = false
        val reason = session?.closeReason?.await()
        val code = reason?.code?.toInt() ?: -1
        val message = reason?.message ?: "unknown"
        Timber.tag(tag).w("handleDisconnect: disconnected with code=$code reason='$message'")
        Timber.tag(tag).d("State after disconnect: sessionId=$sessionId seq=$sequence resumeUrl=$resumeUrl")

        when {
            code == 4004 -> {
                Timber.tag(tag).e("Token invalid (4004) — will not attempt reconnection")
            }
            code == 4006 || code == 4008 -> {
                Timber.tag(tag).i("Session invalidated (code=$code), clearing session state and reconnecting")
                sessionId = null
                sequence = 0
                resumeUrl = null
                scheduleReconnection()
            }
            code == 4000 -> {
                Timber.tag(tag).d("Close code 4000 — scheduling immediate reconnect")
                delay(200.milliseconds)
                connect()
            }
            else -> {
                Timber.tag(tag).d("Close code $code — scheduling reconnection with backoff")
                scheduleReconnection()
            }
        }
    }

    private suspend fun sendIdentify() {
        val systemLocale = Locale.getDefault().toString()
        val osVersion = android.os.Build.VERSION.RELEASE
        val sdkVersion = android.os.Build.VERSION.SDK_INT.toString()
        val props = IdentifyProperties(
            os = os,
            browser = browser,
            device = device,
            systemLocale = systemLocale,
            clientVersion = "314.13 - Stable",
            releaseChannel = "googleRelease",
            osVersion = osVersion,
            osSdkVersion = sdkVersion,
            clientBuildNumber = 314013,
        )
        Timber.tag(tag).i("-> IDENTIFY: os=$os browser=$browser device=$device locale=$systemLocale sdk=$sdkVersion")
        val identify = Identify(
            token = token,
            properties = props,
            presence = Presence(status = "online", since = null, afk = false),
            clientState = ClientState(),
        )
        Timber.tag(tag).d("Sending Identify payload (token len=${token.length})")
        send(op = OpCode.IDENTIFY, d = identify)
        Timber.tag(tag).d("Identify sent")
    }

    private suspend fun sendResume(sid: String) {
        Timber.tag(tag).i("-> RESUME: sessionId=$sid seq=$sequence")
        val resume = Resume(token = token, sessionId = sid, seq = sequence)
        Timber.tag(tag).d("Sending Resume payload (token len=${token.length})")
        send(op = OpCode.RESUME, d = resume)
        Timber.tag(tag).d("Resume sent")
    }

    private suspend fun sendHeartbeat() {
        totalHeartbeats++
        val seq = if (sequence == 0) null else sequence
        Timber.tag(tag).v("-> HEARTBEAT #$totalHeartbeats seq=$seq")
        send(op = OpCode.HEARTBEAT, d = seq)
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            Timber.tag(tag).d("Heartbeat loop started (interval=${heartbeatInterval}ms)")
            lastHeartbeatAckReceivedAt = System.currentTimeMillis()
            var beatCount = 0
            while (isActive) {
                delay(heartbeatInterval)
                beatCount++
                Timber.tag(tag).v("Heartbeat loop: beat #$beatCount, sending...")
                sendHeartbeat()
            }
            Timber.tag(tag).d("Heartbeat loop ended (sent $beatCount beats)")
        }
    }

    private fun startHeartbeatWatchdog() {
        heartbeatWatchdogJob?.cancel()
        heartbeatWatchdogJob = launch {
            val threshold = (heartbeatInterval * 2).coerceAtLeast(10_000L)
            Timber.tag(tag).d("Heartbeat watchdog started (threshold=${threshold}ms, interval=${heartbeatInterval}ms)")
            while (isActive) {
                delay(threshold)
                val elapsed = System.currentTimeMillis() - lastHeartbeatAckReceivedAt
                Timber.tag(tag).v("Watchdog check: ${elapsed}ms since last ACK (threshold=${threshold}ms)")
                if (elapsed >= threshold && connected) {
                    Timber.tag(tag).w("Heartbeat watchdog triggered: no ACK for ${elapsed}ms — forcing reconnect")
                    handleReconnect()
                }
            }
            Timber.tag(tag).d("Heartbeat watchdog ended")
        }
    }

    private fun scheduleReconnection() {
        if (intentionalClose) {
            Timber.tag(tag).d("scheduleReconnection: intentionalClose=true, skipping reconnection")
            return
        }
        Timber.tag(tag).d("scheduleReconnection: scheduling reconnect in ${currentReconnectDelay.inWholeSeconds}s (connected=$connected)")
        reconnectionJob?.cancel()
        reconnectionJob = launch {
            delay(currentReconnectDelay)
            Timber.tag(tag).d("scheduleReconnection: wait elapsed, current delay was ${currentReconnectDelay.inWholeSeconds}s")
            currentReconnectDelay = (currentReconnectDelay * 2).coerceAtMost(MAX_RECONNECT_DELAY)
            Timber.tag(tag).d("scheduleReconnection: calling connect() (next delay=${currentReconnectDelay.inWholeSeconds}s)")
            connect()
        }
    }

    suspend fun updatePresence(presence: Presence) {
        totalPresenceUpdates++
        Timber.tag(tag).d("updatePresence #$totalPresenceUpdates: called (sessionEstablished=$sessionEstablished)")
        lastPresence = presence

        if (!sessionEstablished) {
            Timber.tag(tag).d("updatePresence #$totalPresenceUpdates: session not established, queueing and triggering connect")
            if (!connected && !intentionalClose) {
                connect()
            }
            return
        }

        Timber.tag(tag).i("-> PRESENCE_UPDATE #$totalPresenceUpdates: activities=${presence.activities.size} status=${presence.status}")
        if (presence.activities.isNotEmpty()) {
            val act = presence.activities.first()
            Timber.tag(tag).d("Activity details: name=${act.name} type=${act.type} state=${act.state} details=${act.details}")
            Timber.tag(tag).d("Activity assets: large=${act.assets?.largeImage?.takeLast(30)} small=${act.assets?.smallImage?.takeLast(30)}")
            Timber.tag(tag).d("Activity timestamps: start=${act.timestamps?.start} end=${act.timestamps?.end}")
            Timber.tag(tag).d("Activity buttons: ${act.buttons?.size}")
        }
        send(op = OpCode.PRESENCE_UPDATE, d = presence)
    }

    private suspend fun resendLastPresence() {
        val presence = lastPresence ?: run {
            Timber.tag(tag).d("resendLastPresence: no last presence to resend")
            return
        }
        Timber.tag(tag).i("resendLastPresence: re-sending previous presence after session recovery")
        Timber.tag(tag).d("resendLastPresence: activities=${presence.activities.size} status=${presence.status}")
        send(op = OpCode.PRESENCE_UPDATE, d = presence)
        Timber.tag(tag).d("resendLastPresence: done")
    }

    suspend fun clearPresence() {
        Timber.tag(tag).d("clearPresence: called (sessionEstablished=$sessionEstablished)")
        if (sessionEstablished) {
            Timber.tag(tag).i("-> PRESENCE_UPDATE (clearing) — sending empty activities")
            send(
                op = OpCode.PRESENCE_UPDATE,
                d = Presence(activities = emptyList(), since = null, status = "online", afk = false),
            )
            Timber.tag(tag).d("clearPresence: sent empty presence")
        } else {
            Timber.tag(tag).d("clearPresence: session not established, nothing to clear")
        }
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        Timber.tag(tag).v("send: op=${op.name} dType=${T::class.simpleName} dNull=${d == null}")
        if (session?.isActive == true) {
            val payload = json.encodeToString(
                Payload(
                    op = op,
                    d = if (d != null) json.encodeToJsonElement(d) else null,
                ),
            )
            Timber.tag(tag).v("send: payload len=${payload.length} bytes")
            try {
                session?.send(Frame.Text(payload))
                Timber.tag(tag).v("send: ${op.name} payload sent successfully")
            } catch (e: Exception) {
                Timber.tag(tag).e(e, "send: failed to send ${op.name} payload (${payload.length} bytes)")
            }
        } else {
            Timber.tag(tag).w("send: Cannot send ${op.name}: session is not active (isActive=${session?.isActive})")
        }
    }

    fun close() {
        Timber.tag(tag).i("close() called (connected=$connected sessionEstablished=$sessionEstablished)")
        intentionalClose = true
        reconnectionJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        Timber.tag(tag).d("close: cancelled all jobs, closing WebSocket session...")
        kotlinx.coroutines.runBlocking {
            try {
                session?.close()
                Timber.tag(tag).d("close: WebSocket session closed")
            } catch (e: Exception) {
                Timber.tag(tag).d(e, "close: exception while closing session (ignored)")
            }
        }
        connected = false
        sessionEstablished = false
        Timber.tag(tag).i("close: completed (state reset)")
    }

    companion object {
        private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=9&encoding=json"
        private const val USER_AGENT = "Discord-Android/314013;RNA"

        private val INITIAL_RECONNECT_DELAY = 1.seconds
        private val MAX_RECONNECT_DELAY = 60.seconds
    }
}
