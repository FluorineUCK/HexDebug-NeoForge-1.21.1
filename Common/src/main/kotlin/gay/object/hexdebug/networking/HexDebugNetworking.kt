package gay.`object`.hexdebug.networking

import dev.architectury.networking.NetworkManager
import gay.`object`.hexdebug.HexDebug
import gay.`object`.hexdebug.networking.msg.HexDebugMessage
import gay.`object`.hexdebug.networking.msg.HexDebugMessageC2S
import gay.`object`.hexdebug.networking.msg.HexDebugMessageCompanion
import gay.`object`.hexdebug.networking.msg.HexDebugMessageS2C
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.Supplier

object HexDebugNetworking {
    private data class MessageInfo<T : HexDebugMessage>(
        val id: ResourceLocation,
        val companion: HexDebugMessageCompanion<T>,
    )

    private val messages = mutableMapOf<Class<*>, MessageInfo<*>>()
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        for (subclass in HexDebugMessageCompanion::class.sealedSubclasses) {
            subclass.objectInstance?.register()
        }
    }

    fun <T : HexDebugMessage> register(companion: HexDebugMessageCompanion<T>) {
        val id = packetId(companion.type)
        messages[companion.type] = MessageInfo(id, companion)

        val receiver = NetworkManager.NetworkReceiver<RegistryFriendlyByteBuf> { buf, context ->
            companion.apply(companion.decode(buf), Supplier { context })
        }

        if (HexDebugMessageC2S::class.java.isAssignableFrom(companion.type)) {
            NetworkManager.registerReceiver(NetworkManager.c2s(), id, receiver)
        }
        if (HexDebugMessageS2C::class.java.isAssignableFrom(companion.type)) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), id, receiver)
        }
    }

    fun sendToServer(message: HexDebugMessageC2S) {
        val (id, buf) = encode(message)
        NetworkManager.sendToServer(id, buf)
    }

    fun sendToPlayer(player: net.minecraft.server.level.ServerPlayer, message: HexDebugMessageS2C) {
        val (id, buf) = encode(message)
        NetworkManager.sendToPlayer(player, id, buf)
    }

    fun sendToPlayers(players: Iterable<net.minecraft.server.level.ServerPlayer>, message: HexDebugMessageS2C) {
        val (id, buf) = encode(message)
        NetworkManager.sendToPlayers(players, id, buf)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : HexDebugMessage> encode(message: T): Pair<ResourceLocation, RegistryFriendlyByteBuf> {
        val info = messages[message.javaClass] as? MessageInfo<T>
            ?: error("Unknown message type! $message")
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        with(info.companion) {
            message.encode(buf)
        }
        return info.id to buf
    }

    private fun packetId(type: Class<*>): ResourceLocation {
        val id = UUID.nameUUIDFromBytes(type.name.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
        return HexDebug.id("networking_channel/$id")
    }
}
