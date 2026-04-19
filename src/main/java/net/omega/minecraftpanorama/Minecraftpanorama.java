package net.omega.minecraftpanorama;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(Minecraftpanorama.MODID)
public class Minecraftpanorama {
    public static final String MODID = "minecraftpanorama";
    private static final Logger LOGGER = LogUtils.getLogger();


    public Minecraftpanorama (IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void commonSetup (final FMLCommonSetupEvent event) {

    }

    @SubscribeEvent
    private void registerCommands(RegisterCommandsEvent event) {
        PanoramaCommand.register(event.getDispatcher());
    }

}
