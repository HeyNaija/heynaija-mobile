package ng.heynaija.app.games.chess

// The rules of standard chess, enough for the board to show a game and offer only
// legal moves. Lichess is the judge: it plays every move, and a move it refuses goes
// back on the board. The web does the same with chessops; there is no Kotlin library
// for it here, so this is written for the app and checked against the standard move
// counts (perft) in RulesTest.
//
// Squares are 0 to 63: a1 is 0, h1 is 7, a8 is 56.

enum class Side {
    White, Black;

    val other: Side get() = if (this == White) Black else White

    /** 'white' or 'black', as the API names them. */
    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String?): Side? = entries.firstOrNull { it.key == key }
    }
}

enum class Kind(val letter: Char) {
    King('k'), Queen('q'), Rook('r'), Bishop('b'), Knight('n'), Pawn('p');

    companion object {
        fun of(letter: Char): Kind? = entries.firstOrNull { it.letter == letter.lowercaseChar() }
    }
}

data class Piece(val side: Side, val kind: Kind)

fun file(square: Int) = square % 8
fun rank(square: Int) = square / 8
fun squareName(square: Int) = "${'a' + file(square)}${rank(square) + 1}"
fun squareOf(name: String): Int? {
    if (name.length != 2) return null
    val f = name[0] - 'a'
    val r = name[1] - '1'
    return if (f in 0..7 && r in 0..7) r * 8 + f else null
}

data class Move(val from: Int, val to: Int, val promotion: Kind? = null) {
    /** As Lichess writes it: "e2e4", "e7e8q", and the king's two squares for castling. */
    val uci: String get() = squareName(from) + squareName(to) + (promotion?.letter ?: "")
}

private const val WHITE_KINGSIDE = 1
private const val WHITE_QUEENSIDE = 2
private const val BLACK_KINGSIDE = 4
private const val BLACK_QUEENSIDE = 8

private val KNIGHT_STEPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
private val KING_STEPS = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
private val ROOK_RAYS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val BISHOP_RAYS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

/** The square [df] files and [dr] ranks from [square], or -1 off the board. */
private fun step(square: Int, df: Int, dr: Int): Int {
    val f = file(square) + df
    val r = rank(square) + dr
    return if (f in 0..7 && r in 0..7) r * 8 + f else -1
}

/** A position: where every piece stands, whose turn it is, and what castling and en passant allow. */
class Position private constructor(
    private val board: Array<Piece?>,
    val turn: Side,
    private val castling: Int,
    /** The square a pawn may capture onto en passant, after a two-square pawn move. */
    val enPassant: Int?,
    val halfmoves: Int,
    val fullmoves: Int,
) {
    operator fun get(square: Int): Piece? = board[square]

    fun kingSquare(side: Side): Int = board.indexOfFirst { it?.side == side && it.kind == Kind.King }

    /** Whether [side]'s king is attacked. */
    fun inCheck(side: Side = turn): Boolean {
        val king = kingSquare(side)
        return king >= 0 && attacked(king, side.other)
    }

    /** Whether any piece of [by] attacks [square]. */
    fun attacked(square: Int, by: Side): Boolean {
        // A pawn of [by] attacks diagonally forward, so look diagonally backward from the square.
        val back = if (by == Side.White) -1 else 1
        for (df in listOf(-1, 1)) {
            val s = step(square, df, back)
            if (s >= 0 && board[s] == Piece(by, Kind.Pawn)) return true
        }
        for ((df, dr) in KNIGHT_STEPS) {
            val s = step(square, df, dr)
            if (s >= 0 && board[s] == Piece(by, Kind.Knight)) return true
        }
        for ((df, dr) in KING_STEPS) {
            val s = step(square, df, dr)
            if (s >= 0 && board[s] == Piece(by, Kind.King)) return true
        }
        for ((rays, kinds) in listOf(ROOK_RAYS to setOf(Kind.Rook, Kind.Queen), BISHOP_RAYS to setOf(Kind.Bishop, Kind.Queen))) {
            for ((df, dr) in rays) {
                var s = step(square, df, dr)
                while (s >= 0) {
                    val p = board[s]
                    if (p != null) {
                        if (p.side == by && p.kind in kinds) return true
                        break
                    }
                    s = step(s, df, dr)
                }
            }
        }
        return false
    }

    /** Every move the rules allow the side to move. */
    fun legalMoves(): List<Move> = pseudoMoves().filter { !play(it).inCheck(turn) }

    /** The legal moves from [square]. */
    fun legalMovesFrom(square: Int): List<Move> = legalMoves().filter { it.from == square }

    private fun pseudoMoves(): List<Move> {
        val moves = ArrayList<Move>(48)
        for (from in 0 until 64) {
            val piece = board[from] ?: continue
            if (piece.side != turn) continue
            when (piece.kind) {
                Kind.Pawn -> pawnMoves(from, moves)
                Kind.Knight -> steps(from, KNIGHT_STEPS, moves)
                Kind.King -> {
                    steps(from, KING_STEPS, moves)
                    castlingMoves(from, moves)
                }
                Kind.Bishop -> rays(from, BISHOP_RAYS, moves)
                Kind.Rook -> rays(from, ROOK_RAYS, moves)
                Kind.Queen -> rays(from, ROOK_RAYS + BISHOP_RAYS, moves)
            }
        }
        return moves
    }

    private fun pawnMoves(from: Int, moves: MutableList<Move>) {
        val forward = if (turn == Side.White) 1 else -1
        val startRank = if (turn == Side.White) 1 else 6
        val lastRank = if (turn == Side.White) 7 else 0
        fun add(to: Int) {
            if (rank(to) == lastRank) {
                for (kind in listOf(Kind.Queen, Kind.Rook, Kind.Bishop, Kind.Knight)) moves += Move(from, to, kind)
            } else {
                moves += Move(from, to)
            }
        }
        val one = step(from, 0, forward)
        if (one >= 0 && board[one] == null) {
            add(one)
            val two = step(from, 0, 2 * forward)
            if (rank(from) == startRank && board[two] == null) moves += Move(from, two)
        }
        for (df in listOf(-1, 1)) {
            val to = step(from, df, forward)
            if (to < 0) continue
            val target = board[to]
            if (target != null && target.side != turn) add(to)
            else if (target == null && to == enPassant) moves += Move(from, to)
        }
    }

    private fun steps(from: Int, offsets: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        for ((df, dr) in offsets) {
            val to = step(from, df, dr)
            if (to >= 0 && board[to]?.side != turn) moves += Move(from, to)
        }
    }

    private fun rays(from: Int, directions: List<Pair<Int, Int>>, moves: MutableList<Move>) {
        for ((df, dr) in directions) {
            var to = step(from, df, dr)
            while (to >= 0) {
                val target = board[to]
                if (target == null) {
                    moves += Move(from, to)
                } else {
                    if (target.side != turn) moves += Move(from, to)
                    break
                }
                to = step(to, df, dr)
            }
        }
    }

    private fun castlingMoves(from: Int, moves: MutableList<Move>) {
        val home = if (turn == Side.White) 4 else 60
        if (from != home || inCheck(turn)) return
        val (kingside, queenside) = if (turn == Side.White) WHITE_KINGSIDE to WHITE_QUEENSIDE else BLACK_KINGSIDE to BLACK_QUEENSIDE
        val rook = Piece(turn, Kind.Rook)
        if (castling and kingside != 0 && board[home + 3] == rook && board[home + 1] == null && board[home + 2] == null &&
            !attacked(home + 1, turn.other) && !attacked(home + 2, turn.other)
        ) {
            moves += Move(home, home + 2)
        }
        if (castling and queenside != 0 && board[home - 4] == rook && board[home - 1] == null && board[home - 2] == null && board[home - 3] == null &&
            !attacked(home - 1, turn.other) && !attacked(home - 2, turn.other)
        ) {
            moves += Move(home, home - 2)
        }
    }

    /** The position after [move], which is not checked for legality. */
    fun play(move: Move): Position {
        val next = board.copyOf()
        val piece = next[move.from] ?: return this
        val captured = next[move.to]
        next[move.from] = null
        next[move.to] = if (move.promotion != null && piece.kind == Kind.Pawn) Piece(piece.side, move.promotion) else piece
        var enPassantNext: Int? = null
        if (piece.kind == Kind.Pawn) {
            // Taken en passant: the pawn that passed is on the mover's side of the target square.
            if (move.to == enPassant && captured == null && file(move.from) != file(move.to)) {
                next[move.to + if (piece.side == Side.White) -8 else 8] = null
            }
            if (kotlin.math.abs(move.to - move.from) == 16) enPassantNext = (move.from + move.to) / 2
        }
        if (piece.kind == Kind.King && kotlin.math.abs(move.to - move.from) == 2) {
            // The rook comes round the king.
            val (rookFrom, rookTo) = if (move.to > move.from) (move.from + 3) to (move.from + 1) else (move.from - 4) to (move.from - 1)
            next[rookTo] = next[rookFrom]
            next[rookFrom] = null
        }
        var rights = castling
        fun lose(square: Int) {
            rights = rights and when (square) {
                4 -> (WHITE_KINGSIDE or WHITE_QUEENSIDE).inv()
                60 -> (BLACK_KINGSIDE or BLACK_QUEENSIDE).inv()
                0 -> WHITE_QUEENSIDE.inv()
                7 -> WHITE_KINGSIDE.inv()
                56 -> BLACK_QUEENSIDE.inv()
                63 -> BLACK_KINGSIDE.inv()
                else -> 0.inv()
            }
        }
        lose(move.from)
        lose(move.to)
        return Position(
            next,
            turn.other,
            rights,
            enPassantNext,
            if (piece.kind == Kind.Pawn || captured != null) 0 else halfmoves + 1,
            if (turn == Side.Black) fullmoves + 1 else fullmoves,
        )
    }

    /**
     * The move a UCI string names here, if it is legal: "e2e4", "e7e8q", and castling
     * written either as the king's two squares or as the king onto its own rook.
     */
    fun moveOf(uci: String): Move? {
        if (uci.length !in 4..5) return null
        val from = squareOf(uci.substring(0, 2)) ?: return null
        var to = squareOf(uci.substring(2, 4)) ?: return null
        val promotion = uci.getOrNull(4)?.let { Kind.of(it) ?: return null }
        val piece = board[from]
        if (piece?.kind == Kind.King && board[to] == Piece(piece.side, Kind.Rook)) to = if (to > from) from + 2 else from - 2
        return legalMoves().firstOrNull { it.from == from && it.to == to && it.promotion == promotion }
    }

    /** [move] in standard algebraic notation, with + or # after it: "Nf3", "exd5", "O-O", "e8=Q+". */
    fun san(move: Move): String {
        val piece = board[move.from] ?: return move.uci
        val after = play(move)
        val suffix = when {
            !after.inCheck() -> ""
            after.legalMoves().isEmpty() -> "#"
            else -> "+"
        }
        if (piece.kind == Kind.King && kotlin.math.abs(move.to - move.from) == 2) {
            return (if (move.to > move.from) "O-O" else "O-O-O") + suffix
        }
        val capture = board[move.to] != null || (piece.kind == Kind.Pawn && file(move.from) != file(move.to))
        val text = StringBuilder()
        if (piece.kind == Kind.Pawn) {
            if (capture) text.append('a' + file(move.from)).append('x')
            text.append(squareName(move.to))
            move.promotion?.let { text.append('=').append(it.letter.uppercaseChar()) }
        } else {
            text.append(piece.kind.letter.uppercaseChar())
            // Another piece of the same kind that could also go there: say which, by file, rank, or both.
            val rivals = legalMoves().filter { it.to == move.to && it.from != move.from && board[it.from] == piece }
            if (rivals.isNotEmpty()) {
                when {
                    rivals.none { file(it.from) == file(move.from) } -> text.append('a' + file(move.from))
                    rivals.none { rank(it.from) == rank(move.from) } -> text.append('1' + rank(move.from))
                    else -> text.append(squareName(move.from))
                }
            }
            if (capture) text.append('x')
            text.append(squareName(move.to))
        }
        return text.append(suffix).toString()
    }

    /** Whether the side to move has no legal move: checkmate if in check, stalemate if not. */
    val stuck: Boolean get() = legalMoves().isEmpty()

    companion object {
        const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        val start: Position by lazy { fromFen(START)!! }

        /** A position from FEN, or null if it cannot be read. */
        fun fromFen(fen: String): Position? {
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.size < 4) return null
            val board = arrayOfNulls<Piece>(64)
            val ranks = parts[0].split('/')
            if (ranks.size != 8) return null
            for ((i, row) in ranks.withIndex()) {
                val r = 7 - i
                var f = 0
                for (c in row) {
                    if (c.isDigit()) {
                        f += c - '0'
                    } else {
                        val kind = Kind.of(c) ?: return null
                        if (f > 7) return null
                        board[r * 8 + f] = Piece(if (c.isUpperCase()) Side.White else Side.Black, kind)
                        f++
                    }
                }
                if (f != 8) return null
            }
            val turn = if (parts[1] == "b") Side.Black else Side.White
            var rights = 0
            if ('K' in parts[2]) rights = rights or WHITE_KINGSIDE
            if ('Q' in parts[2]) rights = rights or WHITE_QUEENSIDE
            if ('k' in parts[2]) rights = rights or BLACK_KINGSIDE
            if ('q' in parts[2]) rights = rights or BLACK_QUEENSIDE
            val ep = if (parts[3] == "-") null else squareOf(parts[3])
            return Position(board, turn, rights, ep, parts.getOrNull(4)?.toIntOrNull() ?: 0, parts.getOrNull(5)?.toIntOrNull() ?: 1)
        }
    }
}

/**
 * A game replayed from its moves, as the API sends them: the position after each
 * move (index 0 is the start), each move's SAN, and each move itself. Stops at the
 * first move it cannot read, which would mean the app and Lichess disagree.
 */
class Replay(uci: List<String>) {
    val positions: List<Position>
    val moves: List<Move>
    val san: List<String>

    init {
        val positions = mutableListOf(Position.start)
        val moves = mutableListOf<Move>()
        val san = mutableListOf<String>()
        for (text in uci) {
            val here = positions.last()
            val move = here.moveOf(text) ?: break
            san += here.san(move)
            moves += move
            positions += here.play(move)
        }
        this.positions = positions
        this.moves = moves
        this.san = san
    }

    val current: Position get() = positions.last()
}

/** The number of positions [depth] moves on from [position]: the standard check of a move generator. */
fun perft(position: Position, depth: Int): Long {
    if (depth == 0) return 1
    val moves = position.legalMoves()
    if (depth == 1) return moves.size.toLong()
    return moves.sumOf { perft(position.play(it), depth - 1) }
}
