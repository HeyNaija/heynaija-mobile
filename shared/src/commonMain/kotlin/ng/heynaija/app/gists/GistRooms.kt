package ng.heynaija.app.gists

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ng.heynaija.app.platform.GistKeeper
import ng.heynaija.app.platform.GistNow

/**
 * The Gists the app has open, and the one the member is in.
 *
 * A room's screen comes and goes; the room the member joined stays until they
 * leave or it ends, so its sound goes on while they read the rest of the app
 * (GistBar shows it over every other screen) and with the app in the background
 * or the phone locked ([keeper]). One room at a time: joining another leaves the
 * first. Screens showing the same Gist share its room.
 */
class GistRooms(
    private val keeper: GistKeeper?,
    private val scope: CoroutineScope,
    private val make: (id: String, onPresence: (GistRoom, Boolean) -> Unit, foreground: () -> Boolean) -> GistRoom,
) {
    private class Held(val room: GistRoom, var screens: Int = 0)

    private val held = mutableMapOf<String, Held>()
    private val _current = MutableStateFlow<GistRoom?>(null)
    /** The room the member is in, or joining. */
    val current: StateFlow<GistRoom?> = _current.asStateFlow()
    private var foreground = true
    private var telling: Job? = null

    /** A screen opens the Gist [id]: the room the member is already in, or another screen shows, or a new one. */
    fun open(id: String): GistRoom {
        val entry = held.getOrPut(id) { Held(make(id, ::presence) { foreground }) }
        entry.screens++
        return entry.room
    }

    /** That screen is gone. The room goes with the last one, unless the member is in it. */
    fun close(id: String) {
        val entry = held[id] ?: return
        entry.screens--
        drop(entry)
    }

    private fun drop(entry: Held) {
        if (entry.screens > 0 || _current.value === entry.room) return
        held.remove(entry.room.id)
        entry.room.dispose()
    }

    private fun presence(room: GistRoom, inRoom: Boolean) {
        if (inRoom) {
            val before = _current.value
            _current.value = room
            // One room at a time, as one device at a time on the web.
            if (before != null && before !== room) before.leave()
            tell(room)
        } else {
            if (_current.value === room) {
                _current.value = null
                telling?.cancel()
                telling = null
                keeper?.release()
            }
            held[room.id]?.let(::drop)
        }
    }

    /** Keeps the system up to date with the room: its title, the microphone, a lost connection. */
    private fun tell(room: GistRoom) {
        telling?.cancel()
        val keeper = keeper ?: return
        telling = scope.launch {
            room.state
                .map { GistNow(room.id, it.view?.title.orEmpty(), micOn = it.micOn, reconnecting = it.phase == Phase.Reconnecting) }
                .distinctUntilChanged()
                .collect { now -> keeper.keep(now, leave = room::leave) }
        }
    }

    /** Out of whatever room the member is in: signing out, or the app was swiped away. */
    fun leave() {
        _current.value?.leave()
    }

    /** The app came on screen or left it: rooms are not polled off screen, and get a fresh look on coming back. */
    fun setForeground(on: Boolean) {
        foreground = on
        if (on) _current.value?.lookNow()
    }
}

/** A screen's hold on a Gist's room: let go when the screen leaves the stack. */
class GistRoomHolder(private val rooms: GistRooms, id: String) : ViewModel() {
    val room = rooms.open(id)

    override fun onCleared() = rooms.close(room.id)
}
