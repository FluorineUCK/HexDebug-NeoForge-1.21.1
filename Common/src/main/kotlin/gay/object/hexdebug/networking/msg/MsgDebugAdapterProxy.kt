package gay.`object`.hexdebug.networking.msg

import net.minecraft.network.FriendlyByteBuf

data class MsgDebugAdapterProxyC2S(val content: String) : HexDebugMessageC2S {
    companion object : HexDebugMessageCompanion<MsgDebugAdapterProxyC2S> {
        override val type = MsgDebugAdapterProxyC2S::class.java

        override fun decode(buf: FriendlyByteBuf) = MsgDebugAdapterProxyC2S(
            buf.readUtf(),
        )

        override fun MsgDebugAdapterProxyC2S.encode(buf: FriendlyByteBuf) {
            buf.writeUtf(content)
        }
    }
}

data class MsgDebugAdapterProxyS2C(val content: String) : HexDebugMessageS2C {
    companion object : HexDebugMessageCompanion<MsgDebugAdapterProxyS2C> {
        override val type = MsgDebugAdapterProxyS2C::class.java

        override fun decode(buf: FriendlyByteBuf) = MsgDebugAdapterProxyS2C(
            buf.readUtf(),
        )

        override fun MsgDebugAdapterProxyS2C.encode(buf: FriendlyByteBuf) {
            buf.writeUtf(content)
        }
    }
}
