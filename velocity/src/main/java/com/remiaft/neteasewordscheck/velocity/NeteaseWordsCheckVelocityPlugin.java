package com.remiaft.neteasewordscheck.velocity;

import com.google.inject.Inject;
import com.remiaft.neteasewordscheck.config.PluginConfiguration;
import com.remiaft.neteasewordscheck.service.CheckResult;
import com.remiaft.neteasewordscheck.service.NeteaseWordsCheckCore;
import com.remiaft.neteasewordscheck.service.WordCheckService;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

@Plugin(id = "neteasewordscheck", name = "NeteaseWordsCheck", version = "1.0.0", authors = {"Angelhell"})
public final class NeteaseWordsCheckVelocityPlugin {
    private static final long CHECK_TIMEOUT_SECONDS = 5L;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final PluginContainer pluginContainer;

    private NeteaseWordsCheckCore core;
    private PluginConfiguration configuration;

    @Inject
    public NeteaseWordsCheckVelocityPlugin(ProxyServer server,
                                           Logger logger,
                                           PluginContainer pluginContainer,
                                           @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.pluginContainer = pluginContainer;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        try {
            initialize();
            server.getCommandManager()
                    .register(server.getCommandManager()
                            .metaBuilder("neteasecheck")
                            .aliases("nwc")
                            .plugin(pluginContainer)
                            .build(), new NeteaseCheckCommand());
            logger.info("NeteaseWordsCheck Velocity module enabled.");
        } catch (IOException exception) {
            logger.error("Failed to initialize plugin", exception);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (core != null) {
            core.close();
            core = null;
        }
    }

    @Subscribe
    public void onChat(PlayerChatEvent event) {
        if (core == null) {
            return;
        }
        WordCheckService service = core.getWordCheckService();
        String rawMessage = event.getMessage();
        try {
            CheckResult result = service.checkAsync(rawMessage).get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.isAllowed() && configuration.isBlockOnViolation()) {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                event.getPlayer().sendMessage(Component.text("消息包含敏感词：" + result.getViolationDetails().orElse("未知违规内容")));
            }
        } catch (Exception exception) {
            logger.warn("Failed to check chat message", exception);
        }
    }

    private void initialize() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path configFile = dataDirectory.resolve("config.properties");
        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (in == null) {
                    throw new IOException("Default configuration not found");
                }
                Files.copy(in, configFile);
            }
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        }
        configuration = PluginConfiguration.fromProperties(properties);
        if (core != null) {
            core.close();
        }
        Path dataPath = dataDirectory.resolve("data");
        core = new NeteaseWordsCheckCore(dataPath, configuration, createJulLogger());
    }

    private java.util.logging.Logger createJulLogger() {
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("NeteaseWordsCheck-Velocity");
        julLogger.setUseParentHandlers(false);
        for (Handler handler : julLogger.getHandlers()) {
            julLogger.removeHandler(handler);
        }
        Handler bridgeHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!isLoggable(record)) {
                    return;
                }
                Level level = record.getLevel();
                String message = record.getMessage();
                Throwable throwable = record.getThrown();
                if (level.intValue() >= Level.SEVERE.intValue()) {
                    logger.error(message, throwable);
                } else if (level.intValue() >= Level.WARNING.intValue()) {
                    logger.warn(message, throwable);
                } else if (level.intValue() >= Level.INFO.intValue()) {
                    logger.info(message, throwable);
                } else {
                    logger.debug(message, throwable);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        bridgeHandler.setLevel(Level.ALL);
        julLogger.addHandler(bridgeHandler);
        julLogger.setLevel(Level.ALL);
        return julLogger;
    }

    private final class NeteaseCheckCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (core == null) {
                invocation.source().sendMessage(Component.text("插件尚未准备就绪."));
                return;
            }
            String[] args = invocation.arguments();
            if (args.length == 0) {
                invocation.source().sendMessage(Component.text("用法: /neteasecheck <内容>"));
                return;
            }
            String message = String.join(" ", args);
            invocation.source().sendMessage(Component.text("正在检查消息，请稍候..."));
            core.getWordCheckService().checkAsync(message).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.warn("Failed to check message", throwable);
                    invocation.source().sendMessage(Component.text("检查失败，请查看后台日志。"));
                    return;
                }
                if (result.isAllowed()) {
                    invocation.source().sendMessage(Component.text("未检测到敏感词。"));
                } else {
                    invocation.source().sendMessage(Component.text("检测到敏感词：" + result.getViolationDetails().orElse("未知")));
                }
            });
        }
    }
}
