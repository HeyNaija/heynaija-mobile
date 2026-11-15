package ng.heynaija.app.api

import kotlinx.serialization.Serializable

// Games (ADR 0028 and docs/games/GAMES.md in the web repository), by the API's own
// names (the Games types in apps/web/lib/api.ts). Every Games route answers 404 to a
// member the `games` flag is off for, so every screen here treats a 404 as "not
// available" rather than as a fault.

/** One game from the API's registry; `cover` is null until approved art exists. */
@Serializable
data class GameManifest(
    val slug: String,
    val name: String,
    val tagline: String = "",
    val description: String = "",
    val cover: String? = null,
    /** 'live', 'beta' or 'coming_soon'. */
    val status: String = "coming_soon",
    /** 'rating' or 'points'. */
    val rankingType: String = "points",
    val supportsCompetitions: Boolean = false,
) {
    val comingSoon get() = status == "coming_soon"
}

@Serializable
data class GameList(val games: List<GameManifest> = emptyList())

/** A Lichess rating for one speed, as Lichess last said. */
@Serializable
data class ChessRating(val rating: Int, val games: Int = 0, val provisional: Boolean = false)

/** The speeds Lichess rates, in the web's order, with their names. */
val CHESS_SPEEDS = listOf("blitz" to "Blitz", "rapid" to "Rapid", "classical" to "Classical", "correspondence" to "Correspondence")

fun speedName(speed: String) = CHESS_SPEEDS.firstOrNull { it.first == speed }?.second ?: speed.replaceFirstChar { it.uppercase() }

/** A clock the API allows for a challenge: minutes and seconds added a move, or days a move for correspondence. */
@Serializable
data class ChessTimeControl(
    val id: String,
    val speed: String,
    val limit: Int? = null,
    val increment: Int? = null,
    val days: Int? = null,
) {
    /** "10+5", "3 days a move". */
    val label: String
        get() = when {
            days != null -> if (days == 1) "1 day a move" else "$days days a move"
            limit != null -> "${limit / 60}+${increment ?: 0}"
            else -> id
        }
}

@Serializable
data class ChessOpponent(val handle: String, val displayName: String? = null, val avatar: String? = null)

@Serializable
data class ChessChallenge(
    val id: String,
    val opponent: ChessOpponent,
    /** Whether the viewer sent it. */
    val sent: Boolean = false,
    val rated: Boolean = false,
    val speed: String = "rapid",
    /** "10+5", "3 days a move". */
    val time: String = "",
    /** The colour the viewer plays: 'random', 'white' or 'black'. */
    val color: String = "random",
    /** 'pending', 'accepted', 'declined', 'cancelled', 'failed' or 'expired'. */
    val status: String = "pending",
    val createdAt: String = "",
    val expiresAt: String = "",
    /** The game on lichess.org, once accepted. */
    val gameUrl: String? = null,
    /** The game's id, for its page here, once accepted. */
    val gameId: String? = null,
    /** Whether The Arbiter opens a public thread in Chess Arena when it starts. */
    val openThread: Boolean = false,
)

@Serializable
data class ChessAccount(
    val username: String,
    val ratings: Map<String, ChessRating> = emptyMap(),
    val ratingsRefreshedAt: String? = null,
    val linkedAt: String = "",
    val url: String = "",
)

/** The chess page: the member's Lichess account, the clocks they may choose, and their challenges. */
@Serializable
data class ChessOverview(
    /** False where the server has no token key: linking cannot be offered. */
    val linkingAvailable: Boolean = false,
    val account: ChessAccount? = null,
    val timeControls: List<ChessTimeControl> = emptyList(),
    val incoming: List<ChessChallenge> = emptyList(),
    val outgoing: List<ChessChallenge> = emptyList(),
    val recent: List<ChessChallenge> = emptyList(),
)

/** Where to send the member to connect Lichess. [returnsTo] is 'app' when the server sends Lichess's answer back to the app. */
@Serializable
data class LichessLinkStart(val url: String, val returnsTo: String? = null)

/** Someone who may be challenged: they have connected Lichess. */
@Serializable
data class ChessCandidate(val id: String = "", val handle: String, val displayName: String? = null, val avatar: String? = null, val lichess: String = "")

@Serializable
data class ChessCandidates(val members: List<ChessCandidate> = emptyList())

@Serializable
data class ChessGameRef(val id: String)

/** A member's Lichess account for their profile, and the game they are playing now. */
@Serializable
data class ChessMember(
    val username: String,
    val ratings: Map<String, ChessRating> = emptyMap(),
    val url: String = "",
    val canChallenge: Boolean = false,
    val playing: ChessGameRef? = null,
)

@Serializable
data class ChessPlayer(
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val rating: Int? = null,
    val provisional: Boolean = false,
)

@Serializable
data class LiveChessGame(
    val id: String,
    val white: ChessPlayer,
    val black: ChessPlayer,
    val speed: String = "",
    val time: String = "",
    val rated: Boolean = false,
    val startedAt: String = "",
    val moveCount: Int = 0,
)

@Serializable
data class LiveChessGames(val games: List<LiveChessGame> = emptyList())

@Serializable
data class ChessClock(val limit: Int, val increment: Int)

@Serializable
data class RatingChange(val before: Int? = null, val after: Int? = null)

/** A game as the API shows it; `version` goes up with every change. */
@Serializable
data class ChessGame(
    val id: String,
    val white: ChessPlayer,
    val black: ChessPlayer,
    /** The colour the viewer plays: 'white', 'black', or null for a spectator. */
    val you: String? = null,
    val rated: Boolean = false,
    val speed: String = "",
    val time: String = "",
    val clock: ChessClock? = null,
    val days: Int? = null,
    /** Lichess's status: 'started', 'mate', 'resign', 'draw', 'outoftime', 'aborted' and so on. */
    val status: String = "started",
    val finished: Boolean = false,
    val winner: String? = null,
    /** Every move so far, in UCI. */
    val moves: List<String> = emptyList(),
    val whiteMs: Long? = null,
    val blackMs: Long? = null,
    val clockAt: String? = null,
    val drawOffer: String? = null,
    val version: Long = 0,
    val startedAt: String = "",
    val finishedAt: String? = null,
    val threadPostId: String? = null,
    val resultPostId: String? = null,
    val ratingsAfter: Map<String, RatingChange>? = null,
    val lichessUrl: String = "",
)

// Seasons, the Game Score and leaderboards

@Serializable
data class GameBoardRow(
    val rank: Int,
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val value: Int,
    val you: Boolean = false,
)

/** A leaderboard: [board] is 'score', 'chess' or 'keno', [period] "2026-11" or "all". */
@Serializable
data class GameBoard(
    val board: String,
    /** 'xp' or 'points'. */
    val unit: String = "xp",
    val period: String = "all",
    /** 'everyone', 'following' or 'community'. */
    val filter: String = "everyone",
    val community: String? = null,
    val rows: List<GameBoardRow> = emptyList(),
    val you: GameBoardRow? = null,
)

@Serializable
data class GameSeasons(val current: String, val seasons: List<String> = emptyList())

@Serializable
data class ChessXp(val finished: Int = 0, val win: Int = 0, val draw: Int = 0)

@Serializable
data class KenoXp(val round: Int = 0, val pointsPerXp: Int = 0, val pointsXpMax: Int = 0)

@Serializable
data class CompetitionXp(val entered: Int = 0, val places: List<Int> = emptyList(), val topTen: Int = 0)

@Serializable
data class XpRules(val chess: ChessXp = ChessXp(), val keno: KenoXp = KenoXp(), val competition: CompetitionXp = CompetitionXp())

/** Every scoring rule, as the API scores, so the app shows the numbers the scoring uses. */
@Serializable
data class GameScoring(
    val xp: XpRules = XpRules(),
    /** Each game's competition formats, and what each means. */
    val formats: Map<String, Map<String, String>> = emptyMap(),
    val season: String = "",
)
