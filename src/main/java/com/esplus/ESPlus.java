package com.esplus;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.audit.BehaviorHooks;
import com.esplus.audit.MovementTracker;
import com.esplus.command.ESPlusCommands;
import com.esplus.command.SudoCommands;
import com.esplus.panel.IsolatedSpringPanel;
import com.esplus.panel.PanelActionProcessor;
import com.esplus.panel.ServerSnapshotSync;
import com.esplus.security.SecurityService;
import com.esplus.security.gate.CommandGate;
import com.esplus.ui.PasswordPromptBridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(ESPlus.MODID)
public class ESPlus {
    public static final String MODID = "esplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ESPlus instance;

    private final SecurityService securityService = new SecurityService();
    private final IsolatedSpringPanel springPanel = new IsolatedSpringPanel();
    private final PasswordPromptBridge passwordBridge = new PasswordPromptBridge();

    public ESPlus(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new CommandGate(securityService));
        NeoForge.EVENT_BUS.register(new BehaviorHooks(securityService));
        NeoForge.EVENT_BUS.register(new MovementTracker(securityService));
        NeoForge.EVENT_BUS.register(new PanelActionProcessor(securityService));
        NeoForge.EVENT_BUS.register(new ServerSnapshotSync(securityService));
    }

    public static PasswordPromptBridge getPasswordBridge() {
        return instance == null ? null : instance.passwordBridge;
    }

    public static SecurityService getSecurityService() {
        return instance == null ? null : instance.securityService;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        securityService.start(event.getServer());
        if (!securityService.isReady()) {
            LOGGER.error("ESPlus security is NOT ready: {}", securityService.failureReason());
            return;
        }
        if (securityService.databasePath() != null) {
            springPanel.start(securityService.databasePath(), event.getServer().getServerDirectory());
        }
        LOGGER.info("""

                   _____ __________  __
                  / ___// ____/ __ \\/ /_  _______
                  \\__ \\/ __/ / /_/ / / / / / ___/
                 ___/ / /___/ ____/ / /_/ (__  )
                /____/_____/_/   /_/\\\\__,_/____/

                ESPlus security suite starting (Qt password UI + isolated Spring panel)
                """);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        springPanel.stop();
        securityService.stop();
        LOGGER.info("ESPlus security suite stopped");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ESPlusCommands.register(event.getDispatcher(), securityService, passwordBridge);
        SudoCommands.register(event.getDispatcher(), event.getBuildContext(), securityService, passwordBridge);
    }
}
