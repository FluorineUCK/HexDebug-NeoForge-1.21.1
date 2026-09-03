package gay.`object`.hexdebug.forge

import gay.`object`.hexdebug.HexDebugClient
import gay.`object`.hexdebug.resources.splicing.SplicingTableIotasResourceReloadListener
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

object ForgeHexDebugClient {
    @Suppress("UNUSED_PARAMETER")
    fun init(event: FMLClientSetupEvent) {
        HexDebugClient.init()
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent -> HexDebugClient.getConfigScreen(parent) }
        }
    }

    fun registerClientReloadListeners(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener(SplicingTableIotasResourceReloadListener)
    }
}
