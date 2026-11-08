package ng.heynaija.app.platform

import androidx.compose.runtime.Composable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import ng.heynaija.app.LocalApp

/**
 * Apple's Declared Age Range, which only Swift can call: iosApp's AgeRange.swift
 * implements it, and MainViewController passes it in.
 */
interface AppleAgeRange {
    /** Calls [done] once, on the main thread, with what Apple says. */
    fun check(done: (StoreAge) -> Unit)
}

internal class DeclaredAgeSignals(private val apple: AppleAgeRange) : AgeSignals {
    override val store = "Apple"

    override suspend fun check(): StoreAge = suspendCancellableCoroutine { continuation ->
        apple.check { if (continuation.isActive) continuation.resume(it) }
    }
}

@Composable
actual fun rememberAgeSignals(): AgeSignals? = LocalApp.current.platform.ageSignals
