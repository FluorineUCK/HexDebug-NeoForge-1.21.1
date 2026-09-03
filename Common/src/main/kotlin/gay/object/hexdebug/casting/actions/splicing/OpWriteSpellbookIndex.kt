package gay.`object`.hexdebug.casting.actions.splicing

import at.petrak.hexcasting.api.block.HexBlockEntity
import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getBlockPos
import at.petrak.hexcasting.api.casting.getIntBetween
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.common.items.storage.ItemSpellbook
import at.petrak.hexcasting.common.lib.HexDataComponents
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.max

class OpWriteSpellbookIndex(private val useListItem: Boolean) : SpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result {
        val pos = args.getBlockPos(0, argc)
        val index = args.getIntBetween(idx=1, min=1, max=ItemSpellbook.MAX_PAGES, argc=argc)

        env.assertPosInRangeForEditing(pos)

        val (blockEntity, stack) = OpReadSpellbookIndex.getSpellbook(env, pos, useListItem)

        return SpellAction.Result(
            Spell(blockEntity, stack, index),
            0,
            listOf(ParticleSpray(pos.center, Vec3(1.0, 0.0, 0.0), 0.25, 3.14, 40))
        )
    }

    private data class Spell(val blockEntity: HexBlockEntity, val stack: ItemStack, val index: Int) : RenderedSpell {
        override fun cast(env: CastingEnvironment) {
            // copied from ItemSpellbook.rotatePageIdx with modifications
            stack.set(HexDataComponents.SELECTED_SPELLBOOK_PAGE.get(), index)

            val names = stack.getOrDefault(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), emptyMap<String, Component>())
            val shiftedIndex = max(1, index)
            val nameKey = shiftedIndex.toString()
            val name = names[nameKey]
            if (name != null) {
                stack.set(DataComponents.CUSTOM_NAME, name)
            } else {
                stack.remove(DataComponents.CUSTOM_NAME)
            }

            blockEntity.sync()
        }
    }
}
