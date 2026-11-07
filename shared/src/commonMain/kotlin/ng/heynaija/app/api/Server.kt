package ng.heynaija.app.api

/**
 * A HeyNaija the app can talk to. On staging and production the web app serves
 * the API under `/api`, Kratos under `/kratos` and processed images under
 * `/media`, all on one origin. The dev stack on a computer serves them on their
 * own ports, so each has its own base.
 */
data class Server(
    val key: String,
    val label: String,
    /** The API's root, without `/v1`. */
    val api: String,
    /** Kratos's public API. */
    val kratos: String,
    /** Where `/media/...` paths are served from: the API sends paths, not URLs. */
    val media: String,
    /** The web app, for links people share and pages the app does not have. */
    val web: String,
    /**
     * Kratos's Google client (docs/deployment/google-sign-in.md in the web
     * repository): Google signs the app's ID tokens for it, so Kratos accepts
     * them. Null where Google sign-in is off.
     */
    val googleClientId: String? = HEYNAIJA_GOOGLE_CLIENT,
) {
    companion object {
        /** heynaija.ng: what release builds always use, and a debug build can choose. */
        val Production = Server(
            key = "production",
            label = "Production",
            api = "https://heynaija.ng/api",
            kratos = "https://heynaija.ng/kratos",
            media = "https://heynaija.ng",
            web = "https://heynaija.ng",
        )

        val Staging = Server(
            key = "staging",
            label = "Staging",
            api = "https://staging.heynaija.ng/api",
            kratos = "https://staging.heynaija.ng/kratos",
            media = "https://staging.heynaija.ng",
            web = "https://staging.heynaija.ng",
        )

        /**
         * The dev stack (`pnpm dev:api` and `pnpm infra:up`) on the computer running
         * the simulator. The iOS simulator shares the computer's network; on the
         * Android emulator, forward the ports first with `adb reverse` (README).
         */
        val Local = Server(
            key = "local",
            label = "This computer",
            api = "http://127.0.0.1:4000",
            kratos = "http://127.0.0.1:4533",
            media = "http://127.0.0.1:9100/heynaija-media",
            web = "http://127.0.0.1:3000",
        )

        val all = listOf(Production, Staging, Local)

        /** A debug build's choice, kept between launches; staging until one is made. */
        fun byKey(key: String?): Server = all.firstOrNull { it.key == key } ?: Staging
    }
}

/** The web client every HeyNaija server's Kratos uses for Google (staging, production and the dev stack's `.env`). */
private const val HEYNAIJA_GOOGLE_CLIENT = "567185521629-rk7666tosiga37ljdvvi4tkivl3b0jil.apps.googleusercontent.com"

/** The server the app talks to: production in a release build; a debug build can switch it in Settings. */
object Servers {
    var current: Server = Server.Production
}

/**
 * A processed image at one of its stored sizes. `path` is what the API sends
 * (`/media/...`, no size); avatars come in "96" and "256", post images in "640"
 * and "1600", community photos in "256", and a GIF's first frame is "still".
 */
fun mediaUrl(path: String, size: String): String {
    val server = Servers.current
    // The dev stack's bucket is served at its own root, without the /media prefix.
    val shown = if (server.media.endsWith("/heynaija-media")) path.removePrefix("/media") else path
    return "${server.media}$shown/$size.webp"
}

/**
 * A video's file as the API names it: absolute on the media domain where the
 * server has MEDIA_ORIGIN, or a `/media/…` path where it has not (the dev stack),
 * which is made absolute here.
 */
fun mediaFile(url: String): String {
    if (!url.startsWith("/")) return url
    val server = Servers.current
    return if (server.media.endsWith("/heynaija-media")) server.media + url.removePrefix("/media") else server.web + url
}
