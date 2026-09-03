package gay.object.hexdebug.forge.probe;

import at.petrak.hexcasting.api.mod.HexTags;
import gay.object.hexdebug.HexDebug;
import gay.object.hexdebug.api.HexDebugTags;
import gay.object.hexdebug.registry.HexDebugBlocks;
import gay.object.hexdebug.registry.HexDebugItems;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Development-only regression probe for the Minecraft 1.21 singular item and
 * block tag resource directories.
 */
public final class HexDebugTagProbe {
    private HexDebugTagProbe() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(HexDebugTagProbe::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        int exitCode = 0;
        try {
            require(
                    HexDebugItems.EVALUATOR.getValue().getDefaultInstance().is(HexTags.Items.STAVES),
                    "hexdebug:evaluator missing from hexcasting:staves");
            require(
                    HexDebugItems.QUENCHED_EVALUATOR.getValue().getDefaultInstance().is(HexTags.Items.STAVES),
                    "hexdebug:quenched_evaluator missing from hexcasting:staves");
            HexDebug.LOGGER.info("[HEXDEBUG-TAG-PROBE] staves=PASS");

            require(
                    HexDebugBlocks.FOCUS_HOLDER.getItem().getDefaultInstance()
                            .is(HexDebugTags.Items.FOCUS_HOLDER_BLACKLIST),
                    "hexdebug:focus_holder missing from hexdebug:focus_holder/blacklist");
            HexDebug.LOGGER.info("[HEXDEBUG-TAG-PROBE] focus_holder_blacklist=PASS");

            require(
                    HexDebugBlocks.SPLICING_TABLE.getBlock().defaultBlockState()
                            .is(BlockTags.MINEABLE_WITH_PICKAXE),
                    "hexdebug:splicing_table missing from minecraft:mineable/pickaxe");
            require(
                    HexDebugBlocks.ENLIGHTENED_SPLICING_TABLE.getBlock().defaultBlockState()
                            .is(BlockTags.MINEABLE_WITH_PICKAXE),
                    "hexdebug:enlightened_splicing_table missing from minecraft:mineable/pickaxe");
            require(
                    HexDebugBlocks.FOCUS_HOLDER.getBlock().defaultBlockState()
                            .is(BlockTags.MINEABLE_WITH_PICKAXE),
                    "hexdebug:focus_holder missing from minecraft:mineable/pickaxe");
            HexDebug.LOGGER.info("[HEXDEBUG-TAG-PROBE] pickaxe_mineable=PASS");
            HexDebug.LOGGER.info("[HEXDEBUG-TAG-PROBE] aggregate=PASS");
        } catch (Throwable throwable) {
            exitCode = 1;
            HexDebug.LOGGER.error("[HEXDEBUG-TAG-PROBE] aggregate=FAIL", throwable);
        } finally {
            scheduleHardExit(exitCode);
            event.getServer().halt(false);
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
        }, "hexdebug-tag-probe-hard-stop");
        hardStop.setDaemon(true);
        hardStop.start();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
