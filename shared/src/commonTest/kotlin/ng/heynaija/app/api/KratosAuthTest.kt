package ng.heynaija.app.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.io.encoding.Base64
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

    private val google = IdToken("google", "eyJ.google.token", "n0nce")

    @Test
    fun googleSignsInWithItsIdTokenAndNonce() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "session_token": "ory_st_google", "session": {} }""" }

        assertEquals(AuthStep.Done("ory_st_google"), server.kratos.loginWith("f1", google))
        val body = (server.requests.single().body as TextContent).text
        assertTrue(""""method":"oidc","provider":"google","id_token":"eyJ.google.token","id_token_nonce":"n0nce"""" in body, body)
    }

    @Test
    fun appleSignsInWithItsIdTokenButNotItsCode() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "session_token": "ory_st_apple", "session": {} }""" }
        val apple = IdToken("apple", "eyJ.apple.token", "n0nce", appleCode = "c.one-time")

        assertEquals(AuthStep.Done("ory_st_apple"), server.kratos.loginWith("f1", apple))
        val body = (server.requests.single().body as TextContent).text
        assertTrue(""""method":"oidc","provider":"apple","id_token":"eyJ.apple.token","id_token_nonce":"n0nce"""" in body, body)
        // The code is for HeyNaija's API, not Kratos.
        assertTrue("c.one-time" !in body, body)
    }

    @Test
    fun googleForAnAccountThatSignsInByEmailAsksForItsCodeFirst() = runTest {
        // HeyNaija's Kratos policy hands back the sign-in flow with message 4900102 and the address to confirm.
        val server = FakeServer {
            HttpStatusCode.OK to """
                { "id": "f2", "state": "choose_method", "ui": {
                  "action": "https://test.heynaija.ng/kratos/self-service/login?flow=f2",
                  "messages": [{ "id": 4900102, "type": "info", "text": "Google confirmed your email." }],
                  "nodes": [{ "group": "code", "attributes": { "name": "identifier", "value": "ada@example.com" }, "messages": [] }]
                } }
            """.trimIndent()
        }

        assertEquals(AuthStep.ConfirmEmail("f2", "ada@example.com"), server.kratos.loginWith("f1", google))
    }

    @Test
    fun googleThatNobodyHasJoinedWithIsNoAccount() = runTest {
        // Kratos turns the sign-in into a signup, which wants a handle.
        val server = FakeServer {
            HttpStatusCode.BadRequest to """
                { "id": "r1", "state": "choose_method", "ui": {
                  "action": "https://test.heynaija.ng/kratos/self-service/registration?flow=r1",
                  "messages": [],
                  "nodes": [
                    { "attributes": { "name": "traits.email", "value": "new@example.com" }, "messages": [] },
                    { "attributes": { "name": "traits.handle" }, "messages": [{ "type": "error", "text": "Property handle is missing." }] }
                  ]
                } }
            """.trimIndent()
        }

        assertEquals(AuthStep.NoAccount("new@example.com"), server.kratos.loginWith("f1", google))
    }

    /** Kratos 26.2's answer then: the sign-in flow, with the signup's missing handle on a field of its own and no address. */
    private val handleMissing = """
        { "id": "f1", "type": "api", "state": "choose_method", "ui": {
          "action": "https://test.heynaija.ng/kratos/self-service/login?flow=f1",
          "messages": [],
          "nodes": [
            { "group": "default", "attributes": { "name": "csrf_token", "value": "" }, "messages": [] },
            { "group": "default", "attributes": { "name": "traits.handle", "type": "text" }, "messages": [
              { "id": 4000002, "type": "error", "text": "Property handle is missing.", "context": { "property": "handle" } }
            ] }
          ]
        } }
    """.trimIndent()

    @Test
    fun googleThatNobodyHasJoinedWithIsNoAccountAsKratosAnswersIt() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to handleMissing }
        val claims = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode("""{"sub":"1","email":"new@example.com"}""".encodeToByteArray())

        assertEquals(AuthStep.NoAccount("new@example.com"), server.kratos.loginWith("f1", IdToken("google", "eyJhbGciOiJSUzI1NiJ9.$claims.c2ln", "n0nce")))
    }

    @Test
    fun noAccountWithoutAnAddressStillSaysSo() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to handleMissing }

        assertEquals(AuthStep.NoAccount(null), server.kratos.loginWith("f1", google))
    }

    @Test
    fun aMissingHandleOnASignupIsTheFormsToShow() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to handleMissing.replace("/login?", "/registration?") }

        assertEquals(
            AuthStep.Refused(mapOf("traits.handle" to "Property handle is missing.")),
            server.kratos.signupWith("f1", google, "", SignupExtras("HN-1", adultDeclared = true)),
        )
    }

    @Test
    fun joiningWithGoogleSendsTheHandleInviteAndAgeButNoEmail() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to flow("choose_method", "traits.handle" to "That handle is taken") }

        val step = server.kratos.signupWith("r1", google, "ada", SignupExtras("HN-1234", "1990-04-12"))

        assertEquals(AuthStep.Refused(mapOf("traits.handle" to "That handle is taken")), step)
        val request = server.requests.single()
        assertEquals("https://test.heynaija.ng/kratos/self-service/registration", request.url.toString().substringBefore('?'))
        val body = (request.body as TextContent).text
        assertTrue(""""traits":{"handle":"ada"}""" in body, body)
        assertTrue(""""transient_payload":{"invite_code":"HN-1234","birth_date":"1990-04-12"}""" in body, body)
        assertTrue(""""id_token_nonce":"n0nce"""" in body, body)
    }

    @Test
    fun theAdultBoxGoesAsAdultDeclaredWithNoDate() = runTest {
        val server = FakeServer { HttpStatusCode.BadRequest to flow("sent_email") }

        server.kratos.sendSignupCode("f1", "ada@example.com", "ada", SignupExtras("HN-1234", adultDeclared = true))

        val body = (server.requests.single().body as TextContent).text
        assertTrue(""""transient_payload":{"invite_code":"HN-1234","adult_declared":true}""" in body, body)
    }
}
