package gay.`object`.hexdebug.casting.eval

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import at.petrak.hexcasting.api.casting.eval.CastResult
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import at.petrak.hexcasting.api.utils.TreeList
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerLevel

data class FrameBreakpoint(val stopBefore: Boolean, val isFatal: Boolean = false) : ContinuationFrame {
    override fun breakDownwards(stack: TreeList<Iota>) = false to stack

    override fun evaluate(continuation: SpellContinuation, level: ServerLevel, harness: CastingVM) = CastResult(
        NullIota(),
        continuation,
        null,
        listOf(),
        ResolvedPatternType.EVALUATED,
        HexEvalSounds.NOTHING.get(),
    )

    override fun size() = 0

    override val type = TYPE

    companion object {
        @JvmField
        val TYPE = object : ContinuationFrame.Type<FrameBreakpoint> {
            override fun codec(): MapCodec<FrameBreakpoint> = CODEC

            override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, FrameBreakpoint> = STREAM_CODEC
        }

        private val CODEC: MapCodec<FrameBreakpoint> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                // 0.11 used camelCase. Keep it canonical so persisted slates/circles remain readable.
                Codec.BOOL.optionalFieldOf("stopBefore", false).forGetter(FrameBreakpoint::stopBefore),
                Codec.BOOL.optionalFieldOf("isFatal", false).forGetter(FrameBreakpoint::isFatal),
                // Accept saves produced by early 1.21 port builds as well.
                Codec.BOOL.optionalFieldOf("stop_before", false).forGetter { false },
                Codec.BOOL.optionalFieldOf("is_fatal", false).forGetter { false },
            ).apply(instance) { stopBefore, isFatal, snakeStopBefore, snakeIsFatal ->
                FrameBreakpoint(stopBefore || snakeStopBefore, isFatal || snakeIsFatal)
            }
        }

        private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FrameBreakpoint> = StreamCodec.composite(
            ByteBufCodecs.BOOL.mapStream { it },
            FrameBreakpoint::stopBefore,
            ByteBufCodecs.BOOL.mapStream { it },
            FrameBreakpoint::isFatal,
            ::FrameBreakpoint,
        )

        fun fatal() = FrameBreakpoint(stopBefore = true, isFatal = true)
    }
}
