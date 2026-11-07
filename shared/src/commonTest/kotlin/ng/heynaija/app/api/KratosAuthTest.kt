package ng.heynaija.app.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class KratosAuthTest {
    private fun flow(state: String, vararg messages: Pair<String?, String>) = """
        { "id": "f1", "state": "$state", "ui": {
          "messages": [${messages.filter { it.first == null }.joinToString(",") { """{"type":"error","text":"${it.second}"}""" }}
            ${if (state == "sent_email") """${if (messages.any { it.first == null }) "," else ""}{"type":"info","text":"A code has been sent"}""" else ""}],
          "nodes": [${messages.filter { it.first != null }.joinToString(",") { """{"attributes":{"name":"${it.first}"},"messages":[{"type":"error","text":"${it.second}"}]}""" }}]
        } }
    """.trimIndent()

    @Test
    fun aSentCodeIsSentNotAnError() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to flow("sent_email") }

        assertEquals(AuthStep.Sent, server.kratos.sendLoginCode("f1", "ada@example.com"))
        val request = server.requests.single()
        assertEquals("https://test.heynaija.ng/kratos/self-service/login", request.url.toString().substringBefore('?'))
        assertEquals("f1", request.url.parameters["flow"])
    }

    @Test
    fun theFinishedSignupGivesTheTokenAndSendsTheInviteAndAge() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "session_token": "ory_st_new", "session": {} }""" }

        val step = server.kratos.finishSignup("f1", "ada@example.com", "ada", SignupExtras("HN-1234", "1990-04-12"), "123456")

        assertEquals(AuthStep.Done("ory_st_new"), step)
        val body = (server.requests.single().body as TextContent).text
        assertTrue(""""transient_payload":{"invite_code":"HN-1234","birth_date":"1990-04-12"}""" in body, body)
        assertTrue(""""traits":{"email":"ada@example.com","handle":"ada"}""" in body, body)
    }

    @Test
    fun fieldErrorsComeBackByField() = runTest {
        val server = FakeServer {
            HttpStatusCode.BadRequest to flow("choose_method", "traits.handle" to "That handle is taken", null to "That invite code is not valid any more.")
        }

        val step = server.kratos.sendSignupCode("f1", "ada@example.com", "ada", SignupExtras("HN-1", "1990-01-01"))

        assertEquals(
            AuthStep.Refused(mapOf("" to "That invite code is not valid any more.", "traits.handle" to "That handle is taken")),
            step,
        )
    }

    @Test
    fun anExpiredFlowStartsAgain() = runTest {
        val server = FakeServer { HttpStatusCode.Gone to """{ "error": { "id": "self_service_flow_expired", "message": "expired" } }""" }

        assertEquals(AuthStep.Expired, server.kratos.finishLogin("f1", "ada@example.com", "123456"))
    }
}
