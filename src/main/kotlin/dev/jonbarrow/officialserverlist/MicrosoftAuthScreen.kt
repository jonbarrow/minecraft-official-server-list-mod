package dev.jonbarrow.officialserverlist

import java.net.URI
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.dimaskama.mcef.api.MCEFApi
import net.dimaskama.mcef.api.MCEFBrowser
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.BlitRenderState
import net.minecraft.network.chat.Component
import org.joml.Matrix3x2f
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.lwjgl.glfw.GLFW;

class MicrosoftAuthScreen(private val parent: Screen, private val authType: AuthType, private val onResult: (String?) -> Unit) : Screen(Component.translatable("officialserverlist.screen.auth.title")) {
	companion object {
		private const val LOGIN_PAGE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=1e25a03a-e733-4f0a-a639-0cd9fc8f6224&scope=user.read&response_type=code&prompt=login&approval_prompt=auto&redirect_uri=https://1yrsextl7j.execute-api.us-east-1.amazonaws.com/prod/auth/microsoft/connection"
		private const val LOGIN_CALLBACK_PREFIX = "https://findmcserver.com/auth/"

		private const val LINK_MINECRAFT_PAGE = "https://login.live.com/oauth20_authorize.srf?client_id=1e25a03a-e733-4f0a-a639-0cd9fc8f6224&response_type=code&prompt=login&approval_prompt=auto&scope=Xboxlive.signin&redirect_uri=https://findmcserver.com/auth/minecraft-login"
		private const val LINK_MINECRAFT_CALLBACK_PREFIX = "https://findmcserver.com/auth/minecraft-login"
	}

	private var browser: MCEFBrowser? = null
	private var captured: Boolean = false
	private var resolved: Boolean = false

	private var scaleX: Double = 1.0
	private var scaleY: Double = 1.0

	enum class AuthType {
		LOGIN,
		LINK_MINECRAFT
	}

	override fun init() {
		if (browser == null) {
			browser = when (authType) {
				AuthType.LOGIN ->  MCEFApi.getInstance().createBrowser(LOGIN_PAGE, false)
				AuthType.LINK_MINECRAFT ->  MCEFApi.getInstance().createBrowser(LINK_MINECRAFT_PAGE, false)
			}
		}

		// * Have to scale the UI otherwise it gets really zoomed in
		scaleX = minecraft.window.width.toDouble() / this.width
		scaleY = minecraft.window.height.toDouble() / this.height

		browser?.resize(this.scaledX(this.width).toInt(), this.scaledY(this.height).toInt())
		browser?.setFocus(true)
	}

	private fun scaledX(x: Int): Double {
		return x * scaleX
	}

	private fun scaledX(x: Double): Double {
		return x * scaleX
	}

	private fun scaledY(y: Int): Double {
		return y * scaleY
	}

	private fun scaledY(y: Double): Double {
		return y * scaleY
	}

	private fun checkURL() {
		if (captured) return
		val url = browser?.cefBrowser?.url ?: return

		// * Check this first since both endpoints have a common prefix
		if (!captured && url.startsWith(LINK_MINECRAFT_CALLBACK_PREFIX)) {
			captured = true
			finish(extractCode(url))
		}

		if (!captured && url.startsWith(LOGIN_CALLBACK_PREFIX)) {
			captured = true
			finish(extractHash(url))
		}
	}

	private fun extractHash(url: String): String {
		return url.substring(LOGIN_CALLBACK_PREFIX.length)
				.substringBefore('?') // * I don't think this should be here, but just in case
				.substringBefore('#') // * I don't think this should be here, but just in case
				.trim('/')
	}

	private fun extractCode(url: String): String? {
		val queryParams = URI(url).query.split("&").associate { pair ->
			val (key, value) = pair.split("=", limit=2)
			key to value
		}

		return queryParams["code"]
	}

	private fun finish(hash: String?) {
		if (resolved) return
		resolved = true

		browser?.close()
		browser = null

		minecraft.setScreen(parent)
		onResult(hash)
	}

	private fun cefFrame(): CefFrame? {
		return browser?.cefBrowser?.focusedFrame ?: browser?.cefBrowser?.mainFrame
	}

	private fun remapNumpadDigit(event: KeyEvent): KeyEvent {
		// * MCEF Modern maps GLFW_KEY_KP_* to VK_NUMPAD*, which can have unexpected
		// * side effects (numpad-6 triggers CEF fullscreen). This remaps numpad keys
		// * to top-row keys to prevent this
		val remapped = when (event.key()) {
			GLFW.GLFW_KEY_KP_0 -> GLFW.GLFW_KEY_0
			GLFW.GLFW_KEY_KP_1 -> GLFW.GLFW_KEY_1
			GLFW.GLFW_KEY_KP_2 -> GLFW.GLFW_KEY_2
			GLFW.GLFW_KEY_KP_3 -> GLFW.GLFW_KEY_3
			GLFW.GLFW_KEY_KP_4 -> GLFW.GLFW_KEY_4
			GLFW.GLFW_KEY_KP_5 -> GLFW.GLFW_KEY_5
			GLFW.GLFW_KEY_KP_6 -> GLFW.GLFW_KEY_6
			GLFW.GLFW_KEY_KP_7 -> GLFW.GLFW_KEY_7
			GLFW.GLFW_KEY_KP_8 -> GLFW.GLFW_KEY_8
			GLFW.GLFW_KEY_KP_9 -> GLFW.GLFW_KEY_9
			else -> return event
		}

		return KeyEvent(remapped, event.scancode(), event.modifiers())
	}

	private fun isClipboardModifier(event: KeyEvent): Boolean {
		val mods = event.modifiers()

		// HACK - This is gross, is there a better way to detect the platform?
		return if (System.getProperty("os.name").lowercase().contains("mac")) {
			(mods and GLFW.GLFW_MOD_SUPER) != 0
		} else {
			(mods and GLFW.GLFW_MOD_CONTROL) != 0
		}
	}

	override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		// * MCEF Modern doesn't have a "check if the URL changed" hook, so we have to
		// * poll the current URL every frame
		checkURL()

		val gpuTextureView = browser?.textureView
		if (gpuTextureView != null) {
			guiGraphics.guiRenderState.addGuiElement(
				BlitRenderState(
					RenderPipelines.GUI_TEXTURED,
					TextureSetup.singleTexture(gpuTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
					Matrix3x2f(guiGraphics.pose()),
					0,
					0,
					width,
					height,
					0.0f,
					1.0f,
					0.0f,
					1.0f,
					0xFFFFFFFF.toInt(),
					guiGraphics.scissorStack.peek()
				)
			)
		}
		browser?.let { guiGraphics.requestCursor(it.cursorType) }
	}

	override fun onClose() {
		finish(null)
	}

	override fun removed() {
		if (!resolved) {
			browser?.close()
			browser = null
		}
		super.removed()
	}

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		// * Ran into issues with not being able to click back into input fields
		// * after opening another window, manually setting focus back seems to
		// * fix that. Unsure if there's a better way
		browser?.setFocus(true)

		val scaledEvent = MouseButtonEvent(
			this.scaledX(event.x()),
			this.scaledY(event.y()),
			event.buttonInfo()
		)

		browser?.onMouseClicked(scaledEvent, doubled)
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val scaledEvent = MouseButtonEvent(
			this.scaledX(event.x()),
			this.scaledY(event.y()),
			event.buttonInfo()
		)

		browser?.onMouseReleased(scaledEvent)
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
		browser?.onMouseScrolled(this.scaledX(mouseX).toInt(), this.scaledY(mouseY).toInt(), verticalAmount)
		return true
	}

	override fun mouseMoved(x: Double, y: Double) {
		browser?.onMouseMoved(this.scaledX(x).toInt(), this.scaledY(y).toInt())
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			finish(null)
			return true
		}

		val remapped = remapNumpadDigit(event)

		// * MCEF Modern doesn't seem to handle this itself, so we have to do it
		if (isClipboardModifier(remapped)) {
			val frame = cefFrame()
			when (remapped.key()) {
				GLFW.GLFW_KEY_V -> {
					frame?.paste();
					return true
				}
				GLFW.GLFW_KEY_C -> {
					frame?.copy();
					return true
				}
				GLFW.GLFW_KEY_X -> {
					frame?.cut();
					return true
				}
				GLFW.GLFW_KEY_A -> {
					frame?.selectAll();
					return true
				}
			}
		}

		browser?.onKeyPressed(remapped)
		return true
	}

	override fun keyReleased(event: KeyEvent): Boolean {
		browser?.onKeyReleased(event)
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		browser?.onCharTyped(event)
		return true
	}
}