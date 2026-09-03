package gay.`object`.hexdebug.forge

import gay.`object`.hexdebug.HexDebug
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.fml.loading.FMLEnvironment

/**
 * This is your loading entrypoint on forge, in case you need to initialize
 * something platform-specific.
 */
@Mod(HexDebug.MODID)
class HexDebugForge(bus: IEventBus) {
    init {
        initPlatformBus(bus)
        bus.apply {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                addListener(ForgeHexDebugClient::init)
                addListener(ForgeHexDebugClient::registerClientReloadListeners)
            }
            addListener(::initServer)
        }
        HexDebug.init()
        registerDevelopmentProbe(
            "hexdebug.probe.validateTags",
            "gay.object.hexdebug.forge.probe.HexDebugTagProbe",
        )
        registerDevelopmentProbe(
            "hexdebug.probe.validateRuntime",
            "gay.object.hexdebug.forge.probe.HexDebugRuntimeProbe",
        )
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerDevelopmentProbe(
                "hexdebug.probe.validateClient",
                "gay.object.hexdebug.forge.probe.HexDebugClientProbe",
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun initServer(event: FMLDedicatedServerSetupEvent) {
        HexDebug.initServer()
    }

    /**
     * Keeps regression probes on the Loom development classpath without
     * linking or shipping them in the production mod JAR.
     */
    private fun registerDevelopmentProbe(property: String, className: String) {
        if (FMLEnvironment.production || !java.lang.Boolean.getBoolean(property)) {
            return
        }
        try {
            Class.forName(className).getMethod("register").invoke(null)
        } catch (exception: ReflectiveOperationException) {
            throw IllegalStateException("Unable to register development probe $className", exception)
        }
    }
}
