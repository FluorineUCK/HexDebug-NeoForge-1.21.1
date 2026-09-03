package gay.`object`.hexdebug.forge.probe

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType
import at.petrak.hexcasting.api.casting.iota.DoubleIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.iota.Vec3Iota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.common.lib.HexDataComponents
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.lib.hex.HexActions
import at.petrak.hexcasting.common.lib.hex.HexContinuationTypes
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes
import com.mojang.serialization.Codec
import gay.`object`.hexdebug.HexDebug
import gay.`object`.hexdebug.adapter.DebugAdapterManager
import gay.`object`.hexdebug.api.splicing.SplicingTableIotaClientView
import gay.`object`.hexdebug.blocks.focusholder.FocusHolderBlockEntity
import gay.`object`.hexdebug.blocks.splicing.SplicingTableBlockEntity
import gay.`object`.hexdebug.casting.eval.FrameBreakpoint
import gay.`object`.hexdebug.casting.iotas.CognitohazardIota
import gay.`object`.hexdebug.config.HexDebugServerConfig
import gay.`object`.hexdebug.core.api.HexDebugCoreAPI
import gay.`object`.hexdebug.hexcompat.deserializeIota
import gay.`object`.hexdebug.hexcompat.serializeIota
import gay.`object`.hexdebug.items.DebuggerItem
import gay.`object`.hexdebug.items.EvaluatorItem
import gay.`object`.hexdebug.items.FocusHolderBlockItem.Companion.getIotaStack
import gay.`object`.hexdebug.items.FocusHolderBlockItem.Companion.setIotaStack
import gay.`object`.hexdebug.networking.HexDebugNetworking
import gay.`object`.hexdebug.networking.msg.*
import gay.`object`.hexdebug.recipes.FlyswatterQuenchingShapedRecipe
import gay.`object`.hexdebug.recipes.FocusHolderFillingShapedRecipe
import gay.`object`.hexdebug.registry.*
import gay.`object`.hexdebug.splicing.Selection
import gay.`object`.hexdebug.splicing.SplicingTableAction
import gay.`object`.hexdebug.splicing.SplicingTableClientView
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap
import net.minecraft.core.BlockPos
import net.minecraft.SharedConstants
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.server.ServerStartedEvent
import java.util.Optional

/** Development-only, feature-shaped pre-2 regression probe. */
object HexDebugRuntimeProbe {
    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onServerStarted)
    }

    private fun onServerStarted(event: ServerStartedEvent) {
        var failures = 0
        fun check(name: String, test: () -> Unit) {
            try {
                test()
                HexDebug.LOGGER.info("[HEXDEBUG-RUNTIME-PROBE] {}=PASS", name)
            } catch (throwable: Throwable) {
                failures++
                HexDebug.LOGGER.error("[HEXDEBUG-RUNTIME-PROBE] {}=FAIL", name, throwable)
            }
        }

        val server = event.server
        val level = server.overworld()

        check("registries") { checkRegistries() }
        check("recipes_advancements") { checkResources(event) }
        check("current_codecs") { checkCurrentCodecs(level) }
        check("legacy_iota_codec") { checkLegacyIotaCodec(level) }
        check("legacy_breakpoint_codec") { checkLegacyBreakpointCodec() }
        check("network_codecs") { checkNetworkCodecs(level) }
        check("focus_holder_item") { checkFocusHolderItem() }
        check("block_entity_persistence") { checkBlockEntityPersistence(level) }
        check("menu") { checkMenu(level) }
        check("debugger_enchantments") { checkDebuggerEnchantments(level) }
        check("custom_recipes") { checkCustomRecipes(event, level) }
        check("splicing_table_actions") { checkSplicingTableActions(level) }
        check("debugger_lifecycle") { checkDebuggerLifecycle(level) }

        if (failures == 0) {
            HexDebug.LOGGER.info("[HEXDEBUG-RUNTIME-PROBE] aggregate=PASS")
        } else {
            HexDebug.LOGGER.error("[HEXDEBUG-RUNTIME-PROBE] aggregate=FAIL failures={}", failures)
        }
        scheduleHardExit(if (failures == 0) 0 else 1)
        server.halt(false)
    }

    private fun checkRegistries() {
        requireNamespaceCount(BuiltInRegistries.BLOCK.keySet(), "blocks", 3)
        requireNamespaceCount(BuiltInRegistries.ITEM.keySet(), "items", 7)
        requireNamespaceCount(BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet(), "block_entities", 2)
        requireNamespaceCount(BuiltInRegistries.MENU.keySet(), "menus", 1)
        requireNamespaceCount(BuiltInRegistries.RECIPE_SERIALIZER.keySet(), "recipe_serializers", 2)
        requireNamespaceCount(BuiltInRegistries.CREATIVE_MODE_TAB.keySet(), "creative_tabs", 1)
        requireNamespaceCount(HexActions.REGISTRY.keySet(), "actions", 22)
        requireNamespaceCount(HexContinuationTypes.REGISTRY.keySet(), "continuations", 1)
        requireNamespaceCount(HexIotaTypes.REGISTRY.keySet(), "iota_types", 1)

        val messagesField = HexDebugNetworking::class.java.getDeclaredField("messages").apply { isAccessible = true }
        val messages = messagesField.get(HexDebugNetworking) as Map<*, *>
        require(messages.size == 14) { "expected 14 packet registrations, found ${messages.size}" }
    }

    private fun requireNamespaceCount(ids: Iterable<net.minecraft.resources.ResourceLocation>, label: String, expected: Int) {
        val actual = ids.count { it.namespace == HexDebug.MODID }
        require(actual == expected) { "$label expected=$expected actual=$actual" }
    }

    private fun checkResources(event: ServerStartedEvent) {
        val recipeIds = listOf(
            "debugger",
            "evaluator",
            "focus_holder",
            "quenched_debugger",
            "quenched_evaluator",
            "splicing_table",
            "brainsweep/enlightened_splicing_table",
            "focus_holder_filling_shaped/focus",
        ).map(HexDebug::id)
        val missingRecipes = recipeIds.filterNot { event.server.recipeManager.byKey(it).isPresent }
        require(missingRecipes.isEmpty()) { "missing recipes: $missingRecipes" }

        val advancementIds = listOf(
            "recipes/brainsweep/brainsweep/enlightened_splicing_table",
            "recipes/misc/focus_holder",
            "recipes/misc/splicing_table",
            "recipes/misc/focus_holder_filling_shaped/focus",
            "recipes/tools/debugger",
            "recipes/tools/evaluator",
            "recipes/tools/quenched_debugger",
            "recipes/tools/quenched_evaluator",
        ).map(HexDebug::id)
        val missingAdvancements = advancementIds.filter { event.server.advancements.get(it) == null }
        require(missingAdvancements.isEmpty()) { "missing advancements: $missingAdvancements" }
    }

    private fun checkCurrentCodecs(level: ServerLevel) {
        val iotas = listOf<Iota>(
            CognitohazardIota(),
            DoubleIota(42.25),
            Vec3Iota(Vec3(1.25, -2.5, 3.75)),
            ListIota(listOf(DoubleIota(7.5), CognitohazardIota())),
        )
        for (iota in iotas) {
            val encoded = serializeIota(iota)
            val decoded = deserializeIota(encoded, level)
            require(decoded != null && decoded.javaClass == iota.javaClass && serializeIota(decoded) == encoded) {
                "iota round trip failed for ${iota.javaClass.name}: $encoded -> $decoded"
            }
        }

        val frame = FrameBreakpoint(stopBefore = true, isFatal = true)
        val codec: Codec<FrameBreakpoint> = FrameBreakpoint.TYPE.codec().codec()
        val encodedFrame = codec.encodeStart(NbtOps.INSTANCE, frame).getOrThrow()
        val decodedFrame = codec.parse(NbtOps.INSTANCE, encodedFrame).getOrThrow()
        require(decodedFrame == frame) { "frame codec mismatch: $frame -> $encodedFrame -> $decodedFrame" }

        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess())
        try {
            FrameBreakpoint.TYPE.streamCodec().encode(buf, frame)
            val decodedStream = FrameBreakpoint.TYPE.streamCodec().decode(buf)
            require(decodedStream == frame) { "frame stream codec mismatch: $frame -> $decodedStream" }
        } finally {
            buf.release()
        }
    }

    private fun checkLegacyIotaCodec(level: ServerLevel) {
        val legacyDouble = legacyIota("hexcasting:double", DoubleTag.valueOf(12.5))
        val decodedDouble = deserializeIota(legacyDouble, level) as? DoubleIota
        require(decodedDouble?.double == 12.5) { "legacy double did not decode: $decodedDouble" }

        val legacyCognitohazard = legacyIota("hexdebug:cognitohazard", CompoundTag())
        require(deserializeIota(legacyCognitohazard, level) is CognitohazardIota) {
            "legacy cognitohazard did not decode"
        }
    }

    private fun legacyIota(type: String, data: net.minecraft.nbt.Tag) = CompoundTag().apply {
        putString("hexcasting:type", type)
        put("hexcasting:data", data)
    }

    private fun checkLegacyBreakpointCodec() {
        val legacy = CompoundTag().apply {
            putBoolean("stopBefore", true)
            putBoolean("isFatal", true)
        }
        val decoded = FrameBreakpoint.TYPE.codec().codec().parse(NbtOps.INSTANCE, legacy).getOrThrow()
        require(decoded == FrameBreakpoint(true, true)) { "legacy frame mismatch: $decoded" }
    }

    private fun checkNetworkCodecs(level: ServerLevel) {
        assertPacketRoundTrip(MsgDebugAdapterProxyC2S("c2s-probe"), MsgDebugAdapterProxyC2S, level)
        assertPacketRoundTrip(MsgDebugAdapterProxyS2C("s2c-probe"), MsgDebugAdapterProxyS2C, level)
        assertPacketRoundTrip(
            MsgDebuggerStateS2C(mapOf(0 to DebuggerItem.DebugState.DEBUGGING, 3 to DebuggerItem.DebugState.NOT_DEBUGGING)),
            MsgDebuggerStateS2C,
            level,
        )
        assertPacketRoundTrip(
            MsgEvaluatorClientInfoS2C(
                2,
                ExecutionClientView(
                    false,
                    ResolvedPatternType.EVALUATED,
                    listOf(DoubleIota(9.25), CognitohazardIota()),
                    Vec3Iota(Vec3(3.0, 2.0, 1.0)),
                ),
            ),
            MsgEvaluatorClientInfoS2C,
            level,
        )
        assertPacketRoundTrip(MsgEvaluatorStateS2C(3, EvaluatorItem.EvalState.MODIFIED), MsgEvaluatorStateS2C, level)
        assertPacketRoundTrip(MsgPrintDebuggerStatusS2C("probe", 4, 7, true), MsgPrintDebuggerStatusS2C, level)
        assertPacketRoundTrip(MsgSplicingTableActionC2S(SplicingTableAction.SELECT_ALL), MsgSplicingTableActionC2S, level)
        assertPacketClassRoundTrip(MsgSplicingTableCastHexC2S(), MsgSplicingTableCastHexC2S, level)
        assertPacketClassRoundTrip(MsgSplicingTableGetDataC2S(), MsgSplicingTableGetDataC2S, level)

        val iotaTag = serializeIota(DoubleIota(5.5))
        val clientView = SplicingTableClientView(
            listOf(SplicingTableIotaClientView(iotaTag, Component.literal("probe"), "<5.5>", 2, 1)),
            iotaTag,
            isListWritable = true,
            isClipboardWritable = false,
            isEnlightened = true,
            hasHex = true,
            undoSize = 4,
            undoIndex = 2,
        )
        assertPacketRoundTrip(
            MsgSplicingTableNewDataS2C(clientView, Selection.range(1, 3), 2),
            MsgSplicingTableNewDataS2C,
            level,
        )
        val pattern = HexPattern.fromAngles("aqwed", HexDir.EAST)
        assertPacketRoundTrip(MsgSplicingTableNewStaffPatternC2S(pattern, 5), MsgSplicingTableNewStaffPatternC2S, level)
        assertPacketRoundTrip(
            MsgSplicingTableNewStaffPatternS2C(ResolvedPatternType.ESCAPED, 5),
            MsgSplicingTableNewStaffPatternS2C,
            level,
        )
        assertPacketRoundTrip(MsgSplicingTableSelectIndexC2S(7, true, false), MsgSplicingTableSelectIndexC2S, level)

        val config = HexDebugServerConfig.ServerConfig()
        assertPacketRoundTrip(MsgSyncConfigS2C(config), MsgSyncConfigS2C, level) { original, decoded ->
            val a = encodeConfig(original.serverConfig)
            val b = encodeConfig(decoded.serverConfig)
            require(a.contentEquals(b)) { "config packet mismatch: ${a.contentToString()} != ${b.contentToString()}" }
        }
    }

    private fun encodeConfig(config: HexDebugServerConfig.ServerConfig): ByteArray {
        val buf = FriendlyByteBuf(Unpooled.buffer())
        return try {
            config.encode(buf)
            val result = ByteArray(buf.readableBytes())
            buf.readBytes(result)
            result
        } finally {
            buf.release()
        }
    }

    private fun <T : HexDebugMessage> assertPacketRoundTrip(
        message: T,
        companion: HexDebugMessageCompanion<T>,
        level: ServerLevel,
        assertion: (T, T) -> Unit = { original, decoded -> require(original == decoded) { "$original != $decoded" } },
    ) {
        val decoded = packetRoundTrip(message, companion, level)
        assertion(message, decoded)
    }

    private fun <T : HexDebugMessage> assertPacketClassRoundTrip(
        message: T,
        companion: HexDebugMessageCompanion<T>,
        level: ServerLevel,
    ) {
        val decoded = packetRoundTrip(message, companion, level)
        require(decoded.javaClass == message.javaClass) { "packet class mismatch: ${message.javaClass} != ${decoded.javaClass}" }
    }

    private fun <T : HexDebugMessage> packetRoundTrip(
        message: T,
        companion: HexDebugMessageCompanion<T>,
        level: ServerLevel,
    ): T {
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess())
        return try {
            with(companion) { message.encode(buf) }
            companion.decode(buf)
        } finally {
            buf.release()
        }
    }

    private fun checkFocusHolderItem() {
        val nested = ItemStack(HexItems.FOCUS.get()).also {
            it.set(DataComponents.CUSTOM_NAME, Component.literal("nested-focus"))
            seedIotaComponent(it, ListIota(listOf(DoubleIota(3.25), CognitohazardIota())))
        }
        val holder = ItemStack(HexDebugBlocks.FOCUS_HOLDER.item).setIotaStack(nested)
        val (decodedStack, decodedHolder) = holder.getIotaStack()
        require(decodedStack.`is`(HexItems.FOCUS.get())) { "nested item type was not preserved: $decodedStack" }
        require(decodedStack.get(DataComponents.CUSTOM_NAME)?.string == "nested-focus") { "nested components were not preserved" }
        val decodedIota = decodedHolder?.readIota(decodedStack) as? ListIota
        require(decodedIota?.list?.size == 2) { "nested iota was not preserved: $decodedIota" }
    }

    private fun checkBlockEntityPersistence(level: ServerLevel) {
        val nested = ItemStack(HexItems.FOCUS.get()).also {
            it.set(DataComponents.CUSTOM_NAME, Component.literal("be-focus"))
            seedIotaComponent(it, Vec3Iota(Vec3(8.0, 9.0, 10.0)))
        }
        val focusState = HexDebugBlocks.FOCUS_HOLDER.block.defaultBlockState()
        val focus = FocusHolderBlockEntity(BlockPos.ZERO, focusState)
        focus.iotaStack = nested
        val focusTag = focus.saveWithFullMetadata(level.registryAccess())
        val loadedFocus = BlockEntity.loadStatic(BlockPos.ZERO, focusState, focusTag, level.registryAccess()) as? FocusHolderBlockEntity
            ?: error("focus holder block entity did not load")
        require(loadedFocus.iotaStack.get(DataComponents.CUSTOM_NAME)?.string == "be-focus") {
            "focus holder nested stack did not survive BE round trip"
        }

        val tableState = HexDebugBlocks.ENLIGHTENED_SPLICING_TABLE.block.defaultBlockState()
        val table = SplicingTableBlockEntity(BlockPos(1, 2, 3), tableState)
        table.setLevel(level)
        table.listStack = ItemStack(HexItems.FOCUS.get()).also {
            seedIotaComponent(it, ListIota(listOf(Vec3Iota(Vec3(8.0, 9.0, 10.0)))))
        }
        table.clipboardStack = ItemStack(HexItems.FOCUS.get()).also { seedIotaComponent(it, DoubleIota(6.5)) }
        table.media = 1_234L
        table.writeSelection(Selection.range(0, 0))
        table.writeViewStartIndex(0)
        table.setCustomName(Component.literal("probe-table"))
        table.setHex(listOf(DoubleIota(1.5), CognitohazardIota()))
        val tableTag = table.saveWithFullMetadata(level.registryAccess())
        val loadedTable = BlockEntity.loadStatic(table.blockPos, tableState, tableTag, level.registryAccess()) as? SplicingTableBlockEntity
            ?: error("splicing table block entity did not load")
        loadedTable.setLevel(level)
        require(loadedTable.media == 1_234L) { "media mismatch: ${loadedTable.media}" }
        require(loadedTable.selection == Selection.range(0, 0)) { "selection mismatch: ${loadedTable.selection}" }
        require(loadedTable.customName?.string == "probe-table") { "custom name mismatch: ${loadedTable.customName}" }
        val hex = loadedTable.getHex(level)
        require(hex?.size == 2 && hex[1] is CognitohazardIota) { "stored hex mismatch: $hex" }
        require(loadedTable.getClientView()?.list?.size == 1) { "client view was not reconstructed" }
    }

    private fun checkMenu(level: ServerLevel) {
        val fakePlayer = FakePlayerFactory.getMinecraft(level)
        val state = HexDebugBlocks.SPLICING_TABLE.block.defaultBlockState()
        val table = SplicingTableBlockEntity(BlockPos(4, 5, 6), state)
        table.setLevel(level)
        val menu = table.createMenu(17, fakePlayer.inventory, fakePlayer)
        require(menu.slots.size == 46) { "expected 46 slots, found ${menu.slots.size}" }
        require(menu.containerId == 17) { "container id mismatch" }
    }

    private fun checkDebuggerEnchantments(level: ServerLevel) {
        val fakePlayer = FakePlayerFactory.getMinecraft(level)
        val bane = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.BANE_OF_ARTHROPODS)

        val debugger = ItemStack(HexDebugItems.DEBUGGER.value)
        debugger.item.onCraftedBy(debugger, level, fakePlayer)
        val debuggerLevel = EnchantmentHelper.getItemEnchantmentLevel(bane, debugger)
        require(debuggerLevel >= 1) { "debugger lost upstream Bane of Arthropods level: $debuggerLevel" }

        val quenched = ItemStack(HexDebugItems.QUENCHED_DEBUGGER.value)
        quenched.item.onCraftedBy(quenched, level, fakePlayer)
        val quenchedLevel = EnchantmentHelper.getItemEnchantmentLevel(bane, quenched)
        require(quenchedLevel >= 2) { "quenched debugger lost upstream Bane of Arthropods level: $quenchedLevel" }
    }

    private fun checkCustomRecipes(event: ServerStartedEvent, level: ServerLevel) {
        val focusRecipe = event.server.recipeManager
            .byKey(HexDebug.id("focus_holder_filling_shaped/focus"))
            .orElseThrow()
            .value()
            .let { it as? FocusHolderFillingShapedRecipe ?: error("wrong focus-holder recipe class: ${it.javaClass.name}") }
        val emptyHolder = ItemStack(HexDebugBlocks.FOCUS_HOLDER.item)
        val focusInput = CraftingInput.of(3, 3, listOf(
            emptyHolder,
            ItemStack(Items.LEATHER),
            ItemStack.EMPTY,
            ItemStack(Items.PAPER),
            ItemStack(HexItems.CHARGED_AMETHYST.get()),
            ItemStack(Items.PAPER),
            ItemStack.EMPTY,
            ItemStack(Items.LEATHER),
            ItemStack(Items.GLOWSTONE),
        ))
        require(focusRecipe.matches(focusInput, level)) { "focus-holder filling recipe did not match its generated pattern" }
        val filledHolder = focusRecipe.assemble(focusInput, level.registryAccess())
        val (innerStack, _) = filledHolder.getIotaStack()
        require(filledHolder.`is`(HexDebugBlocks.FOCUS_HOLDER.item) && innerStack.`is`(HexItems.FOCUS.get())) {
            "focus-holder filling recipe lost its nested focus: $filledHolder / $innerStack"
        }

        val alreadyFilledInput = CraftingInput.of(3, 3, focusInput.items().toMutableList().also {
            it[0] = ItemStack(HexDebugBlocks.FOCUS_HOLDER.item).setIotaStack(ItemStack(HexItems.FOCUS.get()))
        })
        require(!focusRecipe.matches(alreadyFilledInput, level)) { "recipe accepted an already-filled focus holder" }

        val quenchingRecipe = event.server.recipeManager
            .byKey(HexDebug.id("quenched_debugger"))
            .orElseThrow()
            .value()
            .let { it as? FlyswatterQuenchingShapedRecipe ?: error("wrong quenching recipe class: ${it.javaClass.name}") }
        val original = ItemStack(HexDebugItems.DEBUGGER.value).also {
            it.set(DataComponents.CUSTOM_NAME, Component.literal("recipe-component-probe"))
        }
        val quenchingInput = CraftingInput.of(3, 3, listOf(
            ItemStack.EMPTY,
            ItemStack(HexItems.QUENCHED_SHARD.get()),
            ItemStack.EMPTY,
            ItemStack(HexItems.QUENCHED_SHARD.get()),
            original,
            ItemStack(HexItems.QUENCHED_SHARD.get()),
            ItemStack.EMPTY,
            ItemStack(HexItems.QUENCHED_SHARD.get()),
            ItemStack.EMPTY,
        ))
        require(quenchingRecipe.matches(quenchingInput, level)) { "quenched debugger recipe did not match its generated pattern" }
        val quenched = quenchingRecipe.assemble(quenchingInput, level.registryAccess())
        require(quenched.`is`(HexDebugItems.QUENCHED_DEBUGGER.value)) { "wrong quenching output: $quenched" }
        require(quenched.get(DataComponents.CUSTOM_NAME)?.string == "recipe-component-probe") {
            "quenching recipe discarded the debugger component patch"
        }
    }

    private fun checkSplicingTableActions(level: ServerLevel) {
        // NeoForge's IDE-only component validator reflectively inspects every public method
        // on Hex Casting Iotas. That reflection attempts to resolve client-only tooltip method
        // signatures on a dedicated-server userdev run. Production sets this flag to false, so
        // temporarily mirror production while exercising the real ItemFocus write path.
        val wasRunningInIde = SharedConstants.IS_RUNNING_IN_IDE
        SharedConstants.IS_RUNNING_IN_IDE = false
        try {
            checkSplicingTableActionsProductionPath(level)
        } finally {
            SharedConstants.IS_RUNNING_IN_IDE = wasRunningInIde
        }
    }

    private fun checkSplicingTableActionsProductionPath(level: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(level)

        val copyTable = newActionTable(level, listOf(DoubleIota(1.0), DoubleIota(2.0), DoubleIota(3.0)))
        copyTable.writeSelection(Selection.range(0, 1))
        val mediaBeforeCopy = copyTable.media
        copyTable.runAction(SplicingTableAction.COPY, player)
        val copied = copyTable.clipboardHolder?.readIota() as? ListIota
        require(copied?.list?.map { (it as DoubleIota).double } == listOf(1.0, 2.0)) {
            "copy produced the wrong clipboard value: $copied"
        }
        require(copyTable.media < mediaBeforeCopy) { "copy did not consume media" }

        val editTable = newActionTable(level, listOf(DoubleIota(4.0), DoubleIota(5.0), DoubleIota(6.0)))
        editTable.writeSelection(Selection.range(1, 1))
        editTable.runAction(SplicingTableAction.DELETE, player)
        require(readDoubleList(editTable, level) == listOf(4.0, 6.0)) { "delete did not update the list" }
        editTable.runAction(SplicingTableAction.UNDO, player)
        require(readDoubleList(editTable, level) == listOf(4.0, 5.0, 6.0)) { "undo did not restore the list" }
        editTable.runAction(SplicingTableAction.REDO, player)
        require(readDoubleList(editTable, level) == listOf(4.0, 6.0)) { "redo did not reapply the deletion" }

        editTable.writeSelection(Selection.edge(1))
        val pattern = HexPattern.fromAngles("aqw", HexDir.EAST)
        require(editTable.drawPattern(player, pattern, 0) == ResolvedPatternType.ESCAPED) {
            "drawing a valid pattern failed"
        }
        val edited = editTable.getList(level)?.toList() ?: emptyList()
        require(edited.size == 3 && edited[1] is PatternIota) {
            "drawn pattern was not inserted at the selected edge: $edited"
        }
    }

    private fun newActionTable(level: ServerLevel, values: List<Iota>): SplicingTableBlockEntity {
        return SplicingTableBlockEntity(BlockPos(8, 9, 10), HexDebugBlocks.SPLICING_TABLE.block.defaultBlockState()).also { table ->
            table.setLevel(level)
            table.listStack = ItemStack(HexItems.FOCUS.get()).also { seedIotaComponent(it, ListIota(values)) }
            table.clipboardStack = ItemStack(HexItems.FOCUS.get()).also { seedIotaComponent(it, DoubleIota(-1.0)) }
            table.media = HexDebugServerConfig.config.splicingTableMaxMedia
        }
    }

    private fun readDoubleList(table: SplicingTableBlockEntity, level: ServerLevel): List<Double> =
        (table.getList(level)?.toList() ?: emptyList()).map { (it as DoubleIota).double }

    private fun checkDebuggerLifecycle(level: ServerLevel) {
        val player = FakePlayerFactory.getMinecraft(level)
        invokeDebugAdapterManager("add", player)
        try {
            val adapter = DebugAdapterManager[player] ?: error("player join did not create a debug adapter")
            require(HexDebugCoreAPI.INSTANCE.javaClass.name == "gay.object.hexdebug.impl.HexDebugCoreAPIImpl") {
                "HexDebug Core service loader returned ${HexDebugCoreAPI.INSTANCE.javaClass.name}"
            }
            require(HexDebugCoreAPI.INSTANCE.getFreeDebugThreadId(player) == 0) { "first free thread was not zero" }

            val stack = ItemStack(HexDebugItems.DEBUGGER.value)
            val item = stack.item as DebuggerItem
            item.writeHex(stack, listOf(DoubleIota(13.0)), FrozenPigment.DEFAULT.get(), 0L)
            player.setItemInHand(InteractionHand.MAIN_HAND, stack)

            val result = item.use(level, player, InteractionHand.MAIN_HAND)
            require(result.result.consumesAction()) { "using a populated debugger did not consume the action" }
            require(adapter.debugger(0) != null) { "debugger use did not create thread 0" }
            val debugEnv = HexDebugCoreAPI.INSTANCE.getDebugEnv(player, 0)
                ?: error("core API could not resolve the active debug environment")
            require(HexDebugCoreAPI.INSTANCE.isSessionDebugging(debugEnv)) { "core API did not report the active session" }

            HexDebugCoreAPI.INSTANCE.terminateDebugThread(debugEnv)
            require(adapter.debugger(0) == null) { "terminating through the core API leaked thread 0" }
            require(HexDebugCoreAPI.INSTANCE.getFreeDebugThreadId(player) == 0) { "thread 0 was not released" }
        } finally {
            invokeDebugAdapterManager("remove", player)
        }
    }

    private fun invokeDebugAdapterManager(name: String, player: net.minecraft.server.level.ServerPlayer) {
        DebugAdapterManager::class.java.getDeclaredMethod(name, net.minecraft.server.level.ServerPlayer::class.java).also {
            it.isAccessible = true
            it.invoke(DebugAdapterManager, player)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedIotaComponent(stack: ItemStack, iota: Iota) {
        // NeoForge userdev validates inherited client-only Iota method signatures on ItemStack.set.
        // Production does not run that IDE-only validator, so seed the component directly here.
        val componentsField = ItemStack::class.java.getDeclaredField("components").apply { isAccessible = true }
        val components = componentsField.get(stack)
        val patchField = components.javaClass.getDeclaredField("patch").apply { isAccessible = true }
        val copyOnWriteField = components.javaClass.getDeclaredField("copyOnWrite").apply { isAccessible = true }
        val patch = Reference2ObjectArrayMap<DataComponentType<*>, Optional<Any>>()
        (patchField.get(components) as? Reference2ObjectMap<DataComponentType<*>, Optional<Any>>)?.let(patch::putAll)
        patch[HexDataComponents.IOTA_HOLDER_IOTA.get()] = Optional.of(iota)
        patchField.set(components, patch)
        copyOnWriteField.setBoolean(components, false)
    }

    private fun scheduleHardExit(exitCode: Int) {
        Thread({
            try {
                Thread.sleep(15_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            Runtime.getRuntime().halt(exitCode)
        }, "hexdebug-runtime-probe-hard-stop").apply {
            isDaemon = true
            start()
        }
    }
}
