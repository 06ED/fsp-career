package ru.fsp.career.identity.infrastructure

import java.net.URI

internal data class ProviderConfig(val clientId: String, val secret: String, val redirectUri: String)
internal class AuthConfig(val frontendOrigin: String, val apiOrigin: String, val issuer: String, val legacyIssuer: String,
    val fsp: ProviderConfig?, val vk: ProviderConfig?, val authUrl: String, val tokenUrl: String, val jwksUrl: String,
    val secure: Boolean) {
    companion object {
        fun fromEnv(issuer: String): AuthConfig {
            fun env(key: String) = System.getenv(key)?.takeIf(String::isNotBlank)
            fun provider(prefix: String, callback: String, api: String): ProviderConfig? {
                val id = env("${prefix}_CLIENT_ID")
                val secret = env("${prefix}_CLIENT_SECRET")
                return if (id == null || secret == null) null else ProviderConfig(id, secret, "$api/api/auth/$callback/callback")
            }
            val frontend = env("FRONTEND_ORIGIN") ?: "http://localhost:5173"
            val api = env("API_ORIGIN") ?: "http://localhost:8080"
            for (value in listOf(frontend, api)) {
                val parsed = URI(value)
                require(parsed.host != null && parsed.userInfo == null && parsed.rawQuery == null &&
                    parsed.rawFragment == null && parsed.path in listOf("", "/") && parsed.scheme in listOf("http", "https")) { "Invalid configured origin" }
            }
            val secure = env("APP_ENV") == "production"
            if (secure) require(frontend.startsWith("https://") && api.startsWith("https://") &&
                env("FRONTEND_ORIGIN") != null && env("API_ORIGIN") != null) { "Production origins must be explicit HTTPS" }
            val fspIssuer = (env("FSP_ISSUER") ?: issuer).trimEnd('/')
            if (secure) require(fspIssuer.startsWith("https://")) { "FSP_ISSUER must be HTTPS in production" }
            // ponytail: public issuer validation is separate from private Docker back-channel URLs.
            if (secure) require(env("FSP_AUTH_URL")?.startsWith("https://") != false)
            return AuthConfig(frontend.trimEnd('/'), api.trimEnd('/'), fspIssuer, issuer,
                provider("FSP", "fsp", api.trimEnd('/')), provider("VK", "vk", api.trimEnd('/')),
                env("FSP_AUTH_URL") ?: "$fspIssuer/protocol/openid-connect/auth",
                env("FSP_TOKEN_URL") ?: "$fspIssuer/protocol/openid-connect/token",
                env("FSP_JWKS_URL") ?: "$fspIssuer/protocol/openid-connect/certs", secure)
        }
    }
}
