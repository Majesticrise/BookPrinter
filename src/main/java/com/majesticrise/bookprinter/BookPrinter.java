package com.majesticrise.bookprinter;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public final class BookPrinter extends JavaPlugin {

    private static final String LATEST_CONFIG_VERSION = "2.0";
    private LanguageManager languageManager;
    private net.milkbowl.vault.economy.Economy economy = null;
    private BookGUI bookGUI;
    private BookPrinterCommand commandExecutor;
    private PaymentService paymentService;
    private BookService bookService;

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ensureLanguageFilesExist();
        saveDefaultConfig();
        this.languageManager = new LanguageManager(this);
        languageManager.load();
        checkConfigUpdate();
        reloadConfig();
        setupVault();
        this.bookGUI = new BookGUI(this, languageManager);
        this.paymentService = new PaymentService(this, economy);
        this.bookService = new BookService(this, paymentService);
        this.commandExecutor = new BookPrinterCommand(this, bookService);
        getServer().getPluginManager().registerEvents(bookGUI, this);

        var cmd = getCommand("bookprinter");
        if (cmd != null) {
            cmd.setExecutor(commandExecutor);
            cmd.setTabCompleter(commandExecutor);
        }

        getLogger().info(languageManager.getRaw("log_plugin_enabled"));

        if (getConfig().getBoolean("enable-buy-command", true)) {
            getLogger().info("Buy command is enabled.");
        } else {
            getLogger().info("Buy command is disabled via config.");
        }
    }

    private void ensureLanguageFilesExist() {
        if (!new File(getDataFolder(), "Language-zh_CN.yml").exists()) {
            saveResource("Language-zh_CN.yml", false);
        }
        if (!new File(getDataFolder(), "Language-en_US.yml").exists()) {
            saveResource("Language-en_US.yml", false);
        }
        if (!new File(getDataFolder(), "Language-ES.yml").exists()) {
            saveResource("Language-ES.yml", false);
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

    public void checkConfigUpdate() {
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

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public BookGUI getBookGUI() {
        return bookGUI;
    }

    public BookService getBookService() {
        return bookService;
    }
}
