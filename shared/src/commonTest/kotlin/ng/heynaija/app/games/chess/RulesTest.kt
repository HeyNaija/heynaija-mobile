package ng.heynaija.app.games.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesTest {
    private fun fen(text: String) = Position.fromFen(text)!!

    // The standard move counts (chessprogramming.org/Perft_Results), which only a
    // move generator that gets castling, en passant, promotion and pins right reaches.

    @Test
    fun theStartingPosition() {
        assertEquals(listOf(20L, 400L, 8902L, 197281L), (1..4).map { perft(Position.start, it) })
    }

    @Test
    fun kiwipeteCastlingPinsAndEnPassant() {
        val p = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(listOf(48L, 2039L, 97862L), (1..3).map { perft(p, it) })
    }

    @Test
    fun position3EnPassantAndDiscoveredChecks() {
        val p = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(listOf(14L, 191L, 2812L, 43238L), (1..4).map { perft(p, it) })
    }

    @Test
    fun position4PromotionsAndCastlingWhileAttacked() {
        val p = fen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(listOf(6L, 264L, 9467L), (1..3).map { perft(p, it) })
    }

    @Test
    fun position5() {
        val p = fen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(listOf(44L, 1486L, 62379L), (1..3).map { perft(p, it) })
    }

    // Reading Lichess's moves, and writing them as the move list shows them.

    @Test
    fun aGameReplaysWithItsNotation() {
        val game = Replay("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5c6 d7c6 e1g1 f7f6".split(' '))
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O", "f6"), game.san)
        assertEquals(Side.White, game.current.turn)
        assertEquals(Piece(Side.White, Kind.King), game.current[squareOf("g1")!!])
        assertEquals(Piece(Side.White, Kind.Rook), game.current[squareOf("f1")!!])
    }

    @Test
    fun castlingWrittenAsTheKingOntoItsRookIsReadToo() {
        val p = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(Move(4, 6), p.moveOf("e1h1"))
        assertEquals(Move(4, 2), p.moveOf("e1a1"))
        assertEquals("O-O-O", p.san(Move(4, 2)))
    }

    @Test
    fun noCastlingThroughCheckOrAfterTheKingHasMoved() {
        // The black rook on f8 covers f1: no castling kingside.
        val attacked = fen("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertNull(attacked.moveOf("e1g1"))
        assertNotNull(attacked.moveOf("e1c1"))
        val moved = Replay("e2e4 e7e5 e1e2 e8e7 e2e1 e7e8".split(' ')).current
        assertTrue(moved.legalMoves().none { it.from == 4 && it.to == 6 })
    }

    @Test
    fun enPassantIsOfferedOnceAndTakesThePawn() {
        val game = Replay("e2e4 a7a6 e4e5 d7d5".split(' '))
        val take = game.current.moveOf("e5d6")
        assertNotNull(take)
        assertEquals("exd6", game.current.san(take))
        val after = game.current.play(take)
        assertNull(after[squareOf("d5")!!])
        // A move later it has gone.
        val later = Replay("e2e4 a7a6 e4e5 d7d5 g1f3 h7h6".split(' ')).current
        assertNull(later.moveOf("e5d6"))
    }

    @Test
    fun promotionNeedsAPieceAndIsWritten() {
        val p = fen("k7/4P3/8/8/8/8/8/4K3 w - - 0 1")
        assertNull(p.moveOf("e7e8"))
        val queen = p.moveOf("e7e8q")!!
        assertEquals("e8=Q+", p.san(queen))
        assertEquals(Piece(Side.White, Kind.Queen), p.play(queen)[squareOf("e8")!!])
        assertEquals("e8=N", p.san(p.moveOf("e7e8n")!!))
    }

    @Test
    fun checkmateAndStalemate() {
        val mate = Replay("f2f3 e7e5 g2g4 d8h4".split(' '))
        assertEquals("Qh4#", mate.san.last())
        assertTrue(mate.current.inCheck())
        assertTrue(mate.current.stuck)
        val stalemate = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(stalemate.inCheck())
        assertTrue(stalemate.stuck)
    }

    @Test
    fun twoPiecesThatCouldGoThereAreToldApart() {
        val knights = fen("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertEquals("Nbd2", knights.san(knights.moveOf("b1d2")!!))
        val rooks = fen("4k3/R7/8/8/8/8/8/R3K3 w - - 0 1")
        assertEquals("R1a4", rooks.san(rooks.moveOf("a1a4")!!))
    }

    @Test
    fun inCheckOnlyMovesThatAnswerItAndAnUnreadableMoveStopsTheReplay() {
        val pinned = fen("4k3/8/8/8/8/8/4r3/3RKB2 w - - 0 1")
        // The king is in check from e2: the bishop on f1 may take, the rook on d1 may not wander off.
        assertTrue(pinned.inCheck())
        assertNotNull(pinned.moveOf("f1e2"))
        assertNull(pinned.moveOf("d1d8"))
        val broken = Replay(listOf("e2e4", "e7e9", "g1f3"))
        assertEquals(1, broken.moves.size)
    }

    @Test
    fun theBoardIsDrawnFromThePlayersSide() {
        // White at the bottom: a8 top left, h1 bottom right. Black at the bottom: h1 top left, a8 bottom right.
        assertEquals("a8", squareName(squareAt(0, 0, Side.White)))
        assertEquals("h1", squareName(squareAt(7, 7, Side.White)))
        assertEquals("h1", squareName(squareAt(0, 0, Side.Black)))
        assertEquals("a8", squareName(squareAt(7, 7, Side.Black)))
        assertEquals("e4", squareName(squareAt(4, 4, Side.White)))
    }
}
