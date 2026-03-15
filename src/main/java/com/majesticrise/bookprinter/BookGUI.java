package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

/**
 * 书籍选择GUI，支持翻页、文件选择和购买/打印确认。
 * 所有静态字段改为实例字段，避免重载冲突；
 * 颜色代码使用LegacyComponentSerializer正确解析；
 * 页码状态同步修复；
 * 调用主类的公共方法processPurchase。
 */
public class BookGUI {

    private final JavaPlugin plugin;
    private final LanguageManager lang;
    private final List<File> txtFiles = new ArrayList<>();
    private final Map<UUID, GUIState> playerState = new HashMap<>();

    // GUI 标题（从语言文件获取，已去除静态）
    private static final String MAIN_TITLE_KEY = "gui.main_title";
    private final String CONFIRM_TITLE_KEY = "gui.confirm_title";
    private static final String PREV_PAGE_KEY = "gui.prev_page";
    private static final String NEXT_PAGE_KEY = "gui.next_page";
    private static final String CLOSE_KEY = "gui.close";
    private final String CANCEL_KEY = "gui.cancel";
    private static final String NO_FILES_KEY = "gui.no_files";
    private final String STATE_LOST_KEY = "gui.state_lost";

    // 界面尺寸常量
    private static final int MAIN_INV_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int CLOSE_SLOT = 49;
    private static final int CONFIRM_INV_SIZE = 9;
    private static final int CONFIRM_START = 2;
    private static final int CONFIRM_END = 3;
    private static final int CANCEL_START = 5;
    private static final int CANCEL_END = 6;

    public BookGUI(JavaPlugin plugin, LanguageManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        scanFiles(); // 实例方法，填充自己的列表
    }

    /**
     * 扫描插件文件夹中的 .txt 文件（在主线程调用）
     */
    private void scanFiles() {
        File folder = plugin.getDataFolder();
        if (!folder.exists()) return;
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".txt"));
        if (files != null) {
            txtFiles.clear();
            txtFiles.addAll(Arrays.asList(files));
            txtFiles.sort(Comparator.comparing(File::getName));
        }
    }

    /**
     * 打开主界面
     * @param player 玩家
     * @param page 目标页码（从0开始）
     * @param charge 是否收费（true=buy, false=print）
     * @param author 作者名
     */
    public void openMainGUI(Player player, int page, boolean charge, String author) {
        scanFiles(); // 每次打开前刷新文件列表

        int totalPages = (int) Math.ceil((double) txtFiles.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        // 解析标题（支持颜色代码）
        String titleRaw = lang.getRaw(MAIN_TITLE_KEY) + " - " + (page + 1) + "/" + totalPages;
        Component title = LegacyComponentSerializer.legacyAmpersand().deserialize(titleRaw);
        Inventory inv = Bukkit.createInventory(null, MAIN_INV_SIZE, title);

        // 放置文件物品
        List<ItemStack> pageItems = getPageItems(page);
        for (int i = 0; i < pageItems.size(); i++) {
            inv.setItem(i, pageItems.get(i));
        }

        // 如果没有文件，显示提示
        if (txtFiles.isEmpty()) {
            ItemStack info = createButton(Material.PAPER, lang.getRaw(NO_FILES_KEY));
            inv.setItem(22, info);
        }

        // 导航按钮
        if (page > 0) {
            inv.setItem(PREV_SLOT, createButton(Material.ARROW, lang.getRaw(PREV_PAGE_KEY)));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, createButton(Material.ARROW, lang.getRaw(NEXT_PAGE_KEY)));
        }
        inv.setItem(CLOSE_SLOT, createButton(Material.BARRIER, lang.getRaw(CLOSE_KEY)));

        // 更新或创建玩家状态
        GUIState state = playerState.get(player.getUniqueId());
        if (state == null) {
            state = new GUIState(page, charge, author);
            playerState.put(player.getUniqueId(), state);
        } else {
            state.page = page;
            state.charge = charge;
            state.author = author;
            state.selectedFile = null; // 重置选择
        }
        player.openInventory(inv);
    }

    /**
     * 获取当前页的物品列表
     */
    private List<ItemStack> getPageItems(int page) {
        List<ItemStack> items = new ArrayList<>();
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, txtFiles.size());
        for (int i = start; i < end; i++) {
            File file = txtFiles.get(i);
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();

            // 显示名称：格式化后的文件名
            String displayName = TextUtils.extractTitleFromFileName(file.getName());
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(displayName));

            // Lore：原始文件名和大小（使用 Adventure 颜色）
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(file.getName()).color(NamedTextColor.GRAY));
            lore.add(Component.text("Size: " + file.length() + " bytes").color(NamedTextColor.GRAY));
            meta.lore(lore);

            item.setItemMeta(meta);
            items.add(item);
        }
        return items;
    }

    /**
     * 创建带名称的按钮物品（支持颜色代码）
     */
    private static ItemStack createButton(Material material, String nameRaw) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(nameRaw);
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 打开确认界面
     */
    public void openConfirmGUI(Player player, File selectedFile) {
        GUIState state = playerState.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage(lang.get(STATE_LOST_KEY));
            player.closeInventory();
            return;
        }
        state.selectedFile = selectedFile;

        // 解析标题
        Component title = LegacyComponentSerializer.legacyAmpersand().deserialize(lang.getRaw(CONFIRM_TITLE_KEY));
        Inventory inv = Bukkit.createInventory(null, CONFIRM_INV_SIZE, title);

        // 放置确认按钮（绿色玻璃）
        String CONFIRM_KEY = "gui.confirm";
        ItemStack confirm = createButton(Material.LIME_STAINED_GLASS_PANE, lang.getRaw(CONFIRM_KEY));
        for (int i = CONFIRM_START; i <= CONFIRM_END; i++) {
            inv.setItem(i, confirm);
        }

        // 放置取消按钮（红色玻璃）
        ItemStack cancel = createButton(Material.RED_STAINED_GLASS_PANE, lang.getRaw(CANCEL_KEY));
        for (int i = CANCEL_START; i <= CANCEL_END; i++) {
            inv.setItem(i, cancel);
        }

        ItemStack fileInfo = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = fileInfo.getItemMeta();
        String displayName = TextUtils.extractTitleFromFileName(selectedFile.getName());
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&e" + displayName));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(selectedFile.getName()).color(NamedTextColor.GRAY));
        lore.add(Component.text("Size: " + selectedFile.length() + " bytes").color(NamedTextColor.GRAY));
        meta.lore(lore);
        fileInfo.setItemMeta(meta);
        inv.setItem(4, fileInfo); // 中间槽位显示文件信息

        player.openInventory(inv);
    }

    /**
     * 处理库存点击事件（由主类注册监听器调用）
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        String title = event.getView().getTitle(); // 原始标题（可能含颜色代码）

        // 判断是否属于我们的 GUI（通过标题前缀，忽略颜色）
        String strippedTitle = ChatColor.stripColor(title);
        String mainStripped = ChatColor.stripColor(lang.getRaw(MAIN_TITLE_KEY));
        String confirmStripped = ChatColor.stripColor(lang.getRaw(CONFIRM_TITLE_KEY));

        if (!strippedTitle.startsWith(mainStripped) && !strippedTitle.startsWith(confirmStripped)) {
            return;
        }

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        GUIState state = playerState.get(player.getUniqueId());
        if (state == null) {
            player.sendMessage(lang.get(STATE_LOST_KEY));
            player.closeInventory();
            return;
        }

        if (strippedTitle.startsWith(mainStripped)) {
            handleMainClick(player, inv, event.getSlot(), state);
        } else {
            handleConfirmClick(player, event.getSlot(), state);
        }
    }

    private void handleMainClick(Player player, Inventory inv, int slot, GUIState state) {
        // 导航按钮区域
        if (slot >= 45) {
            if (slot == PREV_SLOT && inv.getItem(PREV_SLOT) != null) {
                openMainGUI(player, state.page - 1, state.charge, state.author);
            } else if (slot == NEXT_SLOT && inv.getItem(NEXT_SLOT) != null) {
                openMainGUI(player, state.page + 1, state.charge, state.author);
            } else if (slot == CLOSE_SLOT) {
                player.closeInventory();
                playerState.remove(player.getUniqueId());
            }
            return;
        }

        // 点击文件区域
        int index = state.page * ITEMS_PER_PAGE + slot;
        if (index < txtFiles.size()) {
            File file = txtFiles.get(index);
            openConfirmGUI(player, file);
        }
    }

    private void handleConfirmClick(Player player, int slot, GUIState state) {
        if (state.selectedFile == null) {
            player.closeInventory();
            return;
        }

        if (slot >= CONFIRM_START && slot <= CONFIRM_END) {
            // 确认
            player.closeInventory();
            // 调用主类的公共方法处理购买/打印
            BookPrinter pluginInstance = (BookPrinter) plugin;
            pluginInstance.processFileCommand(player, new String[]{state.selectedFile.getName(), state.author}, state.charge);
            playerState.remove(player.getUniqueId());
        } else if (slot >= CANCEL_START && slot <= CANCEL_END) {
            // 取消，返回主界面
            openMainGUI(player, state.page, state.charge, state.author);
        }
    }

    /**
     * 玩家状态内部类
     */
    private static class GUIState {
        int page;
        File selectedFile;
        boolean charge;
        String author;

        GUIState(int page, boolean charge, String author) {
            this.page = page;
            this.charge = charge;
            this.author = author;
        }
    }
}