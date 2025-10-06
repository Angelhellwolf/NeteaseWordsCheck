package com.remiaft.neteasewordscheck.bukkit;

import com.remiaft.neteasewordscheck.config.PluginConfiguration;
import com.remiaft.neteasewordscheck.service.CheckResult;
import com.remiaft.neteasewordscheck.service.NeteaseWordsCheckCore;
import com.remiaft.neteasewordscheck.service.WordCheckService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class NeteaseWordsCheckBukkitPlugin extends JavaPlugin implements Listener {
    private static final long CHECK_TIMEOUT_SECONDS = 5L;

    private NeteaseWordsCheckCore core;
    private PluginConfiguration configuration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadComponent();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("NeteaseWordsCheck enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.close();
            core = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (core == null) {
            return;
        }
        WordCheckService service = core.getWordCheckService();
        try {
            CheckResult result = service.checkAsync(event.getMessage()).get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!result.isAllowed() && configuration.isBlockOnViolation()) {
                event.setCancelled(true);
                String details = result.getViolationDetails().orElse("未知违规内容");
                runSync(() -> event.getPlayer().sendMessage("§c消息包含敏感词：" + details));
            }
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Failed to check message with NetEase API", exception);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"neteasecheck".equalsIgnoreCase(command.getName())) {
            return false;
        }
        if (args.length == 0) {
            sender.sendMessage("§e用法: /neteasecheck <内容>");
            return true;
        }
        if (core == null) {
            sender.sendMessage("§c插件尚未完全初始化。");
            return true;
        }
        String message = String.join(" ", args);
        sender.sendMessage("§7正在检查消息，请稍候...");
        core.getWordCheckService().checkAsync(message).whenComplete((result, throwable) -> {
            if (throwable != null) {
                getLogger().log(Level.WARNING, "Failed to check message", throwable);
                runSync(() -> sender.sendMessage("§c检查失败，请查看后台日志。"));
                return;
            }
            if (result.isAllowed()) {
                runSync(() -> sender.sendMessage("§a未检测到敏感词。"));
            } else {
                runSync(() -> sender.sendMessage("§c检测到敏感词：" + result.getViolationDetails().orElse("未知")));
            }
        });
        return true;
    }

    private void runSync(Runnable runnable) {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = method.invoke(Bukkit.getServer());
            Method execute = scheduler.getClass().getMethod("execute", JavaPlugin.class, Consumer.class);
            Consumer<Object> taskConsumer = task -> runnable.run();
            execute.invoke(scheduler, this, taskConsumer);
        } catch (Exception ignored) {
            Bukkit.getScheduler().runTask(this, runnable);
        }
    }

    private void reloadComponent() {
        FileConfiguration config = getConfig();
        Properties properties = toProperties(config);
        configuration = PluginConfiguration.fromProperties(properties);
        if (core != null) {
            core.close();
        }
        try {
            Path dataPath = new File(getDataFolder(), "data").toPath();
            core = new NeteaseWordsCheckCore(dataPath, configuration, getLogger());
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Unable to initialize cache storage", exception);
        }
    }

    private Properties toProperties(FileConfiguration configuration) {
        Properties properties = new Properties();
        configuration.getKeys(true).forEach(key -> {
            Object value = configuration.get(key);
            if (value != null) {
                properties.put(key, String.valueOf(value));
            }
        });
        return properties;
    }
}
