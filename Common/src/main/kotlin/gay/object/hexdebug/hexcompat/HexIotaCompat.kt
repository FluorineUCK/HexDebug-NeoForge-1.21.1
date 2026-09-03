@file:JvmName("HexIotaCompat")

package gay.`object`.hexdebug.hexcompat

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.IotaType
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import net.minecraft.server.level.ServerLevel

fun serializeIota(iota: Iota): CompoundTag {
    val encoded = IotaType.TYPED_CODEC
        .encodeStart(NbtOps.INSTANCE, iota)
        .result()
        .orElse(null)
    return encoded as? CompoundTag
        ?: throw IllegalArgumentException("Hex Casting iota ${iota.javaClass.name} did not encode to a compound tag")
}

fun deserializeIota(tag: Tag, level: ServerLevel? = null): Iota? {
    val iota = if (tag is CompoundTag && isLegacyIotaTag(tag)) {
        deserializeLegacyIota(tag, level)
    } else {
        IotaType.TYPED_CODEC
            .parse(NbtOps.INSTANCE, tag)
            .result()
            .orElse(null)
    }
        ?: return null

    return if (level == null || iota.isValidIn(level)) iota else null
}

fun displayIota(tag: CompoundTag): Component {
    return deserializeIota(tag)?.display() ?: IotaType.brokenIota()
}

fun iotaTypeFromTag(tag: CompoundTag): IotaType<*>? {
    return deserializeIota(tag)?.type
}

fun IotaType<*>.typeName(): Component {
    return Component.literal(HexIotaTypes.REGISTRY.getKey(this)?.toString() ?: toString())
}

@Suppress("UNCHECKED_CAST")
private fun Iota.isValidIn(level: ServerLevel): Boolean {
    return (type as IotaType<Iota>).validate(this, level)
}
