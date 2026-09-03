package gay.`object`.hexdebug.recipes

import com.mojang.serialization.MapCodec
import gay.`object`.hexdebug.registry.HexDebugItems
import gay.`object`.hexdebug.registry.HexDebugRecipeSerializers
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapedRecipePattern
import java.util.Optional

class FlyswatterQuenchingShapedRecipe(
    group: String,
    category: CraftingBookCategory,
    pattern: net.minecraft.world.item.crafting.ShapedRecipePattern,
    result: ItemStack,
    showNotification: Boolean,
) : ShapedRecipe(group, category, pattern, result, showNotification) {
    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        var original: ItemStack? = null
        for (stack in input.items()) {
            if (stack.`is`(HexDebugItems.DEBUGGER.value) || stack.`is`(HexDebugItems.EVALUATOR.value)) {
                original = stack
                break
            }
        }

        return super.assemble(input, registries).also {
            original?.componentsPatch?.let(it::applyComponents)
        }
    }

    override fun getSerializer() = HexDebugRecipeSerializers.FLYSWATTER_QUENCHING.value

    companion object {
        private fun fromShapedRecipe(recipe: ShapedRecipe): FlyswatterQuenchingShapedRecipe {
            return recipe.run {
                FlyswatterQuenchingShapedRecipe(
                    group = group,
                    category = category(),
                    pattern = patternFrom(recipe),
                    result = getResultItem(net.minecraft.core.RegistryAccess.EMPTY),
                    showNotification = showNotification(),
                )
            }
        }

        private fun patternFrom(recipe: ShapedRecipe) =
            ShapedRecipePattern(recipe.width, recipe.height, recipe.ingredients, Optional.empty())
    }

    class Serializer : RecipeSerializer<FlyswatterQuenchingShapedRecipe> {
        override fun codec(): MapCodec<FlyswatterQuenchingShapedRecipe> = CODEC

        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, FlyswatterQuenchingShapedRecipe> = STREAM_CODEC

        companion object {
            val CODEC: MapCodec<FlyswatterQuenchingShapedRecipe> =
                ShapedRecipe.Serializer.CODEC.xmap(::fromShapedRecipe) { it }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FlyswatterQuenchingShapedRecipe> =
                ShapedRecipe.Serializer.STREAM_CODEC.map(::fromShapedRecipe) { it }
        }
    }
}
