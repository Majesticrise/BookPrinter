package com.majesticrise.bookprinter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BookPrinterCommand implements CommandExecutor, TabCompleter {

    private final BookPrinter plugin;
    private final BookService bookService;

    public BookPrinterCommand(BookPrinter plugin, BookService bookService) {
        this.plugin = plugin;
        this.bookService = bookService;
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
                plugin.reloadConfig();
                plugin.checkConfigUpdate();
                plugin.getLanguageManager().reload();
                sender.sendMessage(plugin.getLanguageManager().get("reload_success"));
                return true;

            case "info":
                if (!checkPermission(sender, "bookprinter.info")) return true;
                sendInfo(sender);
                return true;

            case "print":
                if (!checkPermission(sender, "bookprinter.print")) return true;
                handlePrintCommand(sender, args);
                return true;

            case "buy":
                if (!checkPermission(sender, "bookprinter.buy")) return true;
                if (!plugin.getConfig().getBoolean("enable-buy-command", true)) {
                    sender.sendMessage(plugin.getLanguageManager().get("buy_command_disabled"));
                    return true;
                }
                handleBuyCommand(sender, args);
                return true;

            case "gui":
                if (!checkPermission(sender, "bookprinter.gui")) return true;
                if (!(sender instanceof org.bukkit.entity.Player)) {
                    sender.sendMessage(plugin.getLanguageManager().get("only_players"));
                    return true;
                }
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
                boolean chargeForBuy = plugin.getConfig().getBoolean("enable-buy-command", true);
                boolean charge;
                if (player.hasPermission("bookprinter.print")) {
                    charge = false;
                } else if (chargeForBuy && player.hasPermission("bookprinter.buy")) {
                    charge = true;
                } else {
                    player.sendMessage(plugin.getLanguageManager().get("no_permission"));
                    return true;
                }
                plugin.getBookGUI().openMainGUI(player, 0, charge, player.getName());
                return true;

            default:
                sender.sendMessage(plugin.getLanguageManager().get("unknown_command"));
                sendUsage(sender);
                return true;
        }
    }

    private void handlePrintCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().get("usage_main"));
            return;
        }
        String[] fileArgs = Arrays.copyOfRange(args, 1, args.length);
        bookService.processFileCommand(sender, fileArgs, false);
    }

    private void handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(plugin.getLanguageManager().get("only_players"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().get("usage_main"));
            return;
        }
        String[] fileArgs = Arrays.copyOfRange(args, 1, args.length);
        bookService.processFileCommand(sender, fileArgs, true);
    }

    private void sendInfo(CommandSender sender) {
        org.bukkit.configuration.ConfigurationSection config = plugin.getConfig();
        long maxBytes = config.getLong("max_file_bytes", 2097152);
        double mb = maxBytes / (1024.0 * 1024.0);
        String sizeStr = String.format("%.2f MB", mb);

        String mode = config.getString("Switch-mode", "classic");
        String lang = config.getString("language", "zh_CN");
        String version = plugin.getPluginMeta().getVersion();

        sender.sendMessage(plugin.getLanguageManager().get("info_header"));
        sender.sendMessage(plugin.getLanguageManager().get("info_mode", java.util.Map.of("mode", mode)));
        sender.sendMessage(plugin.getLanguageManager().get("info_lang", java.util.Map.of("lang", lang)));
        sender.sendMessage(plugin.getLanguageManager().get("info_max_bytes", java.util.Map.of("size", sizeStr)));
        sender.sendMessage(plugin.getLanguageManager().get("info_version", java.util.Map.of("version", version)));
        sender.sendMessage(plugin.getLanguageManager().get("info_footer"));
    }

    private boolean checkPermission(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        sender.sendMessage(plugin.getLanguageManager().get("no_permission"));
        return false;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().get("usage_main"));
        String mode = plugin.getConfig().getString("Switch-mode", "classic");
        sender.sendMessage(plugin.getLanguageManager().get("usage_mode", java.util.Map.of("mode", mode)));
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
            String subCmd = args[0].toLowerCase(Locale.ROOT);
            if (subCmd.equals("print") || subCmd.equals("buy")) {
                org.bukkit.configuration.ConfigurationSection config = plugin.getConfig();
                boolean allowSubdirs;
                String mode = config.getString("Switch-mode", "classic").toLowerCase(Locale.ROOT);
                if ("classic".equals(mode)) {
                    org.bukkit.configuration.ConfigurationSection classicCfg = config.getConfigurationSection("classic");
                    allowSubdirs = classicCfg != null && classicCfg.getBoolean("allow_subdirs", false);
                } else {
                    allowSubdirs = true;
                }
                List<String> fileNames = collectTxtFiles(plugin.getDataFolder(), allowSubdirs);
                String input = args[1].toLowerCase(Locale.ROOT);
                for (String name : fileNames) {
                    if (input.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(input)) {
                        completions.add(name);
                    }
                }
            }
        }
        return completions;
    }

    private List<String> collectTxtFiles(java.io.File dir, boolean recursive) {
        List<String> files = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return files;
        collectTxtFilesRecursive(dir, "", recursive, files);
        return files;
    }

    private void collectTxtFilesRecursive(java.io.File dir, String prefix, boolean recursive, List<String> files) {
        java.io.File[] list = dir.listFiles();
        if (list == null) return;
        for (java.io.File f : list) {
            if (f.isDirectory() && recursive) {
                collectTxtFilesRecursive(f, prefix + f.getName() + "/", recursive, files);
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                files.add(prefix + f.getName());
            }
        }
    }
}
