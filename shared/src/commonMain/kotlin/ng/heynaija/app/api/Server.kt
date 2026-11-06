package ng.heynaija.app.api

/**
 * The HeyNaija the app talks to. The web app serves the API under `/api` and
 * processed images under `/media`, so one origin covers both. Staging, until the
 * app can sign in (README, "What the backend needs").
 */
const val ORIGIN = "https://staging.heynaija.ng"

/**
 * A processed image at one of its stored sizes. `path` is what the API sends
 * (`/media/...`, no size); avatars come in "96" and "256", post images in "640"
 * and "1600", and a GIF's first frame is "still".
 */
fun mediaUrl(path: String, size: String): String = "$ORIGIN$path/$size.webp"
