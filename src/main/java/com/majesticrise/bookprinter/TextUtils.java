package com.majesticrise.bookprinter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.regex.Pattern;

public final class TextUtils {

    private TextUtils() {}

    // ============================================
    // 原版书本页面的物理限制（客户端渲染规则）
    // ============================================
    // 书本页面文本区宽度为 114 像素，每页最多渲染 14 行（行高 9px）。
    // 超出 14 行或单行超出 114px 的内容会被客户端直接裁剪，不显示也不滚动。
    public static final int BOOK_PAGE_WIDTH = 114;
    public static final int BOOK_MAX_LINES = 14;

    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MODERN_LINE_BREAK = Pattern.compile("\\\\Line-break\\\\");
    private static final Pattern LEGACY_COLOR_SHORT = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    // 支持 §x§R§R§G§G§B§B 十六进制形式的序列化/反序列化（渐变色需要逐字符颜色）
    private static final LegacyComponentSerializer LEGACY_HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Map<String, String> LEGACY_MAP = Map.ofEntries(
            Map.entry("0", "<black>"), Map.entry("1", "<dark_blue>"),
            Map.entry("2", "<dark_green>"), Map.entry("3", "<dark_aqua>"),
            Map.entry("4", "<dark_red>"), Map.entry("5", "<dark_purple>"),
            Map.entry("6", "<gold>"), Map.entry("7", "<gray>"),
            Map.entry("8", "<dark_gray>"), Map.entry("9", "<blue>"),
            Map.entry("a", "<green>"), Map.entry("b", "<aqua>"),
            Map.entry("c", "<red>"), Map.entry("d", "<light_purple>"),
            Map.entry("e", "<yellow>"), Map.entry("f", "<white>"),
            Map.entry("k", "<obfuscated>"), Map.entry("l", "<bold>"),
            Map.entry("m", "<strikethrough>"), Map.entry("n", "<underline>"),
            Map.entry("o", "<italic>"), Map.entry("r", "<reset>")
    );

    // ============================================
    // 像素排版引擎：按原版字体宽度换行、按 14 行分页
    // ============================================

    /**
     * 单个字符的渲染宽度（像素，含 1px 字间距），按原版默认字体近似。
     * 原版字体是比例字体：i/l/标点很窄，M/W 较宽，中文等全角字符最宽。
     * 不确定宽度的字符一律往宽了估，宁可提前换行也不会超页。
     */
    private static int baseCharWidth(char c) {
        if (c < 32) return 0;
        if (c == ' ') return 4;
        if (c < 127) {
            switch (c) {
                // 窄字符（1px 字形）
                case '!': case '\'': case ',': case '.': case ':': case ';':
                case 'i': case 'l': case '|': case '`':
                    return 2;
                // 半窄字符（3px 字形）
                case '"': case '(': case ')': case '*': case '/': case 'I':
                case 'f': case 't': case '[': case ']': case '{': case '}': case '\\':
                    return 4;
                // 数字与部分符号（4px 字形）
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    return 5;
                // 最宽的大写字母（6px 字形）
                case 'M': case 'W':
                    return 7;
                // 其余 ASCII 字母/符号按 5px 字形处理
                default:
                    return 6;
            }
        }
        if (isWideGlyph(c)) return 9;
        return 6; // 西里尔、希腊等其他 Unicode 字符
    }

    /** CJK 等全角字符在原版字体中为 8px 字形 + 1px 间距 = 9px */
    private static boolean isWideGlyph(char c) {
        return (c >= 0x1100 && c <= 0x115F)    // Hangul Jamo
                || (c >= 0x2E80 && c <= 0xA4CF) // CJK 部首/符号/注音/平假名/片假名
                || (c >= 0xA960 && c <= 0xA97F)
                || (c >= 0xAC00 && c <= 0xD7A3) // Hangul 音节
                || (c >= 0xF900 && c <= 0xFAFF) // CJK 兼容表意
                || (c >= 0xFE10 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60) // 全角形式
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    /**
     * 计算含 § 颜色代码字符串的渲染像素宽度。
     * 颜色代码本身不占宽度，粗体（§l）使每个字符 +1px。
     */
    public static int legacyWidth(String s) {
        int width = 0;
        boolean bold = false;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if (code == 'x') {
                    i = Math.min(s.length(), i + 14); // §x + 6 组 §h 共 14 字符
                    continue;
                }
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) bold = false;
                else if (code == 'l') bold = true;
                else if (code == 'r') bold = false;
                i += 2;
                continue;
            }
            width += baseCharWidth(c) + (bold ? 1 : 0);
            i++;
        }
        return width;
    }

    /**
     * 排版用的词法单元。prefix 是该单元开始前的完整格式状态
     * （颜色 + 粗斜体等），换行后用它恢复格式，避免断行丢色。
     */
    private record Token(String text, int width, String prefix, boolean newline) {}

    /** 把含 § 代码和 \n 的文本切分为排版单元，并记录每个单元的格式状态 */
    private static List<Token> tokenizeLegacy(String text) {
        List<Token> tokens = new ArrayList<>();
        String color = "";
        StringBuilder formats = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            String state = color + formats;
            if (c == '\n') {
                tokens.add(new Token("\n", 0, state, true));
                i++;
                continue;
            }
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if (code == 'x') {
                    int end = Math.min(text.length(), i + 14);
                    String seq = text.substring(i, end);
                    tokens.add(new Token(seq, 0, state, false));
                    if (end - i == 14) {
                        color = seq;
                        formats.setLength(0);
                    }
                    i = end;
                    continue;
                }
                String seq = text.substring(i, Math.min(text.length(), i + 2));
                tokens.add(new Token(seq, 0, state, false));
                // 状态统一按小写归一化存储，兼容用户手写大写码（如 &L）；
                // 输出仍用原文 seq，保证不影响反序列化
                String norm = "§" + code;
                switch (code) {
                    case 'k', 'l', 'm', 'n', 'o' -> {
                        if (formats.indexOf(norm) < 0) formats.append(norm);
                    }
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                         'a', 'b', 'c', 'd', 'e', 'f' -> {
                        color = norm;
                        formats.setLength(0);
                    }
                    case 'r' -> {
                        color = "";
                        formats.setLength(0);
                    }
                    default -> {}
                }
                i += 2;
                continue;
            }
            boolean bold = formats.indexOf("§l") >= 0;
            tokens.add(new Token(String.valueOf(c), baseCharWidth(c) + (bold ? 1 : 0), state, false));
            i++;
        }
        return tokens;
    }

    /**
     * 按像素宽度把文本（可含 \n）断成若干视觉行。
     * wordWrap=true 时优先在空格处断行（与客户端行为一致），
     * 没有空格（如中文长句）则逐字符硬断；断行后自动恢复格式状态。
     */
    public static List<String> wrapLegacyToLines(String text, int maxWidth, boolean wordWrap) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty()) {
            out.add("");
            return out;
        }
        List<Token> tokens = tokenizeLegacy(text);
        int n = tokens.size();
        int i = 0;
        while (i < n) {
            // 断行续行时先恢复断点处的格式状态（颜色不丢失）
            StringBuilder cur = new StringBuilder(i == 0 ? "" : tokens.get(i).prefix());
            int w = 0;
            int breakToken = -1;  // 最后一个空格之后的单元索引（可断点）
            int breakLen = 0;     // 可断点对应的行文本长度
            int hardStop = -1;    // 溢出的可见单元索引
            int hardLen = 0;      // 溢出前行的文本长度
            boolean forcedBreak = false;
            int j = i;
            while (j < n) {
                Token t = tokens.get(j);
                if (t.newline()) {
                    forcedBreak = true;
                    break;
                }
                int lenBefore = cur.length();
                cur.append(t.text());
                if (t.width() > 0) {
                    if (w + t.width() > maxWidth && w > 0) {
                        hardStop = j;
                        hardLen = lenBefore;
                        break;
                    }
                    w += t.width();
                    if (wordWrap && " ".equals(t.text())) {
                        breakToken = j + 1;
                        breakLen = cur.length();
                    }
                }
                j++;
            }
            if (forcedBreak) {
                out.add(cur.toString());
                i = j + 1;
            } else if (hardStop >= 0 && wordWrap && breakToken > i) {
                out.add(cur.substring(0, breakLen));
                i = breakToken;
            } else if (hardStop >= 0) {
                out.add(cur.substring(0, hardLen));
                i = hardStop;
            } else {
                out.add(cur.toString());
                break;
            }
        }
        return out;
    }

    /**
     * 把视觉行按每页 maxLines 行合并成页（自动适应不同字符宽度后，
     * 真正决定分页的是"行数"这个客户端硬限制）。
     */
    private static List<String> paginateVisualLines(List<String> visualLines, int maxLines,
                                                    boolean preserveNewlines) {
        List<String> pages = new ArrayList<>();
        for (int start = 0; start < visualLines.size(); start += maxLines) {
            int end = Math.min(start + maxLines, visualLines.size());
            List<String> chunk = new ArrayList<>(visualLines.subList(start, end));
            if (!preserveNewlines) {
                while (!chunk.isEmpty() && chunk.get(chunk.size() - 1).isEmpty()) chunk.remove(chunk.size() - 1);
            }
            pages.add(String.join("\n", chunk));
        }
        return pages;
    }

    /**
     * classic 模式的自动排版分页：按像素换行 + 按 14 行分页。
     * strategy: "marker" 先按分页标记切分再排版；"hard" 只按宽度硬断不找空格；
     * 其余（smart/lines）按空格智能断行。
     */
    private static List<String> autoFitPages(String legacyText, String strategy, String pageMarker,
                                             int maxLines, int pageWidth,
                                             boolean preserveNewlines, boolean trimTrailingEmptyPages) {
        boolean useMarker = "marker".equals(strategy) && pageMarker != null && !pageMarker.isEmpty();
        boolean wordWrap = !"hard".equals(strategy);
        String[] segments = useMarker ? legacyText.split(Pattern.quote(pageMarker), -1)
                : new String[]{legacyText};
        List<String> pages = new ArrayList<>();
        for (String seg : segments) {
            List<String> visual = wrapLegacyToLines(seg, pageWidth, wordWrap);
            pages.addAll(paginateVisualLines(visual, maxLines, preserveNewlines));
        }
        if (trimTrailingEmptyPages) {
            while (!pages.isEmpty() && pages.get(pages.size() - 1).isEmpty()) pages.remove(pages.size() - 1);
        }
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    // ============================================
    // 经典模式
    // ============================================

    public static List<Component> parseClassicMode(String rawText, ConfigurationSection config) {
        ConfigurationSection classic = config.getConfigurationSection("classic");
        int maxChars = (classic != null) ? classic.getInt("max_chars_per_page", 165) : 165;
        String strategy = (classic != null) ? classic.getString("split_strategy", "smart") : "smart";
        String pageMarker = (classic != null) ? classic.getString("page_marker", "---PAGE---") : "---PAGE---";
        int maxLines = (classic != null) ? classic.getInt("max_lines_per_page", 14) : 14;
        boolean preserveNewlines = classic == null || classic.getBoolean("preserve_newlines", true);
        boolean trimEmptyPages = classic != null && classic.getBoolean("trim_trailing_empty_pages", false);
        boolean autoFit = classic == null || classic.getBoolean("auto_fit", true);
        int pageWidth = (classic != null) ? classic.getInt("page_width_pixels", BOOK_PAGE_WIDTH) : BOOK_PAGE_WIDTH;

        rawText = rawText.replace("\r", "");

        rawText = ChatColor.translateAlternateColorCodes('&', rawText);

        String hexProcessed = convertHexTags(rawText);

        List<String> pageTexts;
        if (autoFit) {
            // 自动排版：以客户端的物理限制为准（最多 14 行 / 114px 宽），
            // max_chars_per_page 在此模式下不再参与计算。
            int effLines = Math.min(Math.max(1, maxLines), BOOK_MAX_LINES);
            int effWidth = Math.min(Math.max(16, pageWidth), BOOK_PAGE_WIDTH);
            pageTexts = autoFitPages(hexProcessed, strategy, pageMarker, effLines, effWidth, preserveNewlines, trimEmptyPages);
        } else {
            pageTexts = splitToPagesSafe(hexProcessed, maxChars, strategy, pageMarker, maxLines, preserveNewlines, trimEmptyPages);
        }

        List<Component> pages = new ArrayList<>();
        for (String text : pageTexts) {
            pages.add(LEGACY_SERIALIZER.deserialize(text));
        }
        return pages;
    }

    // ============================================
    // 现代模式
    // ============================================

    public static List<Component> parseModernMode(String rawText, ConfigurationSection config) {
        List<Component> pages = new ArrayList<>();

        boolean trimWhitespace = config.getBoolean("modern.trim_whitespace", false);
        boolean autoFit = config.getBoolean("modern.auto_fit", true);
        if (trimWhitespace) {
            rawText = rawText.trim();
        }

        rawText = rawText.replace("\r", "");

        rawText = rawText.replaceAll("&#([A-Fa-f0-9]{6})", "<color:#$1>");

        for (Map.Entry<String, String> entry : LEGACY_MAP.entrySet()) {
            rawText = rawText.replace("&" + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : LEGACY_MAP.entrySet()) {
            rawText = rawText.replace("&" + entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        }

        String[] rawPages = MODERN_LINE_BREAK.split(rawText, -1);

        for (String pageContent : rawPages) {
            String cleanContent = pageContent.trim();

            if (cleanContent.isEmpty()) {
                pages.add(Component.empty());
                continue;
            }

            cleanContent = cleanContent.replace("\\n", "\n").replace("\\\\n", "\n");

            Component pageComponent;
            try {
                pageComponent = MINI_MESSAGE.deserialize(cleanContent);
            } catch (Exception e) {
                pageComponent = Component.text(cleanContent);
            }

            if (!autoFit) {
                pages.add(pageComponent);
                continue;
            }

            // 自动排版：组件 -> §legacy 字符串 -> 按像素换行 + 14 行分页 -> 组件
            // 渐变色会被序列化为逐字符 §x 形式，断行后颜色由排版引擎恢复，不会丢色。
            // 若该段未超出一页（≤14 行），保留原始组件，
            // 避免 legacy 往返丢失 <click>/<hover> 等 MiniMessage 高级功能。
            String legacy = LEGACY_HEX_SERIALIZER.serialize(pageComponent);
            List<String> visual = wrapLegacyToLines(legacy, BOOK_PAGE_WIDTH, true);
            if (visual.size() <= BOOK_MAX_LINES) {
                pages.add(pageComponent);
                continue;
            }
            for (String pageText : paginateVisualLines(visual, BOOK_MAX_LINES, true)) {
                pages.add(LEGACY_HEX_SERIALIZER.deserialize(pageText));
            }
        }
        return pages;
    }

    private static String convertHexTags(String text) {
        if (text == null || !text.contains("&#")) return text;
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = LEGACY_HEX_PATTERN.matcher(text);
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder hexFormat = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                hexFormat.append("§").append(c);
            }
            m.appendReplacement(sb, hexFormat.toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static List<String> splitToPagesSafe(String text, int maxChars, String strategy, String pageMarker, int maxLines, boolean preserveNewlines, boolean trimTrailingEmptyPages) {
        LinkedList<String> pages = new LinkedList<>();
        if (text == null) {
            pages.add("");
            return pages;
        }

        if (maxChars <= 0) maxChars = 1;
        final int thresholdCp = Math.max(3, maxChars / 8);
        String usedStrategy = (strategy == null) ? "smart" : strategy;

        switch (usedStrategy) {
            case "marker": {
                if (pageMarker == null || pageMarker.isEmpty()) {
                    pages.addAll(splitToPagesSafe(text, maxChars, "smart", pageMarker, maxLines, preserveNewlines, trimTrailingEmptyPages));
                    break;
                }
                String[] segments = text.split(Pattern.quote(pageMarker), -1);
                for (String seg : segments) {
                    if (seg.isEmpty()) {
                        pages.add("");
                        continue;
                    }
                    List<String> segPages = splitToPagesSafe(seg, maxChars, "smart", pageMarker, maxLines, preserveNewlines, trimTrailingEmptyPages);
                    pages.addAll(segPages);
                }
                break;
            }

            case "lines": {
                if (maxLines <= 0) maxLines = 1;
                String[] lines = text.split("\n", -1);
                StringBuilder sb = new StringBuilder();
                int lineCount = 0;
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(line);
                    lineCount++;
                    boolean isLastLine = (i == lines.length - 1);
                    if (lineCount >= maxLines || isLastLine) {
                        String pageText = sb.toString();
                        if (pageText.codePointCount(0, pageText.length()) <= maxChars) {
                            String finalPage = preserveNewlines ? pageText : trimTrailingNewlines(pageText);
                            finalPage = truncateByCodePoints(finalPage, maxChars);
                            pages.add(finalPage);
                        } else {
                            pages.addAll(splitToPagesSafe(pageText, maxChars, "smart", pageMarker, maxLines, preserveNewlines, trimTrailingEmptyPages));
                        }
                        sb.setLength(0);
                        lineCount = 0;
                    }
                }
                break;
            }

            case "hard": {
                int pos = 0;
                int len = text.length();
                while (pos < len) {
                    int remainingCp = text.codePointCount(pos, len);
                    int takeCp = Math.min(maxChars, remainingCp);
                    int end = text.offsetByCodePoints(pos, takeCp);
                    String piece = text.substring(pos, end);
                    String finalPiece = preserveNewlines ? piece : trimTrailingNewlines(piece);
                    finalPiece = truncateByCodePoints(finalPiece, maxChars);
                    pages.add(finalPiece);
                    pos = end;
                }
                break;
            }

            case "smart":
            default: {
                if (text.isEmpty()) {
                    pages.add("");
                    break;
                }
                int p = 0;
                int l = text.length();
                while (p < l) {
                    int remainingCp = text.codePointCount(p, l);
                    int takeCp = Math.min(maxChars, remainingCp);
                    int end = text.offsetByCodePoints(p, takeCp);

                    if (end >= l) {
                        String last = text.substring(p, l);
                        String finalLast = preserveNewlines ? last : trimTrailingNewlines(last);
                        finalLast = truncateByCodePoints(finalLast, maxChars);
                        pages.add(finalLast);
                        break;
                    }

                    int lastNewline = lastIndexOfChar(text, '\n', end - 1);
                    if (lastNewline >= p) {
                        int gapCp = text.codePointCount(lastNewline + 1, end);
                        if (gapCp <= thresholdCp) {
                            String part = text.substring(p, lastNewline + 1);
                            String finalPart = preserveNewlines ? part : trimTrailingNewlines(part);
                            finalPart = truncateByCodePoints(finalPart, maxChars);
                            pages.add(finalPart);
                            p = lastNewline + 1;
                            continue;
                        }
                    }

                    int lastSpace = lastIndexOfChar(text, ' ', end - 1);
                    if (lastSpace >= p) {
                        int gapCp = text.codePointCount(lastSpace + 1, end);
                        if (gapCp <= thresholdCp) {
                            String part = text.substring(p, lastSpace + 1);
                            String finalPart = preserveNewlines ? part : trimTrailingNewlines(part);
                            finalPart = truncateByCodePoints(finalPart, maxChars);
                            pages.add(finalPart);
                            p = lastSpace + 1;
                            continue;
                        }
                    }

                    String part = text.substring(p, end);
                    String finalPart = preserveNewlines ? part : trimTrailingNewlines(part);
                    finalPart = truncateByCodePoints(finalPart, maxChars);
                    pages.add(finalPart);
                    p = end;
                }
                break;
            }
        }

        if (trimTrailingEmptyPages) {
            while (!pages.isEmpty() && pages.getLast().isEmpty()) pages.removeLast();
            if (pages.isEmpty()) pages.add("");
        }

        return pages;
    }

    public static String truncateByCodePoints(String s, int maxCodePoints) {
        if (s == null) return null;
        if (maxCodePoints <= 0) return "";
        int cp = s.codePointCount(0, s.length());
        if (cp <= maxCodePoints) return s;
        int endIndex = s.offsetByCodePoints(0, maxCodePoints);
        return s.substring(0, endIndex);
    }

    public static String extractTitleFromFileName(String fileName) {
        if (fileName == null) return "";
        int lastSep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = (lastSep >= 0) ? fileName.substring(lastSep + 1) : fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replace('_', ' ').replace('-', ' ').trim();
        base = base.replaceAll("\\s+", " ");
        StringBuilder sb = new StringBuilder();
        String[] parts = base.split(" ");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            int cp0 = p.codePointAt(0);
            int firstCharLen = Character.charCount(cp0);
            String firstChar = p.substring(0, firstCharLen);
            String rest = (p.length() > firstCharLen) ? p.substring(firstCharLen) : "";
            sb.append(firstChar.toUpperCase()).append(rest.toLowerCase());
            if (i < parts.length - 1) sb.append(' ');
        }
        String title = sb.toString().trim();
        if (title.isEmpty()) title = "Book";
        return title;
    }

    private static int lastIndexOfChar(String s, char c, int fromIndexInclusive) {
        if (s == null || s.isEmpty()) return -1;
        if (fromIndexInclusive >= s.length()) fromIndexInclusive = s.length() - 1;
        if (fromIndexInclusive < 0) return -1;
        for (int i = fromIndexInclusive; i >= 0; i--) {
            if (s.charAt(i) == c) return i;
        }
        return -1;
    }

    private static String trimTrailingNewlines(String s) {
        if (s == null || s.isEmpty()) return s;
        int end = s.length();
        while (end > 0) {
            char ch = s.charAt(end - 1);
            if (ch == '\n' || ch == '\r') end--;
            else break;
        }
        if (end == s.length()) return s;
        return s.substring(0, end);
    }
}
