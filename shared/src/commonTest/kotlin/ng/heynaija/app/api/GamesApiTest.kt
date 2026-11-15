package ng.heynaija.app.api

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GamesApiTest {
    /** The chess page as the API sends it (chess.service.ts `overview`), with a field the app does not read. */
    private val overview = """
        {
          "linkingAvailable": true,
          "account": { "username": "AdaPlays", "ratings": { "blitz": { "rating": 1520, "games": 40, "provisional": false }, "rapid": { "rating": 1611, "games": 3, "provisional": true } },
                       "ratingsRefreshedAt": "2026-11-15T10:00:00.000Z", "linkedAt": "2026-11-01T10:00:00.000Z", "url": "https://lichess.org/@/AdaPlays" },
          "timeControls": [ { "id": "3+2", "speed": "blitz", "limit": 180, "increment": 2 }, { "id": "3d", "speed": "correspondence", "days": 3 } ],
          "incoming": [ { "id": "c1", "opponent": { "handle": "bayo", "displayName": "Bayo", "avatar": null }, "sent": false, "rated": true, "speed": "rapid",
                          "time": "10+5", "color": "white", "status": "pending", "createdAt": "2026-11-15T10:00:00.000Z", "expiresAt": "2026-11-16T10:00:00.000Z",
                          "gameUrl": null, "gameId": null, "openThread": true, "somethingNew": 1 } ],
          "outgoing": [], "recent": []
        }
    """.trimIndent()

    @Test
    fun readsTheChessPage() = runTest {
        val server = FakeServer { HttpStatusCode.OK to overview }
        val chess = server.api.chess()
        assertEquals("AdaPlays", chess.account?.username)
        assertEquals(1611, chess.account?.ratings?.get("rapid")?.rating)
        assertEquals(listOf("3+2", "3 days a move"), chess.timeControls.map { it.label })
        assertEquals("bayo", chess.incoming.single().opponent.handle)
        assertEquals("https://test.heynaija.ng/api/v1/games/chess", server.requests.single().url.toString())
    }

    @Test
    fun connectingLichessAsksToComeBackToTheApp() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "url": "https://lichess.org/oauth?x=1", "returnsTo": "app" }""" }
        val start = server.api.startLichessLink()
        assertEquals("app", start.returnsTo)
        assertEquals("https://test.heynaija.ng/api/v1/games/chess/link?return=app", server.requests.single().url.toString())
        // An older server answers with the website's return only.
        val older = FakeServer { HttpStatusCode.OK to """{ "url": "https://lichess.org/oauth?x=1" }""" }
        assertNull(older.api.startLichessLink().returnsTo)
    }

    @Test
    fun aChallengeSendsWhatTheApiAsksFor() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "id": "c2", "opponent": { "handle": "bayo" }, "sent": true, "status": "pending" }""" }
        server.api.challenge("bayo", rated = false, timeControl = "10+5", color = "black", openThread = true)
        val request = server.requests.single()
        assertEquals("https://test.heynaija.ng/api/v1/games/chess/challenges", request.url.toString())
        assertEquals("""{"handle":"bayo","rated":false,"timeControl":"10+5","color":"black","openThread":true}""", (request.body as TextContent).text)
    }

    @Test
    fun gamesSwitchedOffIsA404() = runTest {
        val server = FakeServer { HttpStatusCode.NotFound to """{"message":"Not Found","statusCode":404}""" }
        assertEquals(404, assertFailsWith<ApiException> { server.api.games() }.status)
    }

    @Test
    fun aLeaderboardAsksForTheBoardSeasonAndWhose() = runTest {
        val server = FakeServer {
            HttpStatusCode.OK to """{ "board": "score", "unit": "xp", "period": "2026-11", "filter": "community", "community": "lagos",
                "rows": [ { "rank": 1, "handle": "ada", "displayName": "Ada", "avatar": null, "value": 120, "you": true } ], "you": { "rank": 1, "handle": "ada", "displayName": "Ada", "avatar": null, "value": 120, "you": true } }"""
        }
        val board = server.api.gameBoard("score", filter = "community", community = "lagos")
        assertEquals(120, board.you?.value)
        assertEquals("https://test.heynaija.ng/api/v1/games/leaderboard?board=score&filter=community&limit=50&community=lagos", server.requests.single().url.toString())
    }

    @Test
    fun aChessNotificationCarriesItsChallengeOrGame() = runTest {
        val server = FakeServer {
            HttpStatusCode.OK to """{ "notifications": [
                { "id": "n1", "kind": "chess_challenge", "createdAt": "2026-11-15T10:00:00.000Z", "read": false, "challenge": { "id": "c1", "phrase": "a rated Rapid game (10+5)" }, "game": null },
                { "id": "n2", "kind": "chess_game_started", "createdAt": "2026-11-15T10:00:00.000Z", "read": false, "challenge": null, "game": { "id": "aBcD1234" } },
                { "id": "n3", "kind": "like", "createdAt": "2026-11-15T10:00:00.000Z", "read": true } ], "cursor": null }"""
        }
        val page = server.api.notifications()
        assertEquals("a rated Rapid game (10+5)", page.notifications[0].challenge?.phrase)
        assertEquals("aBcD1234", page.notifications[1].game?.id)
        assertNull(page.notifications[2].challenge)
    }

    @Test
    fun theChessSwitchSendsBothKindsAtOnce() = runTest {
        val server = FakeServer { HttpStatusCode.OK to """{ "chess_challenge": false, "chess_game_started": false }""" }
        server.api.setNotificationPreferences(mapOf("chess_challenge" to false, "chess_game_started" to false))
        assertEquals("""{"chess_challenge":false,"chess_game_started":false}""", (server.requests.single().body as TextContent).text)
    }
}
