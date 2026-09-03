@file:JvmName("HexDebugAbstractionsImpl")

package gay.`object`.hexdebug.forge

import gay.`object`.hexdebug.registry.HexDebugRegistrar
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

private lateinit var modBus: IEventBus

fun initPlatformBus(bus: IEventBus) {
    modBus = bus
}

fun <T : Any> initRegistry(registrar: HexDebugRegistrar<T>) {
    modBus.addListener { event: RegisterEvent ->
        event.register(registrar.registryKey) { helper ->
            registrar.init(helper::register)
        }
    }
}

