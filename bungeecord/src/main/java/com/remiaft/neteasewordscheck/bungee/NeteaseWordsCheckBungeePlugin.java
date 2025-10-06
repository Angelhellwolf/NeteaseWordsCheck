package com.remiaft.neteasewordscheck.bungee;

import com.remiaft.neteasewordscheck.config.PluginConfiguration;
import com.remiaft.neteasewordscheck.service.CheckResult;
import com.remiaft.neteasewordscheck.service.NeteaseWordsCheckCore;
import com.remiaft.neteasewordscheck.service.WordCheckService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class NeteaseWordsCheckBungeePlugin extends Plugin implements Listener {
    private static final long CHECK_TIMEOUT_SECONDS = 5L;

    private NeteaseWordsCheckCore core;
    private PluginConfiguration configuration;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Unable to save default configuration", exception);
        }
        reloadComponent();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new NeteaseCheckCommand());
        getLogger().info("NeteaseWordsCheck Bungee module enabled.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.close();
            core = null;
        }
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (core == null || event.isCommand()) {
            return;
        }
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }
        WordCheckService service = core.getWordCheckService();
        try {
            CheckResult result = service.checkAsync(event.getMessage()).get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.isAllowed() && configuration.isBlockOnViolation()) {
                event.setCancelled(true);
                String details = result.getViolationDetails().orElse("未知违规内容");
                player.sendMessage(ChatMessageType.CHAT, new TextComponent("§c消息包含敏感词：" + details));
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Failed to check chat message", exception);
        }
    }

    private void reloadComponent() {
        try {
            Properties properties = loadProperties();
            configuration = PluginConfiguration.fromProperties(properties);
            if (core != null) {
                core.close();
            }
            Path dataPath = new File(getDataFolder(), "data").toPath();
            core = new NeteaseWordsCheckCore(dataPath, configuration, getLogger());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to initialize NeteaseWordsCheck", exception);
        }
    }

    private Properties loadProperties() throws IOException {
        File configFile = new File(getDataFolder(), "config.yml");
        Configuration configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        Properties properties = new Properties();
        populateProperties(configuration, properties, "");
        return properties;
    }

    private void populateProperties(Configuration source, Properties properties, String prefix) {
        Collection<String> keys = source.getKeys();
        for (String key : keys) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof Configuration section) {
                populateProperties(section, properties, fullKey);
            } else if (value != null) {
                properties.put(fullKey, String.valueOf(value));
            }
        }
    }

    private void saveDefaultConfig() throws IOException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in == null) {
                    throw new IOException("Default configuration not found");
                }
                Files.copy(in, configFile.toPath());
            }
        }
    }

    private final class NeteaseCheckCommand extends Command {
        private NeteaseCheckCommand() {
            super("neteasecheck", null, "nwc");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (core == null) {
                sender.sendMessage(new TextComponent("§c插件尚未准备就绪。"));
                return;
            }
            if (args.length == 0) {
                sender.sendMessage(new TextComponent("§e用法: /neteasecheck <内容>"));
                return;
            }
            String message = String.join(" ", args);
            sender.sendMessage(new TextComponent("§7正在检查消息，请稍候..."));
            core.getWordCheckService().checkAsync(message).whenComplete((result, throwable) -> {
                if (throwable != null) {
                    getLogger().log(Level.WARNING, "Failed to check message", throwable);
                    sender.sendMessage(new TextComponent("§c检查失败，请查看后台日志。"));
                    return;
                }
                if (result.isAllowed()) {
                    sender.sendMessage(new TextComponent("§a未检测到敏感词。"));
                } else {
                    sender.sendMessage(new TextComponent("§c检测到敏感词：" + result.getViolationDetails().orElse("未知")));
                }
            });
        }
    }
}
