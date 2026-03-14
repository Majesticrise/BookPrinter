package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

public final class BookPrinter extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String LATEST_CONFIG_VERSION = "2.0";
    private LanguageManager languageManager;
    private net.milkbowl.vault.economy.Economy economy = null;
    private final boolean vaultEnabled = false;

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ensureLanguageFilesExist();
        saveDefaultConfig();
        this.languageManager = new LanguageManager(this);
        languageManager.load();
        checkConfigUpdate();
        reloadConfig();
        setupVault();


        PluginCommand cmd = getCommand("bookprinter");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        getLogger().info(languageManager.getRaw("log_plugin_enabled"));

        if (getConfig().getBoolean("enable-buy-command", true)) {
            getLogger().info("Buy command is enabled.");
        } else {
            getLogger().info("Buy command is disabled via config.");
        }
    }

    private void ensureLanguageFilesExist() {
        // 如果文件不存在，才从资源中保存 (false 表示不覆盖已存在文件)
        if (!new File(getDataFolder(), "Language-zh_CN.yml").exists()) {
            saveResource("Language-zh_CN.yml", false);
        }
        if (!new File(getDataFolder(), "Language-en_US.yml").exists()) {
            saveResource("Language-en_US.yml", false);
        }
    }

    private void setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found, buy command will be disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Vault economy provider not found, buy command disabled.");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("Vault economy hooked successfully.");
    }

    private void checkConfigUpdate() {
        File currentConfigFile = new File(getDataFolder(), "config.yml");
        if (!currentConfigFile.exists()) return;

        try {
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(currentConfigFile);
            String currentVersion = currentConfig.getString("config_version", "1.0");

            if (!LATEST_CONFIG_VERSION.equals(currentVersion)) {
                File backupFile = new File(getDataFolder(), "config_old_" + System.currentTimeMillis() + ".yml");
                if (currentConfigFile.renameTo(backupFile)) {
                    saveDefaultConfig();
                    String msg = languageManager.getRaw("log_config_updated");
                    getLogger().warning(msg);
                    reloadConfig();
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to check config version", e);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCmd = args[0].toLowerCase(Locale.ROOT);

        switch (subCmd) {
            case "reload":
                if (!checkPermission(sender, "bookprinter.reload")) return true;
                reloadConfig();
                checkConfigUpdate();
                languageManager.reload();
                sender.sendMessage(languageManager.get("reload_success"));
                return true;

            case "info":
                if (!checkPermission(sender, "bookprinter.info")) return true;
                sendInfo(sender);
                return true;

            case "print":
                if (!checkPermission(sender, "bookprinter.print")) return true;
                // 处理 print 命令（管理员打印）
                handlePrintCommand(sender, args);
                return true;

            case "buy":
                if (!checkPermission(sender, "bookprinter.buy")) return true;
                // 检查配置是否启用了购买命令
                if (!getConfig().getBoolean("enable-buy-command", true)) {
                    sender.sendMessage(languageManager.get("buy_command_disabled"));
                    return true;
                }
                handleBuyCommand(sender, args);
                return true;

            default:
                sender.sendMessage(languageManager.get("unknown_command"));
                sendUsage(sender);
                return true;
        }
    }

    private void handlePrintCommand(CommandSender sender, String[] args) {
        // 参数格式: /bp print <文件名> [作者]
        if (args.length < 2) {
            sender.sendMessage(languageManager.get("usage_main"));
            return;
        }
        // 提取文件名和作者，复用原逻辑（注意：原逻辑期望 args[0] 是文件名，现在 args[0] 是 "print"）
        String[] fileArgs = Arrays.copyOfRange(args, 1, args.length);
        processFileCommand(sender, fileArgs, false); // false 表示不收费
    }

    private void handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(languageManager.get("only_players"));
            return;
        }
        if (!vaultEnabled) {
            sender.sendMessage(languageManager.get("vault_not_available"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(languageManager.get("usage_main"));
            return;
        }
        String[] fileArgs = Arrays.copyOfRange(args, 1, args.length);
        processFileCommand(sender, fileArgs, true); // true 表示收费
    }

    private void processFileCommand(CommandSender sender, String[] args, boolean charge) {
        // 1. 解析文件名和作者（复用原逻辑）
        final ConfigurationSection config = getConfig();
        final String mode = config.getString("Switch-mode", "classic").toLowerCase(Locale.ROOT);
        final long maxSizeBytes = config.getLong("max_file_bytes", 2097152);
        final boolean isClassic = "classic".equals(mode);

        // 处理文件名
        String rawInput = args[0];
        final String fileName = rawInput.toLowerCase(Locale.ROOT).endsWith(".txt") ? rawInput : rawInput + ".txt";

        // 路径安全检查（原逻辑）
        if (fileName.contains("../")) {
            sender.sendMessage(languageManager.get("path_invalid"));
            return;
        }

        // 经典模式下的子目录限制
        if (isClassic) {
            ConfigurationSection classicCfg = config.getConfigurationSection("classic");
            boolean allowSubdirs = classicCfg != null && classicCfg.getBoolean("allow_subdirs", false);
            if (!allowSubdirs && (fileName.contains("/") || fileName.contains("\\"))) {
                sender.sendMessage(languageManager.get("path_no_subdir"));
                return;
            }
        }

        // 提取作者信息
        String potentialAuthor = null;
        UUID potentialUuid = null;

        if (args.length >= 2) {
            // 如果提供了额外参数，全部作为作者名（支持空格）
            potentialAuthor = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else if (sender instanceof Player p) {
            potentialAuthor = p.getName();
            potentialUuid = p.getUniqueId();  // 注意原代码拼写错误已修正
        }

        if (potentialAuthor == null) {
            sender.sendMessage(languageManager.get("usage_main")); // 缺少作者
            return;
        }

        // 清理作者名（去除换行、截断）
        final String author = potentialAuthor.replaceAll("[\r\n]", " ").trim();
        final String finalAuthor = author.length() > 32 ? author.substring(0, 32) : author;

        // 处理文件路径（绝对/相对）
        File userFile = new File(fileName);
        if (!userFile.isAbsolute()) {
            userFile = new File(getDataFolder(), fileName);
        } else {
            // 绝对路径检查（仅 classic 模式且配置允许）
            ConfigurationSection classicCfg = config.getConfigurationSection("classic");
            boolean allowAbs = isClassic && classicCfg != null && classicCfg.getBoolean("allow_absolute_paths", false);
            if (!allowAbs) {
                sender.sendMessage(languageManager.get("path_no_absolute"));
                return;
            }
        }

        // 最终规范化路径，确保在插件数据文件夹内
        final File targetFile;
        try {
            File dataFolderCanonical = getDataFolder().getCanonicalFile();
            File targetFileCanonical = userFile.getCanonicalFile();

            if (!targetFileCanonical.toPath().startsWith(dataFolderCanonical.toPath())) {
                sender.sendMessage(languageManager.get("path_invalid"));
                String path = userFile.getPath();
                String logMsg = languageManager.getRaw("log_path_denied", Map.of("path", path));
                getLogger().warning(logMsg);
                return;
            }
            targetFile = targetFileCanonical;
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Path parsing error", e);
            sender.sendMessage(languageManager.get("path_invalid"));
            return;
        }

        // 如果是收费模式且发送者是玩家，先检查余额
        final double price = config.getDouble("buy-price", 100.0);
        if (charge) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(languageManager.get("only_players"));
                return;
            }
            if (!economy.has(player, price)) {
                sender.sendMessage(languageManager.get("insufficient_funds", Map.of("price", String.valueOf(price))));
                return;
            }
        }

        sender.sendMessage(languageManager.get("start_generating"));

        // 异步处理文件读取和解析
        Bukkit.getAsyncScheduler().runNow(this, (task) -> {
            try {
                // 2. 异步读取和解析
                if (!targetFile.exists() || !targetFile.isFile()) {
                    scheduleGlobal(() -> sender.sendMessage(languageManager.get("file_not_found", Map.of("file", targetFile.getName()))));
                    return;
                }
                long size = Files.size(targetFile.toPath());
                if (size > maxSizeBytes) {
                    scheduleGlobal(() -> sender.sendMessage(languageManager.get("file_too_large", Map.of("size", String.valueOf(size), "limit", String.valueOf(maxSizeBytes)))));
                    return;
                }
                String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                List<Component> pages = "modern".equals(mode)
                        ? TextUtils.parseModernMode(content, config)
                        : TextUtils.parseClassicMode(content, config);

                // 3. 回到主线程处理扣费和给书
                if (sender instanceof Player player) {
                    Location loc = player.getLocation();
                    Bukkit.getRegionScheduler().execute(this, loc, () -> {
                        // 先扣费，后给书（若扣费失败则放弃）
                        if (charge) {
                            if (!economy.withdrawPlayer(player, price).transactionSuccess()) {
                                player.sendMessage(languageManager.get("payment_failed"));
                                return;
                            }
                            player.sendMessage(languageManager.get("payment_success", Map.of("price", String.valueOf(price))));
                        }
                        giveBookToPlayer(player, targetFile.getName(), finalAuthor, pages);
                    });
                } else {
                    // 控制台直接输出结果
                    scheduleGlobal(() -> sender.sendMessage(languageManager.get("console_generated", Map.of("pages", String.valueOf(pages.size())))));
                }
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "IO error", e);
                scheduleGlobal(() -> sender.sendMessage(languageManager.get("log_io_error")));
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Async task error", e);
                scheduleGlobal(() -> sender.sendMessage(languageManager.get("internal_error")));
            }
        });
    }

    private void sendInfo(CommandSender sender) {
        ConfigurationSection config = getConfig();
        long maxBytes = config.getLong("max_file_bytes", 2097152);
        // 字节转MB显示
        double mb = maxBytes / (1024.0 * 1024.0);
        String sizeStr = String.format("%.2f MB", mb);

        String mode = config.getString("Switch-mode", "classic");
        String lang = config.getString("language", "zh_CN");
        String version = Bukkit.getServer().getName().contains("Folia") ? (getPluginMeta().getVersion() + " (Folia)") : getPluginMeta().getVersion();

        sender.sendMessage(languageManager.get("info_header"));
        sender.sendMessage(languageManager.get("info_mode", Map.of("mode", mode)));
        sender.sendMessage(languageManager.get("info_lang", Map.of("lang", lang)));
        sender.sendMessage(languageManager.get("info_max_bytes", Map.of("size", sizeStr)));
        sender.sendMessage(languageManager.get("info_version", Map.of("version", version)));
        sender.sendMessage(languageManager.get("info_footer"));
    }

    private void giveBookToPlayer(Player player, String fileName, String author, List<Component> pages) {
        if (!player.isOnline()) {
            String name = player.getName();
            getLogger().info(languageManager.getRaw("log_player_offline", Map.of("name", name)));
            return;
        }

        try {
            ItemStack book = createBookItem(fileName, author, pages);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);

            if (leftover.isEmpty()) {
                player.sendMessage(languageManager.get("success"));
                var map = Map.of("file", fileName, "pages", String.valueOf(pages.size()));
                player.sendMessage(languageManager.get("success_detail", map));
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), book);
                player.sendMessage(languageManager.get("inventory_full"));
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, languageManager.getRaw("log_give_error"), e);
            player.sendMessage(languageManager.get("internal_error"));
        }
    }

    private ItemStack createBookItem(String fileName, String author, List<Component> pages) {
        ItemStack book = new ItemStack(org.bukkit.Material.WRITTEN_BOOK, 1);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        String title = TextUtils.extractTitleFromFileName(fileName);
        meta.title(LegacyComponentSerializer.legacySection().deserialize(title));
        meta.author(LegacyComponentSerializer.legacySection().deserialize(author));
        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private boolean checkPermission(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        sender.sendMessage(languageManager.get("no_permission"));
        return false;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(languageManager.get("usage_main"));
        String mode = getConfig().getString("Switch-mode", "classic");
        sender.sendMessage(languageManager.get("usage_mode", Map.of("mode", mode)));
    }

    private void scheduleGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(this, task);
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String @NotNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(input) && sender.hasPermission("bookprinter.reload"))
                completions.add("reload");
            if ("info".startsWith(input) && sender.hasPermission("bookprinter.info"))
                completions.add("info");
            if ("print".startsWith(input) && sender.hasPermission("bookprinter.print"))
                completions.add("print");
            if ("buy".startsWith(input) && sender.hasPermission("bookprinter.buy"))
                completions.add("buy");
        } else if (args.length == 2) {
            // 第二参数根据子命令提供文件列表（仅对 print 和 buy）
            String subCmd = args[0].toLowerCase(Locale.ROOT);
            if (subCmd.equals("print") || subCmd.equals("buy")) {
                File dir = getDataFolder();
                if (dir.exists()) {
                    String input = args[1].toLowerCase(Locale.ROOT);
                    File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
                    if (files != null) {
                        for (File f : files) {
                            String name = f.getName();
                            if (input.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(input)) {
                                completions.add(name);
                            }
                        }
                    }
                }
            }
        }
        // 如果 args.length == 3，可以考虑补全作者名（可选），这里简单返回空
        return completions;
    }
}
