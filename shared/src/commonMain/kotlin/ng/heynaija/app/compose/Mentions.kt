package ng.heynaija.app.compose

/** "@ad" just before the cursor, at the start or after a space: the handle typed so far. */
private val TYPING = Regex("""(^|\s)@([A-Za-z0-9_]{1,20})$""")

/** The part of a handle being typed at [cursor], or null when the cursor is not in an @mention. */
fun mentionAt(text: String, cursor: Int): String? {
    if (cursor < 0 || cursor > text.length) return null
    return TYPING.find(text.substring(0, cursor))?.groupValues?.get(2)
}

/**
 * The text with the @mention at [cursor] completed to [handle] and a space after
 * it, and where the cursor goes next. Unchanged when there is no mention there.
 */
fun insertMention(text: String, cursor: Int, handle: String): Pair<String, Int> {
    val typed = mentionAt(text, cursor) ?: return text to cursor
    val start = cursor - typed.length - 1
    val rest = text.substring(cursor)
    val inserted = "@$handle" + if (rest.startsWith(" ")) "" else " "
    return text.substring(0, start) + inserted + rest to start + inserted.length + if (rest.startsWith(" ")) 1 else 0
}
