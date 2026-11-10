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
import kotlin.io.encoding.Base64
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
 * [ConfirmEmail]: Google or Apple vouched for an address that already has an
 * account, which confirms an emailed code in sign-in [flow] before the provider
 * is linked to it (infra/kratos/policy in the web repository). [NoAccount]: the
 * provider is fine, but nobody has joined with it; joining takes an invite.
 */
sealed interface AuthStep {
    data object Sent : AuthStep
    data class Done(val token: String) : AuthStep
    data class Refused(val messages: Map<String, String>) : AuthStep
    data object Expired : AuthStep
    data class ConfirmEmail(val flow: String, val email: String) : AuthStep
    data class NoAccount(val email: String?) : AuthStep
}

/**
 * An ID token from a platform's own sign-in for Kratos's [provider] ("google" on
 * Android, "apple" on iOS), and the nonce it was asked for with (Kratos checks
 * they match). Apple also gives a one-time [appleCode], which the server trades
 * for a token it can revoke when the account is erased (ADR 0026 in the web
 * repository).
 */
data class IdToken(val provider: String, val idToken: String, val nonce: String, val appleCode: String? = null)

/**
 * What a signup carries that is not an account trait: read by the registration
 * hooks (ADR 0022). The age is either the "I am 18 or older" box ([adultDeclared])
 * or, while the server asks for one, a date of birth.
 */
data class SignupExtras(val inviteCode: String, val birthDate: String? = null, val adultDeclared: Boolean = false)

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

    /** Signs in with Google or Apple: an ID token from the platform's own sign-in. */
    suspend fun loginWith(flow: String, token: IdToken): AuthStep =
        when (val step = submit("login", flow, null, idTokenBody(token))) {
            // Kratos's answer may not say which address it was; the token Kratos just checked does.
            is AuthStep.NoAccount -> if (step.email == null) AuthStep.NoAccount(emailIn(token.idToken)) else step
            else -> step
        }

    /** Joins with Google or Apple, which gives the email; the handle and the signup's extras come from the form. */
    suspend fun signupWith(flow: String, token: IdToken, handle: String, extras: SignupExtras): AuthStep =
        submit("registration", flow, null, idTokenBody(token, buildJsonObject {
            put("traits", buildJsonObject { put("handle", handle) })
            put("transient_payload", extras.payload())
        }))

    /** As infra/kratos/invite-check.jsonnet in the web repository reads them. */
    private fun SignupExtras.payload() = buildJsonObject {
        put("invite_code", inviteCode)
        birthDate?.let { put("birth_date", it) }
        if (adultDeclared) put("adult_declared", true)
    }

    private fun idTokenBody(token: IdToken, extra: JsonObject = JsonObject(emptyMap())) = JsonObject(
        buildJsonObject {
            put("method", "oidc")
            put("provider", token.provider)
            put("id_token", token.idToken)
            put("id_token_nonce", token.nonce)
        } + extra,
    )

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
        put("transient_payload", extras.payload())
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
        // Google or Apple, for an address that already has an account: a sign-in flow
        // comes back asking for an emailed code before the provider is linked to it.
        if (hasMessage(json, LINK_PROVIDER_MESSAGE)) {
            val id = (json["id"] as? JsonPrimitive)?.contentOrNull
            val email = nodeValue(json, "identifier")
            if (id != null && email != null) return AuthStep.ConfirmEmail(id, email)
        }
        // Signing in with Google or Apple that nobody has joined with: Kratos turns it into a
        // signup, which needs a handle neither gives. Kratos 26.2 then answers with the sign-in
        // flow carrying the signup's "Property handle is missing.", not with the signup flow.
        if (kind == "login" && (asksForHandle(json) || actionOf(json)?.contains("/self-service/registration") == true || redirectOf(json)?.contains("registration") == true)) {
            return AuthStep.NoAccount(nodeValue(json, "traits.email"))
        }
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
        /** HeyNaija's Kratos policy: "Google (or Apple) confirmed your email. Confirm your existing HeyNaija account with an email code…" */
        private const val LINK_PROVIDER_MESSAGE = 4900102L

        /** Kratos's "Property {property} is missing." */
        private const val REQUIRED_MESSAGE = 4000002L

        /** Whether Kratos said a handle is missing, for the form or any field: only a new account needs one. */
        private fun asksForHandle(flow: JsonObject): Boolean {
            val ui = flow["ui"] as? JsonObject ?: return false
            val lists = listOf(ui["messages"]) + (ui["nodes"] as? JsonArray).orEmpty().map { (it as? JsonObject)?.get("messages") }
            return lists.any { list ->
                (list as? JsonArray).orEmpty().any {
                    val message = it as? JsonObject
                    (message?.get("id") as? JsonPrimitive)?.contentOrNull?.toLongOrNull() == REQUIRED_MESSAGE &&
                        ((message?.get("context") as? JsonObject)?.get("property") as? JsonPrimitive)?.contentOrNull == "handle"
                }
            }
        }

        /** The address in an ID token's claims, to name it on the screen; null if it has none. */
        internal fun emailIn(idToken: String): String? = runCatching {
            val claims = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(idToken.split('.')[1]).decodeToString()
            (HeyNaijaApi.json.parseToJsonElement(claims).jsonObject["email"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()

        private fun hasMessage(flow: JsonObject, id: Long): Boolean =
            ((flow["ui"] as? JsonObject)?.get("messages") as? JsonArray).orEmpty().any {
                ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull?.toLongOrNull() == id
            }

        private fun actionOf(flow: JsonObject): String? = ((flow["ui"] as? JsonObject)?.get("action") as? JsonPrimitive)?.contentOrNull

        private fun redirectOf(json: JsonObject): String? =
            (json["redirect_browser_to"] as? JsonPrimitive)?.contentOrNull
                ?: (((json["error"] as? JsonObject)?.get("details") as? JsonObject)?.get("redirect_browser_to") as? JsonPrimitive)?.contentOrNull

        /** The value Kratos put in the field [name], such as the address to confirm. */
        private fun nodeValue(flow: JsonObject, name: String): String? {
            for (node in ((flow["ui"] as? JsonObject)?.get("nodes") as? JsonArray).orEmpty()) {
                val attributes = (node as? JsonObject)?.get("attributes") as? JsonObject ?: continue
                if ((attributes["name"] as? JsonPrimitive)?.contentOrNull != name) continue
                return (attributes["value"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            }
            return null
        }

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
