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

	private fun loadAsync(url: String) {
		CompletableFuture.supplyAsync<ByteArray?> {
			try {
				val request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build()

				val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
				if (response.statusCode() !in 200..299) {
					return@supplyAsync null
				}

				val image = ImageIO.read(ByteArrayInputStream(response.body()))
				if (image == null) {
					return@supplyAsync null
				}

				val pngBytes = ByteArrayOutputStream().use { out ->
					ImageIO.write(image, "png", out)
					out.toByteArray()
				}

				pngBytes
			} catch (e: Exception) {
				null
			}
		}.thenAccept { bytes ->
			if (bytes == null) return@thenAccept
			Minecraft.getInstance().execute {
				try {
					val image = NativeImage.read(ByteArrayInputStream(bytes))
					val id = Identifier.fromNamespaceAndPath("official-server-list", "image/${url.hashCode().toUInt()}")
					val texture = DynamicTexture(Supplier { "official-server-list-image-$id" }, image)

					Minecraft.getInstance().textureManager.register(id, texture)
					textureRefs[url] = texture
					cache[url] = ImageTexture(id, image.width, image.height)
				} catch (e: Exception) {}
			}
		}
	}
}