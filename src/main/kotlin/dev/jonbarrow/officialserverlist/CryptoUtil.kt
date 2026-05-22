package dev.jonbarrow.officialserverlist

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

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
}