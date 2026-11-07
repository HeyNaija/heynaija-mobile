package ng.heynaija.app.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Where a sign-in or signup stands after a step. [Sent]: the code is on its way.
 * [Done]: here is the session token. [Refused]: Kratos's words, for the form,
 * by field (`""` for the form as a whole). [Expired]: start again.
 */
sealed interface AuthStep {
    data object Sent : AuthStep
    data class Done(val token: String) : AuthStep
    data class Refused(val messages: Map<String, String>) : AuthStep
    data object Expired : AuthStep
}

/** What a signup carries that is not an account trait: read by the registration hooks (ADR 0022). */
data class SignupExtras(val inviteCode: String, val birthDate: String)

/**
 * Kratos's native flows for the emailed code (ADR 0022 in the web repository):
 * sign in, sign up, sign in again, and sign out. They answer JSON, use no
 * cookies, and end with a session token.
 */
class KratosAuth(
    private val server: () -> Server = { Servers.current },
    engine: HttpClientEngine? = null,
) {
    private val client = (engine?.let { HttpClient(it) { setUp() } } ?: HttpClient { setUp() })

    private fun io.ktor.client.HttpClientConfig<*>.setUp() {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
        }
    }

    private val base get() = server().kratos

    /** A new sign-in flow's ID. With [token], a sign-in again of that session (for steps that need a recent one). */
    suspend fun startLogin(token: String? = null): String = flowId(
        client.get("$base/self-service/login/api") {
            if (token != null) {
                parameter("refresh", "true")
                header("X-Session-Token", token)
            }
        },
    )

    suspend fun startSignup(): String = flowId(client.get("$base/self-service/registration/api"))

    /** Emails a sign-in code to [email]. */
    suspend fun sendLoginCode(flow: String, email: String, token: String? = null): AuthStep =
        submit("login", flow, token, buildJsonObject {
            put("method", "code")
            put("identifier", email)
        })

    suspend fun finishLogin(flow: String, email: String, code: String, token: String? = null): AuthStep =
        submit("login", flow, token, buildJsonObject {
            put("method", "code")
            put("identifier", email)
            put("code", code)
        })

    /** Emails a signup code; the invite and age are checked when the code comes back. */
    suspend fun sendSignupCode(flow: String, email: String, handle: String, extras: SignupExtras): AuthStep =
        submit("registration", flow, null, signupBody(email, handle, extras, code = null))

    suspend fun finishSignup(flow: String, email: String, handle: String, extras: SignupExtras, code: String): AuthStep =
        submit("registration", flow, null, signupBody(email, handle, extras, code))

    /** Ends the session in Kratos. Already ended is fine. */
    suspend fun logout(token: String) {
        runCatching {
            client.delete("$base/self-service/logout/api") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("session_token", token) }.toString())
            }
        }
    }

    private fun signupBody(email: String, handle: String, extras: SignupExtras, code: String?) = buildJsonObject {
        put("method", "code")
        put("traits", buildJsonObject {
            put("email", email)
            put("handle", handle)
        })
        put("transient_payload", buildJsonObject {
            put("invite_code", extras.inviteCode)
            put("birth_date", extras.birthDate)
        })
        if (code != null) put("code", code)
    }

    private suspend fun submit(kind: String, flow: String, token: String?, body: JsonObject): AuthStep {
        val response = client.post("$base/self-service/$kind") {
            parameter("flow", flow)
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json")
            if (token != null) header("X-Session-Token", token)
            setBody(body.toString())
        }
        val json = runCatching { HeyNaijaApi.json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            ?: return AuthStep.Refused(mapOf("" to "Something went wrong. Try again in a moment."))
        val token = (json["session_token"] as? JsonPrimitive)?.contentOrNull
        if (response.status.value == 200 && token != null) return AuthStep.Done(token)
        // A sign-in again keeps the session's token, so the answer has none.
        if (response.status.value == 200 && kind == "login") return AuthStep.Done("")
        if (response.status.value == 410 || response.status.value == 403) return AuthStep.Expired
        val error = json["error"] as? JsonObject
        if (error != null) {
            val id = (error["id"] as? JsonPrimitive)?.contentOrNull
            if (id == "self_service_flow_expired") return AuthStep.Expired
            val reason = (error["reason"] as? JsonPrimitive)?.contentOrNull ?: (error["message"] as? JsonPrimitive)?.contentOrNull
            return AuthStep.Refused(mapOf("" to (reason ?: "Something went wrong. Try again in a moment.")))
        }
        val messages = messagesOf(json)
        val state = (json["state"] as? JsonPrimitive)?.contentOrNull
        // "A code has been sent" comes back as an info message on a 400, with no errors.
        if (state == "sent_email" && messages.isEmpty()) return AuthStep.Sent
        return if (messages.isEmpty()) AuthStep.Sent else AuthStep.Refused(messages)
    }

    private suspend fun flowId(response: HttpResponse): String {
        val json = HeyNaijaApi.json.parseToJsonElement(response.bodyAsText()).jsonObject
        return (json["id"] as? JsonPrimitive)?.contentOrNull
            ?: throw ApiException(response.status.value, "flow_unavailable", "Sign-in is not available right now.")
    }

    companion object {
        /** Kratos's error messages, by field name (`""` for the form), leaving out its "info" notes. */
        internal fun messagesOf(flow: JsonObject): Map<String, String> {
            val out = linkedMapOf<String, String>()
            val ui = flow["ui"] as? JsonObject ?: return out
            fun read(list: Any?, field: String) {
                for (message in (list as? JsonArray).orEmpty()) {
                    val m = message as? JsonObject ?: continue
                    if ((m["type"] as? JsonPrimitive)?.contentOrNull != "error") continue
                    val text = (m["text"] as? JsonPrimitive)?.contentOrNull ?: continue
                    if (field !in out) out[field] = text
                }
            }
            read(ui["messages"], "")
            for (node in (ui["nodes"] as? JsonArray).orEmpty()) {
                val n = node as? JsonObject ?: continue
                val name = ((n["attributes"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull ?: ""
                read(n["messages"], name)
            }
            return out
        }
    }
}
