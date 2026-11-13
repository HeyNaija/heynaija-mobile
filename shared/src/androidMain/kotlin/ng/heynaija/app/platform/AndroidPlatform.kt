package ng.heynaija.app.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun androidPlatform(
    context: Context,
    debug: Boolean,
    push: (secure: KeyValueStore) -> PushDevice? = { null },
    gistKeeper: GistKeeper? = null,
): Platform {
    val secure = androidSecureStore(context)
    return Platform(
        secure = secure,
        settings = androidSettingsStore(context),
        debug = debug,
        gistAudio = LiveKitGistAudioFactory(context, debug),
        gistKeeper = gistKeeper,
        push = push(secure),
    )
}

/** The session token and the push key, encrypted by the Keystore; for push, which runs without the app. */
fun androidSecureStore(context: Context): KeyValueStore =
    KeystoreStore(context.getSharedPreferences("heynaija_secure", Context.MODE_PRIVATE))

fun androidSettingsStore(context: Context): KeyValueStore =
    PrefsStore(context.getSharedPreferences("heynaija", Context.MODE_PRIVATE))

private class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun set(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}

/**
 * Values encrypted with an AES key that never leaves the Android Keystore, and
 * stored as IV plus ciphertext. A value that cannot be read back (the key was
 * lost, say, after a restore to another phone) counts as missing.
 */
private class KeystoreStore(private val prefs: SharedPreferences) : KeyValueStore {
    private val alias = "heynaija_session"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun set(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }
}
