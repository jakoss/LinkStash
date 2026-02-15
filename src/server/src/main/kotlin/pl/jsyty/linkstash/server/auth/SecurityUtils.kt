package pl.jsyty.linkstash.server.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val nonceSizeBytes = 12
private const val gcmTagLengthBits = 128

class TokenGenerator {
    private val secureRandom = SecureRandom()

    fun randomUrlSafeToken(sizeBytes: Int = 32): String {
        val bytes = ByteArray(sizeBytes)
        secureRandom.nextBytes(bytes)
        return bytes.toBase64Url()
    }
}

class TokenHasher(secret: String) {
    private val hmacKey = sha256(secret.toByteArray(StandardCharsets.UTF_8))

    fun hash(token: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(token.toByteArray(StandardCharsets.UTF_8)).toBase64Url()
    }
}

class TokenCipher(secret: String) {
    private val keyBytes = sha256(secret.toByteArray(StandardCharsets.UTF_8)).copyOf(32)
    private val secureRandom = SecureRandom()

    fun encrypt(plainText: String): String {
        val nonce = ByteArray(nonceSizeBytes)
        secureRandom.nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(gcmTagLengthBits, nonce))
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        return "v1.${(nonce + encrypted).toBase64Url()}"
    }

    fun decrypt(cipherText: String): String {
        require(cipherText.startsWith("v1.")) { "Unsupported cipher version" }

        val payload = cipherText.removePrefix("v1.").fromBase64Url()
        require(payload.size > nonceSizeBytes) { "Invalid cipher payload" }

        val nonce = payload.copyOfRange(0, nonceSizeBytes)
        val encrypted = payload.copyOfRange(nonceSizeBytes, payload.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(gcmTagLengthBits, nonce))
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted, StandardCharsets.UTF_8)
    }
}

fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

fun String.fromBase64Url(): ByteArray = Base64.getUrlDecoder().decode(this)

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
