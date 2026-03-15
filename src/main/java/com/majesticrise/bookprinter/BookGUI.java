package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class BookGUI {

    private final JavaPlugin plugin;
    private static LanguageManager lang = null;
    private static final List<File> txtFiles = new ArrayList<>();
    private static final Map<UUID, GUIState> playerState = new HashMap<>(); // 记录玩家当前状态

    // GUI 标题（使用语言文件）
    private static String MAIN_TITLE = null;
    private final String CONFIRM_TITLE;

    public BookGUI(JavaPlugin plugin, LanguageManager lang) {
        this.plugin = plugin;
        BookGUI.lang = lang;
        MAIN_TITLE = lang.getRaw("gui.main_title"); // 需要添加到语言文件
        this.CONFIRM_TITLE = lang.getRaw("gui.confirm_title");
        scanFiles();
    }

    // 扫描插件文件夹中的 .txt 文件（不递归子目录，可根据配置修改）
    private void scanFiles() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) return;
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files != null) {
            txtFiles.clear();
            txtFiles.addAll(Arrays.asList(files));
            // 按文件名排序，使显示有序
            txtFiles.sort(Comparator.comparing(File::getName));
        }
    }
    private static class GUIState {
        int page = 0;               // 当前页码（从0开始）
        File selectedFile = null;   // 在确认界面暂存选中的文件
        boolean charge;             // 是否需要收费（根据权限判断）
        String author;              // 作者（默认玩家名）

        GUIState(boolean charge, String author) {
            this.charge = charge;
            this.author = author;
        }
    }
    private static List<ItemStack> getPageItems(int page) {
        List<ItemStack> items = new ArrayList<>();
        int start = page * 45;
        int end = Math.min(start + 45, txtFiles.size());
        for (int i = start; i < end; i++) {
            File file = txtFiles.get(i);
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            // 文件名作为显示名称
            String displayName = TextUtils.extractTitleFromFileName(file.getName());
            meta.displayName(Component.text(displayName));
            // 添加 lore：文件大小、修改时间等（可选）
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7" + file.getName())); // 显示原始文件名
            lore.add(Component.text("§7Size: " + file.length() + " bytes"));
            meta.lore(lore);
            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }
    public static void openMainGUI(Player player, int page, boolean charge, String author) {
        // 计算总页数
        int totalPages = (int) Math.ceil((double) txtFiles.size() / 45);
        if (totalPages == 0) totalPages = 1;

        // 创建 inventory
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(MAIN_TITLE + " - " + (page+1) + "/" + totalPages));

        // 放置文件物品
        List<ItemStack> pageItems = getPageItems(page);
        for (int i = 0; i < pageItems.size(); i++) {
            inv.setItem(i, pageItems.get(i));
        }

        // 放置导航按钮（最后一行 45-53）
        // 上一页（如果 page > 0）
        if (page > 0) {
            ItemStack prev = createButton(Material.ARROW, lang.getRaw("gui.prev_page"));
            inv.setItem(45, prev);
        }
        // 下一页（如果 page < totalPages-1）
        if (page < totalPages - 1) {
            ItemStack next = createButton(Material.ARROW, lang.getRaw("gui.next_page"));
            inv.setItem(53, next);
        }
        // 关闭按钮（可选）
        ItemStack close = createButton(Material.BARRIER, lang.getRaw("gui.close"));
        inv.setItem(49, close);

        // 保存玩家状态
        playerState.put(player.getUniqueId(), new GUIState(charge, author));
        player.openInventory(inv);
    }

    // 辅助方法：创建带名称的物品
    private static ItemStack createButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }
    public void openConfirmGUI(Player player, File selectedFile) {
        // 创建 9 格的 inventory（一行）
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(CONFIRM_TITLE));

        // 绿色玻璃（确认）
        ItemStack confirm = createButton(Material.LIME_STAINED_GLASS_PANE, lang.getRaw("gui.confirm"));
        inv.setItem(2, confirm);
        inv.setItem(3, confirm);
        inv.setItem(4, confirm); // 中间放三个，更显眼

        // 红色玻璃（取消）
        ItemStack cancel = createButton(Material.RED_STAINED_GLASS_PANE, lang.getRaw("gui.cancel"));
        inv.setItem(5, cancel);
        inv.setItem(6, cancel);
        inv.setItem(7, cancel);

        // 更新玩家状态：记录选中的文件
        GUIState state = playerState.get(player.getUniqueId());
        if (state != null) {
            state.selectedFile = selectedFile;
        } else {
            // 理论上不应该发生，如果发生则创建一个默认状态（但这种情况需要处理）
            player.closeInventory();
            return;
        }
        player.openInventory(inv);
    }
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        String title = ChatColor.stripColor(event.getView().getTitle()); // 去除颜色代码比较

        // 检查是否是我们的 GUI（通过标题前缀判断）
        if (!title.startsWith(ChatColor.stripColor(MAIN_TITLE)) && !title.startsWith(ChatColor.stripColor(CONFIRM_TITLE))) {
            return;
        }

        event.setCancelled(true); // 阻止物品移动

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        // 获取玩家状态
        GUIState state = playerState.get(player.getUniqueId());
        if (state == null) return;

        if (title.startsWith(ChatColor.stripColor(MAIN_TITLE))) {
            // 主界面点击
            handleMainClick(player, inv, event.getSlot(), state);
        } else if (title.startsWith(ChatColor.stripColor(CONFIRM_TITLE))) {
            // 确认界面点击
            handleConfirmClick(player, event.getSlot(), state);
        }
    }

    private void handleMainClick(Player player, Inventory inv, int slot, GUIState state) {
        // 导航按钮区域
        if (slot >= 45) {
            // 上一页
            if (slot == 45 && inv.getItem(45) != null && Objects.requireNonNull(inv.getItem(45)).getType() == Material.ARROW) {
                openMainGUI(player, state.page - 1, state.charge, state.author);
            }
            // 下一页
            else if (slot == 53 && inv.getItem(53) != null && Objects.requireNonNull(inv.getItem(53)).getType() == Material.ARROW) {
                openMainGUI(player, state.page + 1, state.charge, state.author);
            }
            // 关闭
            else if (slot == 49 && inv.getItem(49) != null && Objects.requireNonNull(inv.getItem(49)).getType() == Material.BARRIER) {
                player.closeInventory();
                playerState.remove(player.getUniqueId());
            }
            return;
        }

        // 点击文件区域（0-44）
        int index = state.page * 45 + slot;
        if (index < txtFiles.size()) {
            File file = txtFiles.get(index);
            // 打开确认界面
            openConfirmGUI(player, file);
        }
    }

    private void handleConfirmClick(Player player, int slot, GUIState state) {
        if (state.selectedFile == null) return;

        // 判断点击的是确认还是取消
        if (slot >= 2 && slot <= 4) {
            // 确认区域：执行生成书籍
            player.closeInventory();
            // 调用主类的 processFileCommand 方法（需要传递参数）
            // 这里假设我们有一个方法可以直接调用，例如：
            BookPrinter pluginInstance = (BookPrinter) plugin;
            // 构造参数数组：文件名和作者
            String[] args = new String[]{state.selectedFile.getName(), state.author};
            // 注意：processFileCommand 需要 CommandSender（这里是 player）和是否收费
            pluginInstance.processFileCommand(player, args, state.charge);
            playerState.remove(player.getUniqueId());
        } else if (slot >= 5 && slot <= 7) {
            // 取消区域：返回主界面
            openMainGUI(player, state.page, state.charge, state.author);
        }
    }

}
