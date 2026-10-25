package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

public class LanguageManager {

    private final JavaPlugin plugin;
    private FileConfiguration langConfig;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String langCode = plugin.getConfig().getString("language", "zh_CN");
        String fileName = "Language-" + langCode + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);

        if (!langFile.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // 配置里填了 jar 中不存在的语言（如 fr_FR），回退到 en_US 而不是让启动崩溃
                plugin.getLogger().warning("Language file '" + fileName + "' not found in plugin jar, falling back to en_US.");
                fileName = "Language-en_US.yml";
                try {
                    plugin.saveResource(fileName, false);
                } catch (IllegalArgumentException ignored) {
                }
                langFile = new File(plugin.getDataFolder(), fileName);
            }
        }

        if (!langFile.exists()) {
            plugin.getLogger().severe("Language file could not be created: " + fileName);
            this.langConfig = new YamlConfiguration();
            return;
        }

        try {
            this.langConfig = YamlConfiguration.loadConfiguration(langFile);
            plugin.getLogger().info("Loaded language: " + fileName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load language file: " + fileName, e);
            this.langConfig = new YamlConfiguration();
        }
    }

    public String getRaw(String key) {
        return getRaw(key, (String) null);
    }

    /** 键缺失时回退到 fallback 文案（老版本语言文件没有新键，保证消息不为空） */
    public String getRaw(String key, String fallback) {
        if (langConfig == null) return key;
        String prefix = langConfig.getString("prefix", "");
        String msg = langConfig.contains(key)
                ? langConfig.getString(key, "")
                : (fallback == null ? "" : fallback);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public String getRaw(String key, Map<String, String> placeholders) {
        return getRaw(key, placeholders, null);
    }

    public String getRaw(String key, Map<String, String> placeholders, String fallback) {
        String raw = getRaw(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return raw;
    }

    public Component get(String key) {
        return serializer.deserialize(getRaw(key));
    }

    public Component get(String key, String fallback) {
        return serializer.deserialize(getRaw(key, fallback));
    }

    public Component get(String key, Map<String, String> placeholders) {
        return serializer.deserialize(getRaw(key, placeholders));
    }

    public Component get(String key, Map<String, String> placeholders, String fallback) {
        return serializer.deserialize(getRaw(key, placeholders, fallback));
    }

    public void reload() {
        load();
    }
}
