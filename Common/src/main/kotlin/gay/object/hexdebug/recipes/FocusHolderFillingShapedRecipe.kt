package gay.`object`.hexdebug.recipes

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import gay.`object`.hexdebug.items.FocusHolderBlockItem.Companion.hasIotaStack
import gay.`object`.hexdebug.items.FocusHolderBlockItem.Companion.setIotaStack
import gay.`object`.hexdebug.registry.HexDebugBlocks
import gay.`object`.hexdebug.registry.HexDebugRecipeSerializers
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.level.Level
import java.util.Optional

class FocusHolderFillingShapedRecipe(
    group: String,
    category: CraftingBookCategory,
    pattern: net.minecraft.world.item.crafting.ShapedRecipePattern,
    result: ItemStack,
    val resultInner: ItemStack,
    showNotification: Boolean,
) : ShapedRecipe(group, category, pattern, result, showNotification) {
    override fun matches(input: CraftingInput, level: Level): Boolean {
        if (!super.matches(input, level)) return false
        for (ingredient in input.items()) {
            // don't allow filling a holder that's already filled
            if (ingredient.`is`(HexDebugBlocks.FOCUS_HOLDER.item) && ingredient.hasIotaStack) {
                return false
            }
        }
        return true
    }

    override fun getSerializer() = HexDebugRecipeSerializers.FOCUS_HOLDER_FILLING_SHAPED.value

    companion object {
        private fun fromShapedRecipe(recipe: ShapedRecipe, resultInner: ItemStack): FocusHolderFillingShapedRecipe {
            return recipe.run {
                FocusHolderFillingShapedRecipe(
                    group = group,
                    category = category(),
                    pattern = patternFrom(recipe),
                    result = ItemStack(HexDebugBlocks.FOCUS_HOLDER.item).setIotaStack(resultInner),
                    resultInner = resultInner,
                    showNotification = showNotification(),
                )
            }
        }

        private fun patternFrom(recipe: ShapedRecipe) =
            ShapedRecipePattern(recipe.width, recipe.height, recipe.ingredients, Optional.empty())
    }

    class Serializer : RecipeSerializer<FocusHolderFillingShapedRecipe> {
        override fun codec(): MapCodec<FocusHolderFillingShapedRecipe> = CODEC

        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, FocusHolderFillingShapedRecipe> = STREAM_CODEC

        companion object {
            val CODEC: MapCodec<FocusHolderFillingShapedRecipe> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    ShapedRecipe.Serializer.CODEC.forGetter<FocusHolderFillingShapedRecipe> { it },
                    ItemStack.STRICT_CODEC.fieldOf("result_inner").forGetter(FocusHolderFillingShapedRecipe::resultInner),
                ).apply(instance, ::fromShapedRecipe)
            }

            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FocusHolderFillingShapedRecipe> = StreamCodec.composite(
                ShapedRecipe.Serializer.STREAM_CODEC,
                { recipe: FocusHolderFillingShapedRecipe -> recipe },
                ItemStack.STREAM_CODEC,
                FocusHolderFillingShapedRecipe::resultInner,
                ::fromShapedRecipe,
            )
        }
    }
}
