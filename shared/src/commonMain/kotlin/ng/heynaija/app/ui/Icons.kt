package ng.heynaija.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The web app's icons (Lucide, ISC licence: licenses/README.md), drawn from the
 * same path data at the same stroke weight. Tint them with `Icon(tint = ...)`.
 */
object Icons {
    val Heart = lucide(
        "heart",
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
    )
    val Bubble = lucide(
        "message-circle",
        "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
    )
    val Repost = lucide(
        "repeat-2",
        "m2 9 3-3 3 3",
        "M13 18H7a2 2 0 0 1-2-2V6",
        "m22 15-3 3-3-3",
        "M11 6h6a2 2 0 0 1 2 2v10",
    )
    val Play = lucide(
        "play",
        "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",
    )
    val Home = lucide(
        "house",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
    val Search = lucide(
        "search",
        "m21 21-4.34-4.34",
        "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
    )
    val Bell = lucide(
        "bell",
        "M10.268 21a2 2 0 0 0 3.464 0",
        "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    )
    val User = lucide(
        "user",
        "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2",
        "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
    )
    val People = lucide(
        "users",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M16 3.128a4 4 0 0 1 0 7.744",
        "M22 21v-2a4 4 0 0 0-3-3.87",
        "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
    )
    val Bookmark = lucide(
        "bookmark",
        "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
    )
    val Share = lucide(
        "share-2",
        "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        "M8.59 13.51L15.42 17.49",
        "M15.41 6.51L8.59 10.49",
    )
    val Dots = lucide(
        "ellipsis",
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
    )
    val Plus = lucide(
        "plus",
        "M5 12h14",
        "M12 5v14",
    )
    val Close = lucide(
        "x",
        "M18 6 6 18",
        "m6 6 12 12",
    )
    val Image = lucide(
        "image-plus",
        "M16 5h6",
        "M19 2v6",
        "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val Gear = lucide(
        "settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    )
    val Check = lucide(
        "check",
        "M20 6 9 17l-5-5",
    )
    val Back = lucide(
        "chevron-left",
        "m15 18-6-6 6-6",
    )
    val Chevron = lucide(
        "chevron-down",
        "m6 9 6 6 6-6",
    )
    val Next = lucide(
        "chevron-right",
        "m9 18 6-6-6-6",
    )
    val Pin = lucide(
        "pin",
        "M12 17v5",
        "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z",
    )
    val Flag = lucide(
        "flag",
        "M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528",
    )
    val Leave = lucide(
        "log-out",
        "m16 17 5-5-5-5",
        "M21 12H9",
        "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
    )
    val Quote = lucide(
        "quote",
        "M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z",
        "M5 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z",
    )
    val Mention = lucide(
        "at-sign",
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8",
    )
    val Hash = lucide(
        "hash",
        "M4 9L20 9",
        "M4 15L20 15",
        "M10 3L8 21",
        "M16 3L14 21",
    )
    val Vote = lucide(
        "vote",
        "m9 12 2 2 4-4",
        "M5 7c0-1.1.9-2 2-2h10a2 2 0 0 1 2 2v12H5V7Z",
        "M22 19H2",
    )
    val Link = lucide(
        "link",
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    )
    val Verified = lucide(
        "badge-check",
        "M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z",
        "m16 9-5.5 5.5L8 12",
    )
    val Sound = lucide(
        "volume-2",
        "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
        "M16 9a5 5 0 0 1 0 6",
        "M19.364 18.364a9 9 0 0 0 0-12.728",
    )
    val Muted = lucide(
        "volume-x",
        "M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z",
        "m16.5 14.5 5-5",
        "m16.5 9.5 5 5",
    )
    val Pause = lucide(
        "pause",
        "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
        "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1h-3a1 1 0 0 1 -1 -1v-16a1 1 0 0 1 1 -1z",
    )
    val Award = lucide(
        "award",
        "m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526",
        "M6 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0",
    )
    val Mail = lucide(
        "mail",
        "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7",
        "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-16a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z",
    )
    val Pen = lucide(
        "pen-line",
        "M13 21h8",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
    )
    val Follow = lucide(
        "user-plus",
        "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
        "M5 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M19 8L19 14",
        "M22 11L16 11",
    )
    val Lightbulb = lucide(
        "lightbulb",
        "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5",
        "M9 18h6",
        "M10 22h4",
    )
    val Shield = lucide(
        "shield",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    )
    val Info = lucide(
        "info",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M12 16v-4",
        "M12 8h.01",
    )
    val Globe = lucide(
        "globe",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
    )
    val Camera = lucide(
        "camera",
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
        "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
    )
    val Video = lucide(
        "video",
        "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
        "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-10a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
    )
    /** Pressed: the like is the viewer's. */
    val Mic = lucide(
        "mic",
        "M12 19v3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M15 5a3 3 0 0 0-6 0v7a3 3 0 0 0 6 0z",
    )
    val MicOff = lucide(
        "mic-off",
        "M12 19v3",
        "M15 9.34V5a3 3 0 0 0-5.68-1.33",
        "M16.95 16.95A7 7 0 0 1 5 12v-2",
        "M18.89 13.23A7 7 0 0 0 19 12v-2",
        "m2 2 20 20",
        "M9 9v3a3 3 0 0 0 5.12 2.12",
    )
    val Hand = lucide(
        "hand",
        "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
        "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2",
        "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8",
        "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
    )
    /** Lucide's "radio": the web's LiveIcon, for Gists. */
    val Live = lucide(
        "radio",
        "M16.247 7.761a6 6 0 0 1 0 8.478",
        "M19.075 4.933a10 10 0 0 1 0 14.134",
        "M4.925 19.067a10 10 0 0 1 0-14.134",
        "M7.753 16.239a6 6 0 0 1 0-8.478",
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
    )
    val Lock = lucide(
        "lock",
        "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )
    val HeartFilled = lucide(
        "heart-filled",
        "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
        filled = true,
    )
    val BookmarkFilled = lucide(
        "bookmark-filled",
        "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
        filled = true,
    )
}

// apps/web/app/ui/icons.tsx draws every icon at stroke 1.9 on Lucide's 24 grid.
private fun lucide(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

/**
 * Google's "G", in its own four colours, for the "Continue with Google" button
 * (Google's sign-in branding asks for it as it is). Draw it with `Image`, not a
 * tinted `Icon`.
 */
val GoogleG: ImageVector = ImageVector.Builder(name = "google-g", defaultWidth = 18.dp, defaultHeight = 18.dp, viewportWidth = 48f, viewportHeight = 48f)
    .apply {
        val parts = listOf(
            0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
            0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
            0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
            0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
        )
        for ((colour, d) in parts) addPath(pathData = addPathNodes(d), fill = SolidColor(Color(colour)))
    }
    .build()
