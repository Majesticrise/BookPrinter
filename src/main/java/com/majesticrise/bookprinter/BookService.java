package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BookService {

    private final BookPrinter plugin;
    private final PaymentService paymentService;

    public BookService(BookPrinter plugin, PaymentService paymentService) {
        this.plugin = plugin;
        this.paymentService = paymentService;
    }

    public void processFileCommand(org.bukkit.command.CommandSender sender, String[] args, boolean charge) {
        final ConfigurationSection config = plugin.getConfig();
        final String mode = config.getString("Switch-mode", "classic").toLowerCase(Locale.ROOT);
        final long maxSizeBytes = config.getLong("max_file_bytes", 2097152);
        final boolean isClassic = "classic".equals(mode);

        String rawInput = args[0];
        final String fileName = rawInput.toLowerCase(Locale.ROOT).endsWith(".txt") ? rawInput : rawInput + ".txt";

        if (fileName.contains("../")) {
            sender.sendMessage(plugin.getLanguageManager().get("path_invalid"));
            return;
        }

        if (isClassic) {
            ConfigurationSection classicCfg = config.getConfigurationSection("classic");
            boolean allowSubdirs = classicCfg != null && classicCfg.getBoolean("allow_subdirs", false);
            if (!allowSubdirs && (fileName.contains("/") || fileName.contains("\\"))) {
                sender.sendMessage(plugin.getLanguageManager().get("path_no_subdir"));
                return;
            }
        }

        String potentialAuthor = null;
        if (args.length >= 2) {
            potentialAuthor = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        } else if (sender instanceof Player p) {
            potentialAuthor = p.getName();
        }

        if (potentialAuthor == null) {
            sender.sendMessage(plugin.getLanguageManager().get("usage_main"));
            return;
        }

        final String author = potentialAuthor.replaceAll("[\r\n]", " ").trim();
        final String finalAuthor = author.length() > 32 ? author.substring(0, 32) : author;

        File userFile = new File(fileName);
        if (!userFile.isAbsolute()) {
            userFile = new File(plugin.getDataFolder(), fileName);
        } else {
            ConfigurationSection classicCfg = config.getConfigurationSection("classic");
            boolean allowAbs = isClassic && classicCfg != null && classicCfg.getBoolean("allow_absolute_paths", false);
            if (!allowAbs) {
                sender.sendMessage(plugin.getLanguageManager().get("path_no_absolute"));
                return;
            }
        }

        final File targetFile;
        try {
            File dataFolderCanonical = plugin.getDataFolder().getCanonicalFile();
            File targetFileCanonical = userFile.getCanonicalFile();

            if (!targetFileCanonical.toPath().startsWith(dataFolderCanonical.toPath())) {
                sender.sendMessage(plugin.getLanguageManager().get("path_invalid"));
                String path = userFile.getPath();
                String logMsg = plugin.getLanguageManager().getRaw("log_path_denied", Map.of("path", path));
                plugin.getLogger().warning(logMsg);
                return;
            }
            targetFile = targetFileCanonical;
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Path parsing error", e);
            sender.sendMessage(plugin.getLanguageManager().get("path_invalid"));
            return;
        }

        if (charge) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getLanguageManager().get("only_players"));
                return;
            }
            if (!paymentService.canAfford(player)) {
                return;
            }
        }

        sender.sendMessage(plugin.getLanguageManager().get("start_generating"));

        Bukkit.getAsyncScheduler().runNow(plugin, (task) -> {
            try {
                if (!targetFile.exists() || !targetFile.isFile()) {
                    scheduleGlobal(() -> sender.sendMessage(plugin.getLanguageManager().get("file_not_found", Map.of("file", targetFile.getName()))));
                    return;
                }
                long size = Files.size(targetFile.toPath());
                if (size > maxSizeBytes) {
                    scheduleGlobal(() -> sender.sendMessage(plugin.getLanguageManager().get("file_too_large", Map.of("size", String.valueOf(size), "limit", String.valueOf(maxSizeBytes)))));
                    return;
                }
                String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
                List<Component> pages = "modern".equals(mode)
                        ? TextUtils.parseModernMode(content, config)
                        : TextUtils.parseClassicMode(content, config);

                if (sender instanceof Player player) {
                    Location loc = player.getLocation();
                    Bukkit.getRegionScheduler().execute(plugin, loc, () -> {
                        if (charge) {
                            if (!paymentService.processPayment(player)) {
                                return;
                            }
                        }
                        giveBookToPlayer(player, targetFile.getName(), finalAuthor, pages);
                    });
                } else {
                    scheduleGlobal(() -> sender.sendMessage(plugin.getLanguageManager().get("console_generated", Map.of("pages", String.valueOf(pages.size())))));
                }
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "IO error", e);
                scheduleGlobal(() -> sender.sendMessage(plugin.getLanguageManager().get("log_io_error")));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Async task error", e);
                scheduleGlobal(() -> sender.sendMessage(plugin.getLanguageManager().get("internal_error")));
            }
        });
    }

    private void giveBookToPlayer(Player player, String fileName, String author, List<Component> pages) {
        if (!player.isOnline()) {
            String name = player.getName();
            plugin.getLogger().info(plugin.getLanguageManager().getRaw("log_player_offline", Map.of("name", name)));
            return;
        }

        try {
            ItemStack book = createBookItem(fileName, author, pages);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);

            if (leftover.isEmpty()) {
                player.sendMessage(plugin.getLanguageManager().get("success"));
                var map = Map.of("file", fileName, "pages", String.valueOf(pages.size()));
                player.sendMessage(plugin.getLanguageManager().get("success_detail", map));
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), book);
                player.sendMessage(plugin.getLanguageManager().get("inventory_full"));
            }

        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, plugin.getLanguageManager().getRaw("log_give_error"), e);
            player.sendMessage(plugin.getLanguageManager().get("internal_error"));
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

    private void scheduleGlobal(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }
}
