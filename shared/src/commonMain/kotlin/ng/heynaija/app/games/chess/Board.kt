package ng.heynaija.app.games.chess

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import ng.heynaija.app.theme.HeyNaija
import ng.heynaija.app.theme.Radii

// The board, as the web draws it (globals.css, .chess-board): light squares are the
// inset fill, dark ones the accent mixed into it; the last move, the chosen piece and
// its moves in the accent, a king in check in the danger colour. No new colours.

fun pieceImage(piece: Piece): ImageVector = when (piece.side) {
    Side.White -> when (piece.kind) {
        Kind.King -> Pieces.WhiteKing
        Kind.Queen -> Pieces.WhiteQueen
        Kind.Rook -> Pieces.WhiteRook
        Kind.Bishop -> Pieces.WhiteBishop
        Kind.Knight -> Pieces.WhiteKnight
        Kind.Pawn -> Pieces.WhitePawn
    }
    Side.Black -> when (piece.kind) {
        Kind.King -> Pieces.BlackKing
        Kind.Queen -> Pieces.BlackQueen
        Kind.Rook -> Pieces.BlackRook
        Kind.Bishop -> Pieces.BlackBishop
        Kind.Knight -> Pieces.BlackKnight
        Kind.Pawn -> Pieces.BlackPawn
    }
}

fun pieceName(piece: Piece) = "${piece.side.key} ${piece.kind.name.lowercase()}"

/** The square shown at [column], [row] (from the top left) with [orientation] at the bottom. */
fun squareAt(column: Int, row: Int, orientation: Side): Int =
    if (orientation == Side.White) (7 - row) * 8 + column else row * 8 + (7 - column)

/**
 * A board showing [position], [orientation] at the bottom. When [mover] is the side
 * to move, the viewer can move its pieces: tap one and then where it goes, or drag
 * it there. Only legal moves are offered; a pawn reaching the last rank asks which
 * piece it becomes. [onMove] gets the move; Lichess still decides.
 */
@Composable
fun ChessBoard(
    position: Position,
    orientation: Side,
    lastMove: Move?,
    mover: Side?,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HeyNaija.colors
    val light = colors.panel
    val dark = lerp(colors.panel, colors.accent, 0.38f)
    val canMove = mover != null && mover == position.turn
    val legal = remember(position, canMove) { if (canMove) position.legalMoves() else emptyList() }
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    var promoting by remember(position) { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragFrom by remember(position) { mutableStateOf<Int?>(null) }
    var dragAt by remember(position) { mutableStateOf(Offset.Zero) }
    val check = if (position.inCheck()) position.kingSquare(position.turn) else -1
    val targets = selected?.let { from -> legal.filter { it.from == from }.map { it.to }.toSet() } ?: emptySet()

    fun go(from: Int, to: Int) {
        val options = legal.filter { it.from == from && it.to == to }
        when {
            options.isEmpty() -> selected = position[to]?.takeIf { it.side == mover }?.let { to }
            options.size > 1 -> promoting = from to to
            else -> {
                selected = null
                onMove(options.single())
            }
        }
    }

    fun tap(square: Int) {
        if (!canMove) return
        val from = selected
        val piece = position[square]
        when {
            from != null && square in targets -> go(from, square)
            piece?.side == mover && legal.any { it.from == square } -> selected = if (from == square) null else square
            else -> selected = null
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f).clip(Radii.control)) {
        val cell = maxWidth / 8
        val cellPx = with(LocalDensity.current) { cell.toPx() }
        fun squareUnder(at: Offset): Int? {
            val column = (at.x / cellPx).toInt()
            val row = (at.y / cellPx).toInt()
            return if (column in 0..7 && row in 0..7) squareAt(column, row, orientation) else null
        }
        Column(
            Modifier.fillMaxSize().pointerInput(position, canMove, orientation) {
                if (!canMove) return@pointerInput
                detectDragGestures(
                    onDragStart = { at ->
                        val square = squareUnder(at)
                        if (square != null && position[square]?.side == mover && legal.any { it.from == square }) {
                            dragFrom = square
                            dragAt = at
                            selected = square
                        }
                    },
                    onDrag = { change, amount ->
                        if (dragFrom != null) {
                            change.consume()
                            dragAt += amount
                        }
                    },
                    onDragEnd = {
                        val from = dragFrom
                        dragFrom = null
                        val to = squareUnder(dragAt)
                        if (from != null && to != null && to != from) go(from, to)
                    },
                    onDragCancel = { dragFrom = null },
                )
            },
        ) {
            for (row in 0..7) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (column in 0..7) {
                        val square = squareAt(column, row, orientation)
                        val piece = position[square]
                        val isLight = (file(square) + rank(square)) % 2 == 1
                        val tint = when {
                            square == check -> colors.danger.copy(alpha = 0.45f)
                            square == selected -> colors.accent.copy(alpha = 0.45f)
                            lastMove != null && (square == lastMove.from || square == lastMove.to) -> colors.accent.copy(alpha = 0.30f)
                            else -> null
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(if (isLight) light else dark)
                                .let { if (tint != null) it.background(tint) else it }
                                .clickable(enabled = canMove, role = Role.Button, indication = null, interactionSource = null) { tap(square) }
                                .semantics {
                                    contentDescription = squareName(square) + (piece?.let { ", " + pieceName(it) } ?: "") +
                                        (if (square in targets) ", a move" else "")
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (piece != null && square != dragFrom) {
                                Image(rememberVectorPainter(pieceImage(piece)), null, Modifier.fillMaxSize().padding(cell * 0.04f))
                            }
                            if (square in targets) {
                                if (piece == null) {
                                    Box(Modifier.size(cell * 0.3f).clip(CircleShape).background(colors.accent.copy(alpha = 0.55f)))
                                } else {
                                    Box(Modifier.fillMaxSize().border(4.dp, colors.accent.copy(alpha = 0.55f)))
                                }
                            }
                            // The files along the bottom and the ranks up the left, as on the web.
                            val label = Modifier.padding(horizontal = 2.dp)
                            if (row == 7) {
                                Text(
                                    "${'a' + file(square)}",
                                    label.align(Alignment.BottomEnd),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLight) dark else light,
                                )
                            }
                            if (column == 0) {
                                Text(
                                    "${rank(square) + 1}",
                                    label.align(Alignment.TopStart),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isLight) dark else light,
                                )
                            }
                        }
                    }
                }
            }
        }
        // The dragged piece follows the finger, a little larger, over everything.
        dragFrom?.let { from ->
            position[from]?.let { piece ->
                val half = cellPx * 0.6f
                Image(
                    rememberVectorPainter(pieceImage(piece)),
                    null,
                    Modifier
                        .zIndex(2f)
                        .size(cell * 1.2f)
                        .offset { IntOffset((dragAt.x - half).roundToInt(), (dragAt.y - half).roundToInt()) }
                        .graphicsLayer { alpha = 0.95f },
                )
            }
        }
    }

    promoting?.let { (from, to) ->
        val side = position[from]?.side ?: Side.White
        AlertDialog(
            onDismissRequest = { promoting = null },
            title = { Text("Promote to") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (kind in listOf(Kind.Queen, Kind.Rook, Kind.Bishop, Kind.Knight)) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(Radii.control)
                                .background(colors.panel)
                                .clickable {
                                    promoting = null
                                    selected = null
                                    legal.firstOrNull { it.from == from && it.to == to && it.promotion == kind }?.let(onMove)
                                }
                                .semantics { contentDescription = kind.name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(rememberVectorPainter(pieceImage(Piece(side, kind))), null, Modifier.size(48.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { promoting = null }) { Text("Cancel") } },
        )
    }
}

/** The moves in pairs, "1. e4 e5", with the one shown marked; tapping one shows the board after it. */
@Composable
fun MoveList(san: List<String>, shown: Int, onShow: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = HeyNaija.colors
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        san.forEachIndexed { i, text ->
            val ply = i + 1
            if (i % 2 == 0) {
                Text("${i / 2 + 1}.", Modifier.padding(start = if (i == 0) 0.dp else 6.dp, top = 4.dp), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            Text(
                text,
                Modifier
                    .clip(Radii.chip)
                    .background(if (ply == shown) colors.accentSoft else colors.surface)
                    .clickable { onShow(ply) }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ply == shown) colors.accentStrong else colors.ink,
                fontWeight = if (ply == shown) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
