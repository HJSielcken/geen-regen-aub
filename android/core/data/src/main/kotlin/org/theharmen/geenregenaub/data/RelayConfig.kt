package org.theharmen.geenregenaub.data

/**
 * Where the app finds the relay server, and the shared secret it sends with every request.
 * Values are baked in at build time; see android/README.md.
 */
object RelayConfig {
    const val SECRET_HEADER = "X-Geenregen-Key"

    val baseUrl: String = BuildConfig.RELAY_BASE_URL.trimEnd('/')
    val secret: String = BuildConfig.RELAY_SECRET

    val isConfigured: Boolean get() = secret.isNotBlank()
}
