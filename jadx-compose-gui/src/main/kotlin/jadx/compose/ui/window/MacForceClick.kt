package jadx.compose.ui.window

import androidx.compose.runtime.staticCompositionLocalOf
import java.io.Closeable
import java.lang.reflect.Proxy
import javax.swing.JComponent

internal val LocalForceClick = staticCompositionLocalOf<MacForceClick?> { null }

/** Optional JBR macOS bridge. No Apple classes are linked on Windows or Linux. */
internal class MacForceClick(private val root: JComponent) : Closeable {
	var target: (() -> Unit)? = null
	private var remove: (() -> Unit)? = null
	private var closed = false

	init {
		if (isMacOS) runCatching {
			val listenerType = Class.forName("com.apple.eawt.event.PressureListener")
			val gestureType = Class.forName("com.apple.eawt.event.GestureListener")
			val eventType = Class.forName("com.apple.eawt.event.PressureEvent")
			val utilities = Class.forName("com.apple.eawt.event.GestureUtilities")
			val getStage = eventType.getMethod("getStage")
			val consume = eventType.getMethod("consume")
			var deepPressed = false
			val listener = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, method, args ->
				when (method.name) {
					"pressure" -> {
						val event = requireNotNull(args)[0]
						val deep = (getStage.invoke(event) as Double) >= 2.0
						if (!closed && deep && !deepPressed) target?.let { action -> action(); consume.invoke(event) }
						deepPressed = deep
						null
					}
					"equals" -> proxy === args?.get(0)
					"hashCode" -> System.identityHashCode(proxy)
					"toString" -> "JadxForceClickListener"
					else -> null
				}
			}
			utilities.getMethod("addGestureListenerTo", JComponent::class.java, gestureType).invoke(null, root, listener)
			remove = { utilities.getMethod("removeGestureListenerFrom", JComponent::class.java, gestureType).invoke(null, root, listener) }
		}.onFailure { System.err.println("Force Click unavailable: ${it.message}") }
	}

	override fun close() {
		closed = true
		target = null
		remove?.invoke()
		remove = null
	}
}
