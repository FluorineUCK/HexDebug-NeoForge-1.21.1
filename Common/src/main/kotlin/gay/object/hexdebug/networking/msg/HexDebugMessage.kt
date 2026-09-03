package gay.`object`.hexdebug.networking.msg

import dev.architectury.networking.NetworkManager.PacketContext
import gay.`object`.hexdebug.HexDebug
import gay.`object`.hexdebug.networking.HexDebugNetworking
import gay.`object`.hexdebug.networking.handler.applyOnClient
import gay.`object`.hexdebug.networking.handler.applyOnServer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import java.util.function.Supplier

sealed interface HexDebugMessage

sealed interface HexDebugMessageC2S : HexDebugMessage {
    fun sendToServer() {
        HexDebugNetworking.sendToServer(this)
    }
}

sealed interface HexDebugMessageS2C : HexDebugMessage {
    fun sendToPlayer(player: ServerPlayer) {
        HexDebugNetworking.sendToPlayer(player, this)
    }

    fun sendToPlayers(players: Iterable<ServerPlayer>) {
        HexDebugNetworking.sendToPlayers(players, this)
    }
}

sealed interface HexDebugMessageCompanion<T : HexDebugMessage> {
    val type: Class<T>

    fun decode(buf: FriendlyByteBuf): T

    fun T.encode(buf: FriendlyByteBuf)

    fun apply(msg: T, supplier: Supplier<PacketContext>) {
        val ctx = supplier.get()
        when (msg) {
            is HexDebugMessageC2S -> {
                HexDebug.LOGGER.debug("Server received packet from {}: {}", ctx.player.name.string, this)
                msg.applyOnServer(ctx)
            }
            is HexDebugMessageS2C -> {
                HexDebug.LOGGER.debug("Client received packet: {}", this)
                msg.applyOnClient(ctx)
            }
        }
    }

    fun register() {
        HexDebugNetworking.register(this)
    }
}
