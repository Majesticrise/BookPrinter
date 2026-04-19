package com.majesticrise.bookprinter;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;

public class PaymentService {

    private final BookPrinter plugin;
    private final Economy economy;

    public PaymentService(BookPrinter plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public boolean canAfford(Player player) {
        ConfigurationSection config = plugin.getConfig();
        final double price = config.getDouble("buy-price", 100.0);
        final String paymentMethod = config.getString("payment.method", "money");
        final String expType = config.getString("payment.exp-type", "level");

        if ("money".equalsIgnoreCase(paymentMethod)) {
            if (economy == null) {
                player.sendMessage(plugin.getLanguageManager().get("vault_not_available"));
                return false;
            }
            if (!economy.has(player, price)) {
                player.sendMessage(plugin.getLanguageManager().get("insufficient_funds", Map.of("price", String.valueOf(price))));
                return false;
            }
        } else if ("exp".equalsIgnoreCase(paymentMethod)) {
            if ("level".equalsIgnoreCase(expType)) {
                int requiredLevel = (int) price;
                if (player.getLevel() < requiredLevel) {
                    player.sendMessage(plugin.getLanguageManager().get("insufficient_exp_level", Map.of("level", String.valueOf(requiredLevel))));
                    return false;
                }
            } else {
                int requiredPoints = (int) price;
                if (player.getTotalExperience() < requiredPoints) {
                    player.sendMessage(plugin.getLanguageManager().get("insufficient_exp_points", Map.of("points", String.valueOf(requiredPoints))));
                    return false;
                }
            }
        } else {
            plugin.getLogger().warning("Unknown payment method: " + paymentMethod);
            player.sendMessage(plugin.getLanguageManager().get("internal_error"));
            return false;
        }
        return true;
    }

    public boolean processPayment(Player player) {
        ConfigurationSection config = plugin.getConfig();
        final double price = config.getDouble("buy-price", 100.0);
        final String paymentMethod = config.getString("payment.method", "money");
        final String expType = config.getString("payment.exp-type", "level");

        boolean paymentSuccess = false;
        if ("money".equalsIgnoreCase(paymentMethod)) {
            if (economy.withdrawPlayer(player, price).transactionSuccess()) {
                player.sendMessage(plugin.getLanguageManager().get("payment_success", Map.of("price", String.valueOf(price))));
                paymentSuccess = true;
            } else {
                player.sendMessage(plugin.getLanguageManager().get("payment_failed"));
            }
        } else if ("exp".equalsIgnoreCase(paymentMethod)) {
            if ("level".equalsIgnoreCase(expType)) {
                int requiredLevel = (int) price;
                player.setLevel(player.getLevel() - requiredLevel);
                player.sendMessage(plugin.getLanguageManager().get("payment_success_exp_level", Map.of("level", String.valueOf(requiredLevel))));
                paymentSuccess = true;
            } else {
                int requiredPoints = (int) price;
                player.setTotalExperience(Math.max(0, player.getTotalExperience() - requiredPoints));
                player.sendMessage(plugin.getLanguageManager().get("payment_success_exp_points", Map.of("points", String.valueOf(requiredPoints))));
                paymentSuccess = true;
            }
        }
        return paymentSuccess;
    }
}
