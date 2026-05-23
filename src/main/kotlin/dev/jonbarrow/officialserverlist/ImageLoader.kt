package dev.jonbarrow.officialserverlist

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.imageio.ImageIO

object ImageLoader {
	data class ImageTexture(val id: Identifier, val width: Int, val height: Int)

	private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

	private val cache = ConcurrentHashMap<String, ImageTexture>()
	private val inFlight = ConcurrentHashMap<String, Boolean>()
	private val textureRefs = ConcurrentHashMap<String, DynamicTexture>()

	init {
		ImageIO.scanForPlugins()
	}

	fun get(url: String): ImageTexture? {
		cache[url]?.let { return it }

		if (inFlight.putIfAbsent(url, true) == null) {
			loadAsync(url)
		}

		return null
	}

	fun fromBytes(key: String, bytes: ByteArray): ImageTexture? {
		cache[key]?.let { return it }

		val pngBytes = bytesToPNGBytes(bytes) ?: return null

		if (Minecraft.getInstance().isSameThread) {
			registerTexture(key, pngBytes)
		} else {
			Minecraft.getInstance().execute { registerTexture(key, pngBytes) }
		}

		return cache[key]
	}

	private fun loadAsync(url: String) {
		CompletableFuture.supplyAsync<ByteArray?> {
			try {
				val request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build()

				val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
				if (response.statusCode() !in 200..299) {
					return@supplyAsync null
				}

				val pngBytes = bytesToPNGBytes(response.body())
				if (pngBytes == null) {
					return@supplyAsync null
				}

				pngBytes
			} catch (e: Exception) {
				null
			}
		}.thenAccept { bytes ->
			if (bytes == null) return@thenAccept
			Minecraft.getInstance().execute {
				registerTexture(url, bytes)
			}
		}
	}

	private fun bytesToPNGBytes(bytes: ByteArray): ByteArray? {
		val image = ImageIO.read(ByteArrayInputStream(bytes))
		if (image == null) {
			return null
		}

		return ByteArrayOutputStream().use { out ->
			ImageIO.write(image, "png", out)
			out.toByteArray()
		}
	}

	private fun registerTexture(key: String, bytes: ByteArray) {
		try {
			val image = NativeImage.read(ByteArrayInputStream(bytes))
			val id = Identifier.fromNamespaceAndPath("official-server-list", "image/${key.hashCode().toUInt()}")
			val texture = DynamicTexture(Supplier { "official-server-list-image-$id" }, image)

			Minecraft.getInstance().textureManager.register(id, texture)
			textureRefs[key] = texture
			cache[key] = ImageTexture(id, image.width, image.height)
		} catch (e: Exception) {}
	}
}