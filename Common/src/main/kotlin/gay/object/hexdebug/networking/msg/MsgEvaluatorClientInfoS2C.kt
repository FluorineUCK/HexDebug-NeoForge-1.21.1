package gay.`object`.hexdebug.networking.msg

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import gay.`object`.hexdebug.hexcompat.deserializeIota
import gay.`object`.hexdebug.hexcompat.serializeIota
import net.minecraft.network.FriendlyByteBuf

/**
 * Similar to [at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C], but is only applied if holding an Evaluator.
 * This avoids interfering with other staves' client view when stepping through a debug session.
 */
data class MsgEvaluatorClientInfoS2C(val threadId: Int?, val info: ExecutionClientView) : HexDebugMessageS2C {
    companion object : HexDebugMessageCompanion<MsgEvaluatorClientInfoS2C> {
        override val type = MsgEvaluatorClientInfoS2C::class.java

        override fun decode(buf: FriendlyByteBuf) = MsgEvaluatorClientInfoS2C(
            threadId = buf.readNullable(FriendlyByteBuf::readInt),
            info = ExecutionClientView(
                isStackClear = buf.readBoolean(),
                resolutionType = buf.readEnum(ResolvedPatternType::class.java),
                stackDescs = buf.readList(FriendlyByteBuf::readNbt).mapNotNull { it?.let(::deserializeIota) },
                ravenmind = buf.readNullable(FriendlyByteBuf::readNbt)?.let(::deserializeIota),
            ),
        )

        override fun MsgEvaluatorClientInfoS2C.encode(buf: FriendlyByteBuf) {
            buf.writeNullable(threadId, FriendlyByteBuf::writeInt)
            info.apply {
                buf.writeBoolean(isStackClear)
                buf.writeEnum(resolutionType)
                buf.writeCollection(stackDescs) { packetBuf, iota -> packetBuf.writeNbt(serializeIota(iota)) }
                buf.writeNullable(ravenmind) { packetBuf, iota ->
                    packetBuf.writeNbt(serializeIota(iota))
                }
            }
        }
    }
}
