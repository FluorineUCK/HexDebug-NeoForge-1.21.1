package gay.object.hexdebug.forge.probe;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.common.lib.hex.HexActions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gay.object.hexdebug.HexDebug;
import gay.object.hexdebug.api.client.splicing.SplicingTableIotaRenderers;
import gay.object.hexdebug.items.DebuggerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Development-only client resource, model, screen, and Patchouli regression probe. */
public final class HexDebugClientProbe {
    private static final ResourceLocation BOOK_ID = id("hexcasting", "thehexbook");
    private static final Set<ResourceLocation> EXPECTED_ITEM_IDS = Set.of(
            HexDebug.id("debugger"),
            HexDebug.id("quenched_debugger"),
            HexDebug.id("evaluator"),
            HexDebug.id("quenched_evaluator"),
            HexDebug.id("splicing_table"),
            HexDebug.id("enlightened_splicing_table"),
            HexDebug.id("focus_holder")
    );
    private static final int BASE_CHECK_TICK = 160;
    private static final int WORLD_TIMEOUT_TICK =
            Integer.getInteger("hexdebug.probe.worldTimeoutTicks", 1_200);
    private static final List<String> FAILURES = new ArrayList<>();

    private static int ticks;
    private static boolean registered;
    private static boolean baseChecksFinished;
    private static boolean finished;

    private HexDebugClientProbe() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            NeoForge.EVENT_BUS.addListener(HexDebugClientProbe::onClientTick);
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (finished) {
            return;
        }
        ticks++;

        if (!baseChecksFinished && ticks >= BASE_CHECK_TICK) {
            baseChecksFinished = true;
            runCheck("translations", HexDebugClientProbe::checkTranslations);
            runCheck("creative_tab", HexDebugClientProbe::checkCreativeTab);
            runCheck("resource_inventory", HexDebugClientProbe::checkResourceInventory);
            runCheck("baked_models", HexDebugClientProbe::checkBakedModels);
            runCheck("model_predicates", HexDebugClientProbe::checkModelPredicates);
            runCheck("screen_registration", HexDebugClientProbe::checkScreenRegistration);
            runCheck("splicing_renderers", HexDebugClientProbe::checkSplicingRenderers);
        }

        if (baseChecksFinished && Minecraft.getInstance().level != null) {
            runCheck("patchouli_book", HexDebugClientProbe::checkPatchouliBook);
            finish();
        } else if (ticks >= WORLD_TIMEOUT_TICK) {
            FAILURES.add("client_world");
            HexDebug.LOGGER.error(
                    "[HEXDEBUG-CLIENT-PROBE] client_world=FAIL no integrated world after {} ticks screen={}",
                    ticks,
                    Minecraft.getInstance().screen == null
                            ? "null"
                            : Minecraft.getInstance().screen.getClass().getName()
            );
            finish();
        }
    }

    private static void finish() {
        finished = true;
        if (FAILURES.isEmpty()) {
            HexDebug.LOGGER.info("[HEXDEBUG-CLIENT-PROBE] aggregate=PASS");
        } else {
            HexDebug.LOGGER.error(
                    "[HEXDEBUG-CLIENT-PROBE] aggregate=FAIL failure_count={} failures={}",
                    FAILURES.size(),
                    String.join(",", FAILURES)
            );
        }
        if (Boolean.getBoolean("hexdebug.probe.exitAfterClientStartup")) {
            if (!FAILURES.isEmpty()) {
                // Minecraft's normal shutdown path exits with status 0 before
                // a delayed fallback thread can replace the probe result.
                Runtime.getRuntime().halt(1);
            }
            scheduleHardExit(0);
            Minecraft.getInstance().stop();
        }
    }

    private static void scheduleHardExit(int exitCode) {
        Thread hardStop = new Thread(() -> {
            try {
                Thread.sleep(15_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(exitCode);
        }, "hexdebug-client-probe-hard-stop");
        hardStop.setDaemon(true);
        hardStop.start();
    }

    private static void runCheck(String name, ProbeCheck check) {
        try {
            String details = check.run();
            HexDebug.LOGGER.info("[HEXDEBUG-CLIENT-PROBE] {}=PASS {}", name, details);
        } catch (Throwable throwable) {
            FAILURES.add(name);
            HexDebug.LOGGER.error("[HEXDEBUG-CLIENT-PROBE] {}=FAIL", name, throwable);
        }
    }

    private static String checkTranslations() throws Exception {
        JsonObject english = readJson(id("hexdebug", "lang/en_us.json"));
        JsonObject chinese = readJson(id("hexdebug", "lang/zh_cn.json"));
        Set<String> englishKeys = english.keySet();
        Set<String> chineseKeys = chinese.keySet();
        check(englishKeys.equals(chineseKeys), () -> {
            Set<String> onlyEnglish = new LinkedHashSet<>(englishKeys);
            onlyEnglish.removeAll(chineseKeys);
            Set<String> onlyChinese = new LinkedHashSet<>(chineseKeys);
            onlyChinese.removeAll(englishKeys);
            return "language key mismatch only_en=" + onlyEnglish + " only_zh=" + onlyChinese;
        });

        List<String> blank = englishKeys.stream()
                .filter(key -> isBlank(english.get(key)) || isBlank(chinese.get(key)))
                .toList();
        check(blank.isEmpty(), () -> "blank translations " + blank);
        List<String> inactive = englishKeys.stream().filter(key -> !I18n.exists(key)).toList();
        check(inactive.isEmpty(), () -> "keys absent from active language map " + inactive);
        return "keys=" + englishKeys.size() + " en_zh_parity=PASS";
    }

    private static String checkResourceInventory() throws Exception {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> models = manager.listResources(
                "models",
                resource -> resource.getNamespace().equals("hexdebug") && resource.getPath().endsWith(".json")
        );
        Map<ResourceLocation, Resource> blockstates = manager.listResources(
                "blockstates",
                resource -> resource.getNamespace().equals("hexdebug") && resource.getPath().endsWith(".json")
        );
        Map<ResourceLocation, Resource> textures = manager.listResources(
                "textures",
                resource -> resource.getNamespace().equals("hexdebug") && resource.getPath().endsWith(".png")
        );
        check(models.size() == 64, () -> "expected 64 models, found " + models.size());
        check(blockstates.size() == 3, () -> "expected 3 blockstates, found " + blockstates.size());
        check(textures.size() == 33, () -> "expected 33 textures, found " + textures.size());

        List<String> missingReferences = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : models.entrySet()) {
            JsonObject model;
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                model = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (model.has("parent")) {
                checkModelReference(manager, entry.getKey(), model.get("parent").getAsString(), missingReferences);
            }
            if (model.has("overrides")) {
                for (JsonElement override : model.getAsJsonArray("overrides")) {
                    JsonObject object = override.getAsJsonObject();
                    if (object.has("model")) {
                        checkModelReference(manager, entry.getKey(), object.get("model").getAsString(), missingReferences);
                    }
                }
            }
            if (model.has("textures")) {
                for (JsonElement texture : model.getAsJsonObject("textures").asMap().values()) {
                    String value = texture.getAsString();
                    if (value.startsWith("#")) {
                        continue;
                    }
                    ResourceLocation textureId = ResourceLocation.parse(value);
                    ResourceLocation resourceId = id(
                            textureId.getNamespace(),
                            "textures/" + textureId.getPath() + ".png"
                    );
                    if (manager.getResource(resourceId).isEmpty()) {
                        missingReferences.add(entry.getKey() + " -> " + resourceId);
                    }
                }
            }
        }
        check(missingReferences.isEmpty(), () -> "missing model/texture references " + missingReferences);
        return "models=64 blockstates=3 textures=33 references=PASS";
    }

    private static String checkCreativeTab() {
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(HexDebug.id("hexdebug"));
        check(tab != null, () -> "HexDebug creative tab is not registered");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS,
                true,
                RegistryAccess.EMPTY
        ));
        Set<ResourceLocation> actual = new LinkedHashSet<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId.getNamespace().equals(HexDebug.MODID)) {
                actual.add(itemId);
            }
        }
        Set<ResourceLocation> missing = new LinkedHashSet<>(EXPECTED_ITEM_IDS);
        missing.removeAll(actual);
        Set<ResourceLocation> unexpected = new LinkedHashSet<>(actual);
        unexpected.removeAll(EXPECTED_ITEM_IDS);
        check(missing.isEmpty() && unexpected.isEmpty(), () ->
                "creative tab mismatch missing=" + missing + " unexpected=" + unexpected);
        return "items=" + actual.size() + " parity=PASS";
    }

    private static void checkModelReference(
            ResourceManager manager,
            ResourceLocation source,
            String value,
            List<String> missing
    ) {
        ResourceLocation modelId = ResourceLocation.parse(value);
        ResourceLocation resourceId = id(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
        if (manager.getResource(resourceId).isEmpty()) {
            missing.add(source + " -> " + resourceId);
        }
    }

    private static String checkBakedModels() {
        Minecraft client = Minecraft.getInstance();
        var missing = client.getModelManager().getMissingModel();
        List<ResourceLocation> missingItems = new ArrayList<>();
        for (ResourceLocation itemId : BuiltInRegistries.ITEM.keySet()) {
            if (!itemId.getNamespace().equals("hexdebug")) {
                continue;
            }
            if (client.getModelManager().getModel(ModelResourceLocation.inventory(itemId)) == missing) {
                missingItems.add(itemId);
            }
        }
        check(missingItems.isEmpty(), () -> "missing item models " + missingItems);

        List<String> missingStates = new ArrayList<>();
        int stateCount = 0;
        for (ResourceLocation blockId : BuiltInRegistries.BLOCK.keySet()) {
            if (!blockId.getNamespace().equals("hexdebug")) {
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            for (var state : block.getStateDefinition().getPossibleStates()) {
                stateCount++;
                if (client.getModelManager().getBlockModelShaper().getBlockModel(state) == missing) {
                    missingStates.add(blockId + " " + state.getValues());
                }
            }
        }
        check(missingStates.isEmpty(), () -> "missing block-state models " + missingStates);
        return "items=7 block_states=" + stateCount;
    }

    private static String checkModelPredicates() {
        Minecraft client = Minecraft.getInstance();
        List<String> unchanged = new ArrayList<>();
        for (String path : List.of("debugger", "quenched_debugger")) {
            Item item = BuiltInRegistries.ITEM.get(HexDebug.id(path));
            ItemStack empty = new ItemStack(item);
            ItemStack full = new ItemStack(item);
            ((DebuggerItem) item).writeHex(
                    full,
                    List.of(new DoubleIota(1.0)),
                    FrozenPigment.DEFAULT.get(),
                    0L
            );
            var emptyModel = client.getItemRenderer().getModel(empty, client.level, client.player, 0);
            var fullModel = client.getItemRenderer().getModel(full, client.level, client.player, 0);
            if (emptyModel == client.getModelManager().getMissingModel()
                    || fullModel == client.getModelManager().getMissingModel()
                    || emptyModel == fullModel) {
                unchanged.add(path);
            }
        }
        check(unchanged.isEmpty(), () -> "has_hex predicate did not select distinct baked models " + unchanged);
        return "debugger_has_hex=PASS quenched_debugger_has_hex=PASS";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String checkScreenRegistration() {
        var menu = BuiltInRegistries.MENU.get(HexDebug.id("splicing_table"));
        check(MenuScreens.getScreenFactory(menu).isPresent(), () -> "splicing-table screen factory is absent");
        return "splicing_table=PASS";
    }

    private static String checkSplicingRenderers() {
        check(SplicingTableIotaRenderers.getProvider(null) != null, () -> "generic fallback provider is absent");
        List<String> missingParsers = List.of(
                "conditional/if_path_exists", "item", "layers", "list", "pattern", "sub_iota", "texture"
        ).stream().filter(path -> SplicingTableIotaRenderers.getParser(HexDebug.id(path)) == null).toList();
        check(missingParsers.isEmpty(), () -> "missing renderer parsers " + missingParsers);

        List<String> missingProviders = new ArrayList<>();
        for (String path : List.of(
                "boolean", "continuation", "double", "entity", "garbage", "list", "null", "pattern", "vec3"
        )) {
            ResourceLocation typeId = id("hexcasting", path);
            IotaType<?> type = HexIotaTypes.REGISTRY.get(typeId);
            if (type == null || SplicingTableIotaRenderers.getProvider(type, false) == null) {
                missingProviders.add(path);
            }
        }
        check(missingProviders.isEmpty(), () -> "missing exact renderer providers " + missingProviders);
        return "parsers=7 providers=9 fallback=PASS";
    }

    private static String checkPatchouliBook() {
        Book book = BookRegistry.INSTANCE.books.get(BOOK_ID);
        check(book != null, () -> "book not loaded " + BOOK_ID);
        BookContents contents = book.getContents();
        check(contents != null, () -> "book contents are null " + BOOK_ID);
        if (contents.isErrored()) {
            throw new IllegalStateException("Patchouli failed to build " + BOOK_ID, contents.getException());
        }

        Set<ResourceLocation> expected = Set.of(
                bookId("greatwork/enlightened_splicing_table"),
                bookId("greatwork/quenching_debuggers"),
                bookId("items/debugging"),
                bookId("items/focus_holder"),
                bookId("items/splicing_table"),
                bookId("patterns/debugging"),
                bookId("patterns/enlightened_splicing_table"),
                bookId("patterns/splicing_table")
        );
        Set<ResourceLocation> missing = new LinkedHashSet<>(expected);
        missing.removeAll(contents.entries.keySet());
        check(missing.isEmpty(), () -> "missing Patchouli entries " + missing);
        List<ResourceLocation> empty = expected.stream()
                .filter(entry -> contents.entries.get(entry).getPages().isEmpty())
                .toList();
        check(empty.isEmpty(), () -> "Patchouli entries without pages " + empty);

        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> entryResources = manager.listResources(
                "patchouli_books/thehexbook/en_us/entries",
                resource -> resource.getNamespace().equals("hexcasting")
                        && resource.getPath().endsWith(".json")
        );
        Set<ResourceLocation> expectedResources = expected.stream()
                .map(entry -> id(
                        entry.getNamespace(),
                        "patchouli_books/thehexbook/en_us/entries/" + entry.getPath() + ".json"
                ))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<ResourceLocation> documentedActions = new LinkedHashSet<>();
        Set<String> documentedStrings = new LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, Resource> entry : entryResources.entrySet()) {
            if (!expectedResources.contains(entry.getKey())) {
                continue;
            }
            Resource resource = entry.getValue();
            JsonElement root;
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to read active HexDebug Patchouli entry", exception);
            }
            collectDocumentation(root, documentedActions, documentedStrings);
        }
        Set<ResourceLocation> missingResources = new LinkedHashSet<>(expectedResources);
        missingResources.removeAll(entryResources.keySet());
        check(missingResources.isEmpty(), () -> "Patchouli resources missing " + missingResources);

        Set<ResourceLocation> registeredActions = HexActions.REGISTRY.keySet().stream()
                .filter(id -> id.getNamespace().equals(HexDebug.MODID))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        check(registeredActions.size() == 22, () ->
                "expected 22 registered HexDebug actions, found " + registeredActions.size());
        Set<ResourceLocation> missingActions = new LinkedHashSet<>(registeredActions);
        missingActions.removeAll(documentedActions);
        Set<ResourceLocation> unknownActions = new LinkedHashSet<>(documentedActions);
        unknownActions.removeAll(registeredActions);
        check(missingActions.isEmpty() && unknownActions.isEmpty(), () ->
                "Patchouli action mismatch missing=" + missingActions + " unknown=" + unknownActions);

        Set<ResourceLocation> missingItems = EXPECTED_ITEM_IDS.stream()
                .filter(id -> !documentedStrings.contains(id.toString()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        check(missingItems.isEmpty(), () -> "Patchouli item references missing " + missingItems);
        return "entries=8 actions=" + documentedActions.size()
                + " items=" + EXPECTED_ITEM_IDS.size() + " errored=false";
    }

    private static void collectDocumentation(
            JsonElement element,
            Set<ResourceLocation> actions,
            Set<String> strings
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isString()) {
                strings.add(element.getAsString());
            }
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectDocumentation(child, actions, strings));
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("op_id") && object.get("op_id").isJsonPrimitive()) {
            actions.add(ResourceLocation.parse(object.get("op_id").getAsString()));
        }
        object.asMap().values().forEach(child -> collectDocumentation(child, actions, strings));
    }

    private static JsonObject readJson(ResourceLocation id) throws Exception {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow();
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean isBlank(JsonElement value) {
        return value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank();
    }

    private static ResourceLocation bookId(String path) {
        return id("hexcasting", path);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static void check(boolean condition, Message message) {
        if (!condition) {
            throw new IllegalStateException(message.get());
        }
    }

    @FunctionalInterface
    private interface ProbeCheck {
        String run() throws Exception;
    }

    @FunctionalInterface
    private interface Message {
        String get();
    }
}
