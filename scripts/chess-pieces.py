"""
Turns the Cburnett chess piece SVGs into Compose ImageVector code for the board
(shared/.../games/chess/Pieces.kt). The SVGs are MAME's (artwork/chess in
github.com/mamedev/mame, BSD-3-Clause: licenses/Cburnett-pieces-BSD-3-Clause.txt):

    mkdir pieces && cd pieces
    for f in bb bk bn bp bq br wb wk wn wp wq wr; do
      curl -sLO https://raw.githubusercontent.com/mamedev/mame/master/artwork/chess/$f.svg
    done
    cd .. && python3 scripts/chess-pieces.py pieces > shared/src/commonMain/kotlin/ng/heynaija/app/games/chess/Pieces.kt
"""
import math, re, sys, xml.etree.ElementTree as ET
from pathlib import Path

SRC = Path(sys.argv[1])
NS = '{http://www.w3.org/2000/svg}'
NAMES = {'k': 'King', 'q': 'Queen', 'r': 'Rook', 'b': 'Bishop', 'n': 'Knight', 'p': 'Pawn'}

def style_of(el, inherited):
    s = dict(inherited)
    for k in ('fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'stroke-miterlimit'):
        if el.get(k) is not None:
            s[k] = el.get(k).strip()
    for part in (el.get('style') or '').split(';'):
        if ':' in part:
            k, v = part.split(':', 1)
            s[k.strip()] = v.strip()
    return s

def colour(v):
    if v is None or v == 'none':
        return 'null'
    v = v.lower()
    if v in ('#000000', '#000', 'black'):
        return 'Black'
    if v in ('#ffffff', '#fff', 'white'):
        return 'White'
    raise SystemExit(f'unexpected colour {v}')

def group_of(transform):
    if not transform:
        return None
    m = re.match(r'translate\(([-\d.]+)[ ,]+([-\d.]+)\)', transform)
    if m:
        return dict(rotate=0.0, tx=float(m.group(1)), ty=float(m.group(2)))
    m = re.match(r'matrix\(([^)]*)\)', transform)
    if m:
        a, b, c, d, e, f = [float(x) for x in re.split(r'[ ,]+', m.group(1).strip())]
        # A rotation and a translation only (a = d, b = -c, unit scale).
        assert abs(a - d) < 1e-3 and abs(b + c) < 1e-3 and abs(a * a + b * b - 1) < 1e-2, transform
        return dict(rotate=math.degrees(math.atan2(b, a)), tx=e, ty=f)
    raise SystemExit(f'unexpected transform {transform}')

def path_data(el):
    tag = el.tag.replace(NS, '')
    if tag == 'path':
        return ' '.join(el.get('d').split())
    if tag == 'circle':
        cx, cy, r = (float(el.get(k)) for k in ('cx', 'cy', 'r'))
        return f'M {cx - r},{cy} A {r},{r} 0 1,0 {cx + r},{cy} A {r},{r} 0 1,0 {cx - r},{cy} Z'
    return None

def emit(el, inherited, out, depth):
    tag = el.tag.replace(NS, '')
    style = style_of(el, inherited)
    group = group_of(el.get('transform'))
    pad = '    ' * depth
    if group:
        out.append(f'{pad}addGroup(rotate = {group["rotate"]:.4f}f, translationX = {group["tx"]}f, translationY = {group["ty"]}f)')
    d = path_data(el)
    if d is not None:
        cap = {'round': 'Round', 'butt': 'Butt', 'square': 'Square'}[style.get('stroke-linecap', 'butt')]
        join = {'round': 'Round', 'miter': 'Miter', 'bevel': 'Bevel'}[style.get('stroke-linejoin', 'miter')]
        rule = 'EvenOdd' if style.get('fill-rule') == 'evenodd' else 'NonZero'
        width = float(style.get('stroke-width', '1'))
        out.append(
            f'{pad}layer("{d}", fill = {colour(style.get("fill", "#000000"))}, stroke = {colour(style.get("stroke"))}, '
            f'width = {width}f, cap = StrokeCap.{cap}, join = StrokeJoin.{join}, rule = PathFillType.{rule})'
        )
    for child in el:
        emit(child, style, out, depth + (1 if group else 0))
    if group:
        out.append(f'{pad}clearGroup()')

def main():
    pieces = []
    for colour_key, colour_name in (('w', 'White'), ('b', 'Black')):
        for key in 'kqrbnp':
            root = ET.parse(SRC / f'{colour_key}{key}.svg').getroot()
            body = []
            for child in root:
                emit(child, {}, body, 3)
            pieces.append((f'{colour_name}{NAMES[key]}', f'{colour_key}{key}', body))
    print('''package ng.heynaija.app.games.chess

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// GENERATED from the Cburnett chess pieces as MAME ships them (artwork/chess in
// mamedev/mame): BSD-3-Clause, copyright Colin M.L. Burnett, with contributions from
// other Wikipedia users (licenses/README.md). Drawn on a 45 by 45 grid. Their own
// black and white, not the theme's: a piece looks the same on every board.
// Made by scripts/chess-pieces.py; do not edit by hand.

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

private fun ImageVector.Builder.layer(d: String, fill: Color?, stroke: Color?, width: Float, cap: StrokeCap, join: StrokeJoin, rule: PathFillType) {
    addPath(
        pathData = addPathNodes(d),
        pathFillType = rule,
        fill = fill?.let(::SolidColor),
        stroke = stroke?.let(::SolidColor),
        strokeLineWidth = width,
        strokeLineCap = cap,
        strokeLineJoin = join,
        strokeLineMiter = 4f,
    )
}

private fun piece(name: String, draw: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 45.dp, defaultHeight = 45.dp, viewportWidth = 45f, viewportHeight = 45f)
        .apply(draw)
        .build()

/** The twelve pieces, by colour and kind. */
object Pieces {''')
    for name, key, body in pieces:
        print(f'    val {name}: ImageVector by lazy {{')
        print(f'        piece("{key}") {{')
        print('\n'.join(body))
        print('        }')
        print('    }')
    print('}')

main()
