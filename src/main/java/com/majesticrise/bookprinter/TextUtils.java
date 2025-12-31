package com.majesticrise.bookprinter;
import java.util.*;
import java.util.regex.Pattern;

public final class TextUtils {

    private static final boolean DEBUG = false;

    private TextUtils() {}

    public static List<String> splitToPagesSafe(String text, int maxChars, String strategy, String pageMarker, int maxLines, boolean preserveNewlines, boolean trimTrailingEmptyPages) {
        LinkedList<String> pages = new LinkedList<>();
        if (text == null) {
            pages.add("");
            return pages;
        }

        text = text.replace("\r\n", "\n").replace('\r', '\n');
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
                    if (sb.length() != 0) sb.append("\n");
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

        if (DEBUG) {
            int idx = 0;
            for (String pg : pages) debugPageInfo(idx++, pg);
        }

        return pages;
    }

    // 将 truncateByCodePoints 公开以供其它类使用
    public static String truncateByCodePoints(String s, int maxCodePoints) {
        if (s == null) return null;
        if (maxCodePoints <= 0) return "";
        int cp = s.codePointCount(0, s.length());
        if (cp <= maxCodePoints) return s;
        int endIndex = s.offsetByCodePoints(0, maxCodePoints);
        return s.substring(0, endIndex);
    }

    // 如果需要从文件名中提取标题（例如 "my_book_title.txt" -> "My Book Title"）
    public static String extractTitleFromFileName(String fileName) {
        if (fileName == null) return "";
        // 去掉目录路径
        int lastSep = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = (lastSep >= 0) ? fileName.substring(lastSep + 1) : fileName;
        // 去掉扩展名
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        // 替换下划线/破折号为空格，去除多余空白
        base = base.replace('_', ' ').replace('-', ' ').trim();
        // 规范化多个空格为单个空格
        base = base.replaceAll("\\s+", " ");
        // 首字母大写（每个单词）
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

// --------------- 其它辅助方法保持 package-private（private） ---------------

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

    private static String safeSubstringByCodePoints(String s, int beginCpIndex, int endCpIndex) {
        if (s == null) return null;
        int totalCp = s.codePointCount(0, s.length());
        if (beginCpIndex < 0) beginCpIndex = 0;
        if (endCpIndex > totalCp) endCpIndex = totalCp;
        if (beginCpIndex >= endCpIndex) return "";
        int beginCharIndex = s.offsetByCodePoints(0, beginCpIndex);
        int endCharIndex = s.offsetByCodePoints(0, endCpIndex);
        return s.substring(beginCharIndex, endCharIndex);
    }

    private static boolean containsColorCodes(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.indexOf('§') >= 0;
    }

    private static void debugPageInfo(int idx, String page) {
        int cp = (page == null) ? 0 : page.codePointCount(0, page.length());
        boolean hasColor = containsColorCodes(page);
        String snippet = page;
        if (snippet != null && snippet.length() > 120) snippet = snippet.substring(0, 120) + "...";
        System.out.printf("PAGE %d: cp=%d, color=%s, text=\"%s\"%n", idx, cp, hasColor, snippet);
    }
}