package dev.jonbarrow.officialserverlist

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

// * Official client makes use of some JavaScript libraries for crypto that we need to reimplement
object CryptoUtil {
	private data class KeyAndIv(val key: ByteArray, val iv: ByteArray)

	private fun evpBytesToKey(password: ByteArray, salt: ByteArray, keyLen: Int, ivLen: Int): KeyAndIv {
		val md5 = MessageDigest.getInstance("MD5")
		var derived = ByteArray(0)
		var block = ByteArray(0)

		while (derived.size < keyLen + ivLen) {
			md5.reset()
			md5.update(block)
			md5.update(password)
			md5.update(salt)
			block = md5.digest()

			derived += block
		}

		return KeyAndIv(
			key = derived.copyOfRange(0, keyLen),
			iv = derived.copyOfRange(keyLen, keyLen + ivLen)
		)
	}

	// * CryptoJS.AES.decrypt(payload, passphrase) for the "Salted__" mode
	fun decryptCryptoJS(ciphertextB64: String, password: String): String {
		val data = Base64.getDecoder().decode(ciphertextB64)

		val saltHeader = String(data.copyOfRange(0, 8), Charsets.US_ASCII)
		if (saltHeader != "Salted__") {
			throw IllegalArgumentException("Expected CryptoJS 'Salted__' header but got '$saltHeader'. " + "The payload may use a raw-key (non-KDF) mode instead.")
		}

		val salt = data.copyOfRange(8, 16)
		val ciphertext = data.copyOfRange(16, data.size)

		val (key, iv) = evpBytesToKey(password.toByteArray(Charsets.UTF_8), salt, 32, 16)

		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))

		return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
	}

	// * Mimics https://www.npmjs.com/package/jwt-encode, which is what FMCS uses for creating signed POST/PATCH payloads
	val jwtJson: Json = Json {
		explicitNulls = false
	}

	internal fun base64url(data: ByteArray): String {
		return Base64.getEncoder().encodeToString(data)
			.replace(Regex("=+$"), "")
			.replace('+', '-')
			.replace('/', '_')
	}

	internal fun hmacSha256(message: ByteArray, secret: ByteArray): ByteArray {
		val mac = Mac.getInstance("HmacSHA256")
		mac.init(SecretKeySpec(secret, "HmacSHA256"))

		return mac.doFinal(message)
	}

	fun buildJWT(payload: JsonObject, secret: String, options: JsonObject = JsonObject(emptyMap())): String {
		val header = buildJsonObject {
			put("alg", "HS256")
			put("typ", "JWT")

			for ((key, value) in options) {
				put(key, value)
			}
		}

		val encodedHeader = base64url(header.toString().toByteArray(Charsets.UTF_8))
		val encodedData = base64url(payload.toString().toByteArray(Charsets.UTF_8))

		val signingInput = "$encodedHeader.$encodedData"
		val signature = base64url(hmacSha256(signingInput.toByteArray(Charsets.UTF_8), secret.toByteArray(Charsets.UTF_8)))

		return "$signingInput.$signature"
	}

	inline fun <reified T> buildJWT(payload: T, secret: String, options: JsonObject = JsonObject(emptyMap())): String {
		val payloadObject = jwtJson.encodeToJsonElement(payload) as JsonObject

		return buildJWT(payloadObject, secret, options)
	}

	// * FMCS cookie obfuscation. NOT real security. The key is derived from local values so
	// * anything running on this machine can re-derive it. This only raises the bar above
	// * storing the cookie in plaintext, and stops a copied file from being trivially decrypted
	// * on a different machine.
	// * See https://github.com/javakeyring/java-keyring#security-concerns for why we can't use
	// * the system keyring for this. I couldn't think of anything better, sorry
	private const val OBFUSCATION_VERSION: Byte = 1
	private const val HASH_SIZE: Int = 32

	private fun sha256(data: ByteArray): ByteArray {
		return MessageDigest.getInstance("SHA-256").digest(data)
	}

	private fun machineKey(): ByteArray {
		val keySource = buildString {
			append(System.getProperty("user.name") ?: "")
			append(System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "")
			append(System.getProperty("os.name") ?: "")
		}

		return sha256(keySource.toByteArray(Charsets.UTF_8))
	}

	fun obfuscateCookie(plaintext: String): String {
		val iv = ByteArray(16)
		SecureRandom().nextBytes(iv)

		val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
		val hash = sha256(plaintextBytes)
		val payload = ByteArray(plaintextBytes.size + HASH_SIZE)

		System.arraycopy(plaintextBytes, 0, payload, 0, plaintextBytes.size)
		System.arraycopy(hash, 0, payload, plaintextBytes.size, HASH_SIZE)

		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(machineKey(), "AES"), IvParameterSpec(iv))
		val ciphertext = cipher.doFinal(payload)
		val obfuscated = ByteArray(1 + iv.size + ciphertext.size)

		obfuscated[0] = OBFUSCATION_VERSION
		System.arraycopy(iv, 0, obfuscated, 1, iv.size)
		System.arraycopy(ciphertext, 0, obfuscated, 1 + iv.size, ciphertext.size)

		return Base64.getEncoder().encodeToString(obfuscated)
	}

	fun deobfuscateCookie(payloadB64: String): String? {
		return try {
			val obfuscated = Base64.getDecoder().decode(payloadB64)
			if (obfuscated.isEmpty() || obfuscated[0] != OBFUSCATION_VERSION) {
				return null
			}

			val iv = obfuscated.copyOfRange(1, 17)
			val ciphertext = obfuscated.copyOfRange(17, obfuscated.size)

			val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
			cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(machineKey(), "AES"), IvParameterSpec(iv))

			val payload = cipher.doFinal(ciphertext)
			if (payload.size < HASH_SIZE) {
				return null
			}

			val plaintextBytes = payload.copyOfRange(0, payload.size - HASH_SIZE)
			val storedHash = payload.copyOfRange(payload.size - HASH_SIZE, payload.size)
			val computedHash = sha256(plaintextBytes)

			if (!MessageDigest.isEqual(storedHash, computedHash)) {
				return null
			}

			String(plaintextBytes, Charsets.UTF_8)
		} catch (e: Exception) {
			null
		}
	}
}