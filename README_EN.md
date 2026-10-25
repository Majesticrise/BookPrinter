<div align="center">

[简体中文](README.md) | **English** | [Español](README_ES.md)

</div>

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Paper](https://img.shields.io/badge/Paper-1.20.1+-lightgrey.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-green.svg)](https://papermc.io/)

# BookPrinter 📖

Convert `.txt` files into written books on **Paper / Folia** servers (1.20.1 – 1.21.x, Java 17+).

> **What's new in v1.5:** a **pixel-perfect auto pagination engine** — text is laid out with the vanilla font's real character widths, completely fixing the issue of page content being silently cut off. Built on Folia's multi-threaded region scheduling; supports Paper / Folia / Purpur.

---

## 🎯 Pixel-Perfect Auto Pagination (New in v1.5)

Minecraft book pages hold exactly **114 pixels × 14 lines** — anything beyond is silently discarded by the client: no scrolling, no warning. On top of that, the vanilla font is proportional: `i`/`l`/punctuation are narrow, `M`/`W` are wide, full-width CJK characters are the widest, and bold adds 1px per character. Paginating by "character count" is therefore guaranteed to overflow.

BookPrinter now measures every character's real pixel width and mimics the client's own line-breaking behavior:

* Automatically adapts to spaces, symbols, narrow/wide letters, and mixed English/Chinese text
* English text prefers breaking at spaces; long CJK sentences hard-break per character (same as the client)
* Each page is filled to the physical 14-line limit; overflow flows onto the next page — **not a single character is lost**
* Formatting survives wraps and page splits — hex colors, gradients, bold/italic are restored at every break point

### Performance & Compatibility

* **Folia & Paper support** (multi-threaded region scheduling)
* **Async loading** — no main-thread lag
* **NIO.2 file handling** with multi-layer path security checks
* **Java 17+**, one jar for 1.20.1 – 1.21.x

---

## 🎨 Dual Parsing Modes

### 1. Modern Mode — *recommended*

Rich text layout powered by the MiniMessage engine.

* **RGB gradients**: `<gradient:#red:#blue>text</gradient>`
* **Hex colors**: `<#RRGGBB>` or `<color:#RRGGBB>`
* **All formats**: bold, italic, underline, strikethrough and more
* **Backward compatible**: traditional `&` color codes and `&#RRGGBB` still work
* **Auto pagination**: long sections between `\Line-break\` markers are split into properly sized pages

### 2. Classic Mode

* **Smart splitting**: pixel-width-based smart strategy that breaks at spaces and newlines
* **Multiple strategies**: `smart` / `lines` / `hard` / `marker`
* **Stable compatibility**: full support for traditional `&` codes and `&#RRGGBB` hex
* **Fallback switch**: set `classic.auto_fit: false` to restore the legacy character-count pagination

## 💰 Payment System

Sell books for money (Vault economy) or experience (levels/points).

## 🌍 Multi-language

Built-in 简体中文 / English / Español language files; custom languages supported.

---

## 📦 Installation

1. Download the latest `.jar`.
2. Drop it into your server's `/plugins` folder.
3. **Restart the server** (or hot-load with a plugin manager).
4. `config.yml` and language files are generated automatically.
5. Put your `.txt` files into `/plugins/bookprinter/` (the folder name matches the plugin name in `plugin.yml` — mind the casing on Linux).

---

## ⚙️ Configuration

```yaml
# Language: "zh_CN" (简体中文) / "en_US" (English) / "ES" (Español)
language: "en_US"

# Parsing mode: "classic" or "modern"
Switch-mode: "modern"

classic:
  # ★ Auto-fit (keep enabled): pixel-width wrapping + 14 lines per page.
  #   Adapts to spaces/punctuation/letter widths/CJK — no more cut-off pages.
  auto_fit: true
  # Page text width in pixels (vanilla = 114; lower it to 110 if a
  # resource pack changes the font)
  page_width_pixels: 114
  # Only used when auto_fit: false
  max_chars_per_page: 148
  split_strategy: "smart"     # smart / lines / hard / marker
  page_marker: "---PAGE---"
  max_lines_per_page: 14
  preserve_newlines: true
  trim_trailing_empty_pages: false

modern:
  trim_whitespace: false
  # ★ Auto pagination: long \Line-break\ sections are split at 114px × 14 lines
  auto_fit: true

# Payment (money requires Vault; exp does not)
payment:
  method: money          # money / exp
  exp-type: point        # point / level
  buy-price: 100.0
```

---

## 📖 Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/bp print <file> [author]` | `bookprinter.print` | Print a book for free |
| `/bp buy <file> [author]` | `bookprinter.buy` | Buy a book (charged) |
| `/bp gui` | `bookprinter.gui` | Open the file selection GUI |
| `/bp reload` | `bookprinter.reload` | Reload config and language files |
| `/bp info` | `bookprinter.info` | Show mode and plugin status |

Aliases: `/bookprinter`, `/bp`, `/printbook`

---

## ✍️ Writing Examples

### Modern Mode

```text
&lChapter 1: The Adventure Begins

This is a book about &l&6adventure&r.

&nNote:&r RGB gradients are supported!
This is a <gradient:#FF5555:#5555FF>rainbow gradient</gradient>.

(To force a new page...)
use the \Line-break\ marker!
This is the second page.
\n
(Use \n to force a line break)
```

### Classic Mode

```text
Content of the first page, &eyellow text&f.
---PAGE---
The page marker forces a page break here.
With classic.preserve_newlines enabled, blank lines are preserved too.
```

---

## 📜 License

This project is licensed under the [LGPL-3.0](LICENSE).
