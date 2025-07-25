package com.penumbraos.bridge_settings

import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SettingsWebServer"

@Serializable
sealed class SettingsMessage {
    @Serializable
    @SerialName("updateSetting")
    data class UpdateSetting(val category: String, val key: String, val value: JsonElement) :
        SettingsMessage()

    @Serializable
    @SerialName("registerForUpdates")
    data class RegisterForUpdates(val categories: List<String>) : SettingsMessage()

    @Serializable
    @SerialName("getAllSettings")
    object GetAllSettings : SettingsMessage()
}

@Serializable
sealed class StatusMessage {
    @Serializable
    @SerialName("settingChanged")
    data class SettingChanged(val category: String, val key: String, val value: String) :
        StatusMessage()

    @Serializable
    @SerialName("statusUpdate")
    data class StatusUpdate(val type: String, val data: Map<String, String>) : StatusMessage()

    @Serializable
    @SerialName("allSettings")
    data class AllSettings(val settings: Map<String, Map<String, JsonElement>>) : StatusMessage()

    @Serializable
    @SerialName("appStatusUpdate")
    data class AppStatusUpdate(
        val appId: String,
        val component: String,
        val data: Map<String, JsonElement>
    ) : StatusMessage()

    @Serializable
    @SerialName("appEvent")
    data class AppEvent(
        val appId: String,
        val eventType: String,
        val payload: Map<String, JsonElement>
    ) : StatusMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : StatusMessage()
}

class SettingsWebServer(
    private val settingsRegistry: SettingsRegistry,
    private val port: Int = 8080
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? =
        null
    private val webSocketSessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start() {
        Log.i(TAG, "Starting settings web server on port $port")

        val host = "0.0.0.0"

        server = embeddedServer(Netty, port = port, host = host) {
            configureServer()
        }

        // Monitor settings changes and broadcast to all clients
        serverScope.launch {
            settingsRegistry.settingsFlow.collect { allSettings ->
                broadcastSettingsUpdate(allSettings)
            }
        }

        try {
            server?.start(wait = false)

            // Verify the server actually started by checking if it's running
            val startTime = System.currentTimeMillis()
            val timeout = 5000 // 5 seconds

            while (server?.engine?.resolvedConnectors()?.isEmpty() != false && (System.currentTimeMillis() - startTime) < timeout) {
                Thread.sleep(100)
            }

            if (server?.engine?.resolvedConnectors()?.isEmpty() != false) {
                throw RuntimeException("Server failed to start within timeout period")
            }

            Log.i(TAG, "Settings web server started successfully at http://$host:$port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server on port $port", e)
            throw RuntimeException("Failed to bind to port $port: ${e.message}", e)
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping settings web server")
        try {
            server?.stop(1000, 2000)
            serverScope.cancel()
            webSocketSessions.clear()
            Log.i(TAG, "Settings web server stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping web server", e)
        }
    }

    private fun Application.configureServer() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                classDiscriminator = "type"
            })
        }

        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            // WebSocket endpoint for real-time communication
            webSocket("/ws/settings") {
                handleWebSocketConnection(this)
            }

            // REST API endpoints
            get("/api/settings") {
                try {
                    val allSettings = settingsRegistry.getAllSettings()
                    call.respond(allSettings)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting all settings", e)
                    call.respond(mapOf("error" to "Failed to get settings"))
                }
            }

            get("/api/settings/system") {
                try {
                    val systemSettings = settingsRegistry.getAllSystemSettings()
                    call.respond(systemSettings)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting system settings", e)
                    call.respond(mapOf("error" to "Failed to get system settings"))
                }
            }

            post("/api/settings/system/{key}") {
                try {
                    val key =
                        call.parameters["key"] ?: throw IllegalArgumentException("Missing key")
                    val body = call.receive<Map<String, String>>()
                    val value = body["value"] ?: throw IllegalArgumentException("Missing value")

                    val success = settingsRegistry.updateSystemSetting(key, value)
                    if (success) {
                        call.respond(mapOf("status" to "success"))
                    } else {
                        call.respond(mapOf("error" to "Failed to update setting"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating system setting", e)
                    call.respond(mapOf("error" to e.message))
                }
            }

            get("/api/settings/app/{appId}") {
                try {
                    val appId =
                        call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
                    val appSettings = settingsRegistry.getAllAppSettings(appId)
                    call.respond(appSettings)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting app settings", e)
                    call.respond(mapOf("error" to "Failed to get app settings"))
                }
            }

            post("/api/settings/app/{appId}/{category}/{key}") {
                try {
                    val appId =
                        call.parameters["appId"] ?: throw IllegalArgumentException("Missing appId")
                    val category = call.parameters["category"]
                        ?: throw IllegalArgumentException("Missing category")
                    val key =
                        call.parameters["key"] ?: throw IllegalArgumentException("Missing key")
                    val body = call.receive<Map<String, String>>()
                    val value = body["value"] ?: throw IllegalArgumentException("Missing value")

                    val success = settingsRegistry.updateAppSetting(appId, category, key, value)
                    if (success) {
                        call.respond(mapOf("status" to "success"))
                    } else {
                        call.respond(mapOf("error" to "Failed to update setting"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating app setting", e)
                    call.respond(mapOf("error" to e.message))
                }
            }

            // Serve React static files from APK
            get("/{file...}") {
                val path = call.parameters.getAll("file")?.joinToString("/") ?: ""
                val resourcePath =
                    if (path.isEmpty() || path == "/") "react-build/index.html" else "react-build/$path"

                val inputStream = getResourceFromApk(resourcePath)
                if (inputStream != null) {
                    val contentType = getContentType(resourcePath)
                    call.respondBytes(inputStream.readBytes(), contentType)
                    inputStream.close()
                } else {
                    // File not found, try to serve index.html for SPA routing
                    val indexStream = getResourceFromApk("react-build/index.html")
                    if (indexStream != null) {
                        call.respondBytes(indexStream.readBytes(), ContentType.Text.Html)
                        indexStream.close()
                    } else {
                        call.respondText(
                            "React app not found",
                            ContentType.Text.Plain,
                            HttpStatusCode.NotFound
                        )
                    }
                }
            }

            get("/") {
                val indexStream = getResourceFromApk("react-build/index.html")
                if (indexStream != null) {
                    call.respondBytes(indexStream.readBytes(), ContentType.Text.Html)
                    indexStream.close()
                } else {
                    call.respondText(
                        "React app not found",
                        ContentType.Text.Plain,
                        HttpStatusCode.NotFound
                    )
                }
            }
        }
    }

    private suspend fun handleWebSocketConnection(session: DefaultWebSocketSession) {
        val sessionId = generateSessionId()
        webSocketSessions[sessionId] = session

        Log.i(TAG, "WebSocket client connected: $sessionId")

        try {
            // Send current settings to new client
            val allSettings = settingsRegistry.getAllSettings()
            sendToSession(session, StatusMessage.AllSettings(convertToJsonCompatibleMap(allSettings)))

            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        try {
                            val messageText = frame.readText()
                            val message = Json.decodeFromString<SettingsMessage>(messageText)
                            handleWebSocketMessage(session, message)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing WebSocket message", e)
                            sendToSession(session, StatusMessage.Error("Invalid message format"))
                        }
                    }

                    else -> { /* Handle other frame types if needed */
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connection error", e)
        } finally {
            webSocketSessions.remove(sessionId)
            Log.i(TAG, "WebSocket client disconnected: $sessionId")
        }
    }

    private suspend fun handleWebSocketMessage(
        session: DefaultWebSocketSession,
        message: SettingsMessage
    ) {
        when (message) {
            is SettingsMessage.GetAllSettings -> {
                val allSettings = settingsRegistry.getAllSettings()
                sendToSession(session, StatusMessage.AllSettings(convertToJsonCompatibleMap(allSettings)))
            }

            is SettingsMessage.UpdateSetting -> {
                val convertedValue = message.value.jsonPrimitive.let { primitive ->
                    primitive.booleanOrNull ?: primitive.intOrNull ?: primitive.doubleOrNull
                    ?: primitive.content
                }
                val success = if (message.category == "system") {
                    settingsRegistry.updateSystemSetting(message.key, convertedValue)
                } else {
                    // For app settings, we'd need to parse the category to get appId
                    // This is simplified - in a real implementation, the message format would include appId
                    false
                }

                if (!success) {
                    sendToSession(session, StatusMessage.Error("Failed to update setting"))
                }
            }

            is SettingsMessage.RegisterForUpdates -> {
                // Client registered for specific category updates
                // We'll broadcast all changes for now
                Log.i(TAG, "Client registered for updates: ${message.categories}")
            }
        }
    }

    private suspend fun broadcastSettingsUpdate(allSettings: Map<String, Any>) {
        val message = StatusMessage.AllSettings(convertToJsonCompatibleMap(allSettings))
        broadcast(message)
    }

    suspend fun broadcastAppStatusUpdate(appId: String, component: String, payload: Map<String, Any>) {
        val message = StatusMessage.AppStatusUpdate(
            appId = appId,
            component = component,
            data = convertValuesToJsonElements(payload)
        )
        broadcast(message)
        Log.d(TAG, "Broadcasted app status update: $appId.$component")
    }

    suspend fun broadcastAppEvent(appId: String, eventType: String, payload: Map<String, Any>) {
        val message = StatusMessage.AppEvent(
            appId = appId,
            eventType = eventType,
            payload = convertValuesToJsonElements(payload)
        )
        broadcast(message)
        Log.d(TAG, "Broadcasted app event: $appId.$eventType")
    }

    private suspend fun broadcast(message: StatusMessage) {
        try {
            val messageText = Json.encodeToString(message)
            webSocketSessions.values.forEach { session ->
                try {
                    session.send(Frame.Text(messageText))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send message to WebSocket client", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding message", e)
        }
    }

    private suspend fun sendToSession(session: DefaultWebSocketSession, message: StatusMessage) {
        try {
            val messageText = Json.encodeToString(message)
            session.send(Frame.Text(messageText))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to WebSocket session", e)
        }
    }

    private fun convertValuesToJsonElements(data: Map<String, Any>): Map<String, JsonElement> {
        return data.mapValues { (_, value) ->
            when (value) {
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        }
    }

    private fun convertToJsonCompatibleMap(input: Map<String, Any>): Map<String, Map<String, JsonElement>> {
        return input.mapValues { (_, value) ->
            when (value) {
                is Map<*, *> -> convertValuesToJsonElements(
                    value.mapKeys { it.key.toString() }.mapValues { it.value ?: "" }
                )

                else -> mapOf("value" to when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value) 
                    is String -> JsonPrimitive(value)
                    else -> JsonPrimitive(value.toString())
                })
            }
        }
    }

    private fun getResourceFromApk(resourcePath: String): InputStream? {
        return try {
            this::class.java.classLoader?.getResourceAsStream(resourcePath)
        } catch (e: Exception) {
            Log.w(TAG, "Error accessing resource: $resourcePath", e)
            null
        }
    }

    private fun getContentType(path: String): ContentType {
        return when {
            path.endsWith(".html") -> ContentType.Text.Html
            path.endsWith(".js") -> ContentType.Text.JavaScript
            path.endsWith(".css") -> ContentType.Text.CSS
            path.endsWith(".png") -> ContentType.Image.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
            path.endsWith(".svg") -> ContentType.Image.SVG
            path.endsWith(".ico") -> ContentType.parse("image/x-icon")
            path.endsWith(".json") -> ContentType.Application.Json
            path.endsWith(".map") -> ContentType.Application.Json
            else -> ContentType.Application.OctetStream
        }
    }

    private fun generateSessionId(): String {
        return "session_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }


}