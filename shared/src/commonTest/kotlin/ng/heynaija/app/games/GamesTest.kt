package ng.heynaija.app.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ng.heynaija.app.api.ChessChallenge
import ng.heynaija.app.api.ChessGame
import ng.heynaija.app.api.ChessOpponent
import ng.heynaija.app.api.ChessPlayer
import ng.heynaija.app.api.ChessRating
import ng.heynaija.app.api.ChessTimeControl
import ng.heynaija.app.games.chess.challengeGame
import ng.heynaija.app.games.chess.chessNotice
import ng.heynaija.app.games.chess.formatClock
import ng.heynaija.app.games.chess.lichessAnswer
import ng.heynaija.app.games.chess.ratingsLine
import ng.heynaija.app.games.chess.resultText
import ng.heynaija.app.games.chess.sideToMove

class GamesTest {
    @Test
    fun seasonsReadAsMonths() {
        assertEquals("November 2026", seasonName("2026-11"))
        assertEquals("January 2027", seasonName("2027-01"))
        assertEquals("All time", seasonName("all"))
    }

    @Test
    fun scoresAreWrittenAsTheWebWritesThem() {
        assertEquals("1,240 XP", scoreText(1240, "xp"))
        assertEquals("1 point", scoreText(1, "points"))
        assertEquals("1,000,000 points", scoreText(1_000_000, "points"))
        assertEquals("999", grouped(999))
    }

    @Test
    fun clocksReadAsTheWebsDo() {
        assertEquals("10+0", ChessTimeControl("10+0", "rapid", limit = 600, increment = 0).label)
        assertEquals("10+5", ChessTimeControl("10+5", "rapid", limit = 600, increment = 5).label)
        assertEquals("3 days a move", ChessTimeControl("3d", "correspondence", days = 3).label)
        assertEquals("1 day a move", ChessTimeControl("1d", "correspondence", days = 1).label)
        assertEquals("4:05", formatClock(245_000))
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("0:09.4", formatClock(9_450))
        assertEquals("0:00.0", formatClock(-20))
    }

    @Test
    fun ratingsListTheSpeedsLichessRatesInOrderAndMarkProvisionalOnes() {
        val ratings = mapOf("rapid" to ChessRating(1611, 3, provisional = true), "blitz" to ChessRating(1520, 40))
        assertEquals("Blitz 1520 · Rapid 1611?", ratingsLine(ratings))
        assertNull(ratingsLine(emptyMap()))
    }

    @Test
    fun aChallengeSaysTheGameAndYourColour() {
        val c = ChessChallenge("c1", ChessOpponent("bayo"), rated = true, speed = "rapid", time = "10+5", color = "random")
        assertEquals("Rated Rapid 10+5, you play either colour", challengeGame(c))
        assertEquals("Casual Correspondence 3 days a move, you play black", challengeGame(c.copy(rated = false, speed = "correspondence", time = "3 days a move", color = "black")))
    }

    @Test
    fun aFinishedGameSaysHowItEnded() {
        val game = ChessGame("abcd1234", white = ChessPlayer("ada", "Ada"), black = ChessPlayer("bayo"), finished = true)
        assertEquals("Ada won by checkmate", resultText(game.copy(status = "mate", winner = "white")))
        assertEquals("@bayo won on time", resultText(game.copy(status = "outoftime", winner = "black")))
        assertEquals("Drawn by stalemate", resultText(game.copy(status = "stalemate")))
        assertEquals("Game aborted", resultText(game.copy(status = "aborted")))
        assertEquals("white", sideToMove(emptyList()))
        assertEquals("black", sideToMove(listOf("e2e4")))
    }

    @Test
    fun lichessAnswerIsReadFromTheReturnAddressOnly() {
        val answer = lichessAnswer("ng.heynaija.app://lichess/return?code=liu_abc&state=app.xyz")!!
        assertEquals("liu_abc", answer.code)
        assertEquals("app.xyz", answer.state)
        assertNull(answer.error)
        assertEquals("access_denied", lichessAnswer("ng.heynaija.app://lichess/return?error=access_denied")!!.error)
        assertNull(lichessAnswer("https://heynaija.ng/games/chess/callback?code=x&state=y"))
        assertNull(lichessAnswer("ng.heynaija.app://elsewhere?code=x&state=y"))
    }

    @Test
    fun refusalsAreWordedAsOnTheWeb() {
        assertEquals("That Lichess account is already connected to another HeyNaija member.", chessNotice("already_linked"))
        assertNull(chessNotice("something_new"))
    }

    @Test
    fun competitionsReadAsTheWebsDo() {
        assertEquals("Chess · Arena", gameLabel("chess", "lichess_arena"))
        assertEquals("Chess · Swiss", gameLabel("chess", "lichess_swiss"))
        assertEquals("Keno", gameLabel("keno", "keno_points"))
        // Swiss points are kept doubled.
        assertEquals("4½ pts", competitionScore("lichess_swiss", 9))
        assertEquals("½ pts", competitionScore("lichess_swiss", 1))
        assertEquals("3 pts", competitionScore("lichess_swiss", 6))
        assertEquals("1,250 pts", competitionScore("lichess_arena", 1250))
        assertEquals("3+2", clockText(180, 2))
        assertEquals("Coming up", statusText("open"))
    }

    @Test
    fun aCompetitionSaysWhenItRuns() {
        val lagos = kotlinx.datetime.TimeZone.of("Africa/Lagos")
        assertEquals("22 Nov, 8:00 pm to 10:00 pm", whenText("2026-11-22T19:00:00.000Z", "2026-11-22T21:00:00.000Z", lagos))
        assertEquals("22 Nov, 8:00 pm to 23 Nov, 2:00 am", whenText("2026-11-22T19:00:00.000Z", "2026-11-23T01:00:00.000Z", lagos))
    }
}
