package gay.`object`.hexdebug.hexcompat

import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.*
import at.petrak.hexcasting.api.casting.math.HexAngle
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import gay.`object`.hexdebug.casting.iotas.CognitohazardIota
import net.minecraft.nbt.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

private const val LEGACY_TYPE = "hexcasting:type"
private const val LEGACY_DATA = "hexcasting:data"
private const val MAX_LEGACY_IOTA_DEPTH = 256
private const val MAX_LEGACY_IOTA_COUNT = 1024

private class LegacyReadLimitExceeded : RuntimeException()

private class LegacyReadBudget(var remaining: Int = MAX_LEGACY_IOTA_COUNT) {
    fun consume() {
        if (remaining <= 0) throw LegacyReadLimitExceeded()
        remaining--
    }
}

/** Reads the typed-Iota envelope written by Hex Casting 0.11.x. */
internal fun isLegacyIotaTag(tag: CompoundTag): Boolean =
    tag.contains(LEGACY_TYPE, Tag.TAG_STRING.toInt()) && tag.contains(LEGACY_DATA)

internal fun deserializeLegacyIota(tag: CompoundTag, level: ServerLevel?): Iota = try {
    deserializeLegacyIota(tag, level, 0, LegacyReadBudget())
} catch (_: LegacyReadLimitExceeded) {
    GarbageIota()
}

private fun deserializeLegacyIota(
    tag: CompoundTag,
    level: ServerLevel?,
    depth: Int,
    budget: LegacyReadBudget,
): Iota {
    if (depth >= MAX_LEGACY_IOTA_DEPTH) throw LegacyReadLimitExceeded()
    budget.consume()

    val data = tag.get(LEGACY_DATA) ?: return GarbageIota()
    return try {
        when (tag.getString(LEGACY_TYPE)) {
            "hexcasting:null" -> NullIota()
            "hexcasting:garbage" -> GarbageIota()
            "hexcasting:double" -> DoubleIota((data as? NumericTag)?.asDouble ?: return GarbageIota())
            "hexcasting:boolean" -> BooleanIota((data as? NumericTag)?.asByte?.toInt() != 0)
            "hexcasting:vec3" -> Vec3Iota(deserializeLegacyVec3(data) ?: return GarbageIota())
            "hexcasting:pattern" -> PatternIota(deserializeLegacyPattern(data) ?: return GarbageIota())
            "hexcasting:list" -> {
                val entries = data as? ListTag ?: return GarbageIota()
                if (entries.size > budget.remaining) throw LegacyReadLimitExceeded()
                ListIota(entries.map { child ->
                    val childTag = child as? CompoundTag ?: return@map GarbageIota()
                    if (!isLegacyIotaTag(childTag)) GarbageIota()
                    else deserializeLegacyIota(childTag, level, depth + 1, budget)
                })
            }
            "hexcasting:entity" -> {
                val entity = data as? CompoundTag ?: return GarbageIota()
                val uuid = entity.get("uuid")?.let { runCatching { NbtUtils.loadUUID(it) }.getOrNull() }
                    ?: return GarbageIota()
                EntityIota(uuid, null, true)
            }
            "hexcasting:continuation" -> SpellContinuation.CODEC
                .parse(NbtOps.INSTANCE, data)
                .result()
                .orElse(null)
                ?.let(::ContinuationIota)
                ?: GarbageIota()
            "hexdebug:cognitohazard" -> CognitohazardIota()
            else -> decodeLegacyBestEffort(tag.getString(LEGACY_TYPE), data)
        }
    } catch (limit: LegacyReadLimitExceeded) {
        throw limit
    } catch (_: RuntimeException) {
        GarbageIota()
    }
}

/**
 * Third-party legacy iotas whose payload field names stayed stable can often be
 * decoded without linking their implementation classes.
 */
private fun decodeLegacyBestEffort(type: String, data: Tag): Iota {
    val candidate = CompoundTag().apply {
        putString("type", type)
        if (data is CompoundTag) {
            for (key in data.allKeys) {
                data.get(key)?.let { put(key, it.copy()) }
            }
        } else {
            put("value", data.copy())
        }
    }
    return IotaType.TYPED_CODEC
        .parse(NbtOps.INSTANCE, candidate)
        .result()
        .orElse(null)
        ?.takeUnless { it is GarbageIota }
        ?: GarbageIota()
}

private fun deserializeLegacyPattern(tag: Tag): HexPattern? = runCatching {
    val compound = tag as? CompoundTag ?: return null
    if (!compound.contains("start_dir", Tag.TAG_ANY_NUMERIC.toInt()) ||
        !compound.contains("angles", Tag.TAG_BYTE_ARRAY.toInt())
    ) return null

    val start = HexDir.entries.getOrNull(compound.getByte("start_dir").toInt()) ?: return null
    val angles = (compound.get("angles") as ByteArrayTag).asByteArray.map { ordinal ->
        HexAngle.entries.getOrNull(ordinal.toInt()) ?: return null
    }.toMutableList()
    HexPattern(start, angles)
}.getOrNull()

private fun deserializeLegacyVec3(tag: Tag): Vec3? = when (tag) {
    is LongArrayTag -> tag.asLongArray.takeIf { it.size == 3 }?.let {
        Vec3(Double.fromBits(it[0]), Double.fromBits(it[1]), Double.fromBits(it[2]))
    }
    is CompoundTag -> if (tag.contains("x") && tag.contains("y") && tag.contains("z")) {
        Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"))
    } else null
    else -> null
}
