package gay.`object`.hexdebug.casting.iotas

import at.petrak.hexcasting.api.casting.eval.CastResult
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import at.petrak.hexcasting.api.utils.asTranslatedComponent
import at.petrak.hexcasting.api.utils.black
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.codec.StreamCodec

/** An iota that terminates a debugging session if attempted to be executed. */
class CognitohazardIota : Iota({ TYPE }) {
    override fun isTruthy() = true

    override fun toleratesOther(that: Iota) = typesMatch(this, that)

    override fun display() = DISPLAY

    override fun hashCode() = 0xC096170

    override fun execute(vm: CastingVM, world: ServerLevel, continuation: SpellContinuation): CastResult {
        // we shouldn't need any special handling in here, since the cognitohazard should be detected by the debugger before we get to this point
        return CastResult(
            this,
            continuation,
            null,
            listOf(),
            ResolvedPatternType.EVALUATED,
            HexEvalSounds.NOTHING.get(),
        )
    }

    override fun executable() = true

    companion object {
        val DISPLAY = "hexdebug.tooltip.cognitohazard_iota".asTranslatedComponent.black

        val TYPE = object : IotaType<CognitohazardIota>() {
            override fun codec(): MapCodec<CognitohazardIota> = CODEC

            override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, CognitohazardIota> = STREAM_CODEC

            override fun color() = 0xff_000000.toInt()
        }

        private val CODEC: MapCodec<CognitohazardIota> = MapCodec.unit(::CognitohazardIota)

        private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CognitohazardIota> =
            StreamCodec.unit(CognitohazardIota())
    }
}
