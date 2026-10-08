package ru.fsp.career.identity.infrastructure

import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import ru.fsp.career.identity.domain.PasswordHasher

private val random = SecureRandom()
internal fun opaque(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
internal fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

internal object Passwords : PasswordHasher {
    // ponytail: JDK-native PBKDF2, with the version and cost stored alongside each hash.
    private const val rounds = 600_000
    private val workers = Dispatchers.IO.limitedParallelism(4)
    override suspend fun hash(password: String): String = withContext(workers) {
        val salt = ByteArray(16).also(random::nextBytes)
        "pbkdf2-sha256:$rounds:${Base64.getEncoder().encodeToString(salt)}:${derive(password, salt, rounds)}"
    }
    override suspend fun verify(password: String, encoded: String): Boolean = withContext(workers) {
        val parts = encoded.split(':')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256" || parts[1] != "$rounds") false
        else equal(derive(password, Base64.getDecoder().decode(parts[2]), rounds), parts[3])
    }
    private fun derive(password: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try { Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded) }
        finally { spec.clearPassword() }
    }
    override val dummyHash by lazy {
        val salt = ByteArray(16)
        "pbkdf2-sha256:$rounds:${Base64.getEncoder().encodeToString(salt)}:${derive("unknown", salt, rounds)}"
    }
}
internal fun verifiedFspIdentity(token: String, keys: JwkProvider, issuer: String, audience: String, nonce: String): Triple<String, String, Boolean> {
    val decoded = JWT.decode(token)
    require(decoded.algorithm == "RS256")
    val key = keys.get(decoded.keyId)
    val claims = JWT.require(Algorithm.RSA256(key.publicKey as java.security.interfaces.RSAPublicKey, null))
        .withIssuer(issuer).withAudience(audience).build().verify(token)
    require(claims.expiresAt != null && claims.getClaim("nonce").asString() == nonce && claims.subject.isNotBlank())
    return Triple(claims.subject, claims.getClaim("email").asString().orEmpty(), claims.getClaim("email_verified").asBoolean() == true)
}
