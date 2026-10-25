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

    private record PaymentConfig(double price, String method, String expType) {}

    // 价格在 config.yml 中位于 payment 节点下；兼容旧版写在顶层的配置
    private static PaymentConfig readConfig(ConfigurationSection config) {
        double price = config.contains("payment.buy-price")
                ? config.getDouble("payment.buy-price", 100.0)
                : config.getDouble("buy-price", 100.0);
        return new PaymentConfig(
                price,
                config.getString("payment.method", "money"),
                config.getString("payment.exp-type", "level"));
    }

    /** 购买前展示价格用（与扣款时读取的是同一配置路径） */
    public double getPrice() {
        return readConfig(plugin.getConfig()).price();
    }

    public boolean canAfford(Player player) {
        PaymentConfig pc = readConfig(plugin.getConfig());

        if ("money".equalsIgnoreCase(pc.method())) {
            if (economy == null) {
                player.sendMessage(plugin.getLanguageManager().get("vault_not_available"));
                return false;
            }
            if (!economy.has(player, pc.price())) {
                player.sendMessage(plugin.getLanguageManager().get("insufficient_funds", Map.of("price", String.valueOf(pc.price()))));
                return false;
            }
        } else if ("exp".equalsIgnoreCase(pc.method())) {
            if ("level".equalsIgnoreCase(pc.expType())) {
                int requiredLevel = (int) pc.price();
                if (player.getLevel() < requiredLevel) {
                    player.sendMessage(plugin.getLanguageManager().get("insufficient_exp_level", Map.of("level", String.valueOf(requiredLevel))));
                    return false;
                }
            } else {
                int requiredPoints = (int) pc.price();
                if (player.getTotalExperience() < requiredPoints) {
                    player.sendMessage(plugin.getLanguageManager().get("insufficient_exp_points", Map.of("points", String.valueOf(requiredPoints))));
                    return false;
                }
            }
        } else {
            plugin.getLogger().warning("Unknown payment method: " + pc.method());
            player.sendMessage(plugin.getLanguageManager().get("internal_error"));
            return false;
        }
        return true;
    }

    public boolean processPayment(Player player) {
        PaymentConfig pc = readConfig(plugin.getConfig());

        boolean paymentSuccess = false;
        if ("money".equalsIgnoreCase(pc.method())) {
            if (economy.withdrawPlayer(player, pc.price()).transactionSuccess()) {
                player.sendMessage(plugin.getLanguageManager().get("payment_success", Map.of("price", String.valueOf(pc.price()))));
                paymentSuccess = true;
            } else {
                player.sendMessage(plugin.getLanguageManager().get("payment_failed"));
            }
        } else if ("exp".equalsIgnoreCase(pc.method())) {
            if ("level".equalsIgnoreCase(pc.expType())) {
                int requiredLevel = (int) pc.price();
                // canAfford 与扣费之间有时间差，兜底防止等级被扣成负数
                player.setLevel(Math.max(0, player.getLevel() - requiredLevel));
                player.sendMessage(plugin.getLanguageManager().get("payment_success_exp_level", Map.of("level", String.valueOf(requiredLevel))));
                paymentSuccess = true;
            } else {
                int requiredPoints = (int) pc.price();
                player.setTotalExperience(Math.max(0, player.getTotalExperience() - requiredPoints));
                player.sendMessage(plugin.getLanguageManager().get("payment_success_exp_points", Map.of("points", String.valueOf(requiredPoints))));
                paymentSuccess = true;
            }
        }
        return paymentSuccess;
    }

    /** 发书失败时退还费用（与 processPayment 对应） */
    public void refund(Player player) {
        PaymentConfig pc = readConfig(plugin.getConfig());

        if ("money".equalsIgnoreCase(pc.method())) {
            if (economy != null) {
                economy.depositPlayer(player, pc.price());
            }
        } else if ("exp".equalsIgnoreCase(pc.method())) {
            if ("level".equalsIgnoreCase(pc.expType())) {
                player.setLevel(player.getLevel() + (int) pc.price());
            } else {
                player.setTotalExperience(player.getTotalExperience() + (int) pc.price());
            }
        }
    }
}
