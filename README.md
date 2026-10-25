<div align="center">

**简体中文** | [English](README_EN.md) | [Español](README_ES.md)

</div>

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Paper](https://img.shields.io/badge/Paper-1.20.1+-lightgrey.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-green.svg)](https://papermc.io/)

# BookPrinter 📖

支持 **Paper / Folia** 服务器（1.20.1 – 1.21.x，Java 17+）的 TXT 文本转成书物品插件。

> **v1.5 更新**：新增 **像素级自动分页引擎** —— 按原版字体真实宽度排版，彻底修复每页内容超出被裁剪不显示的问题。基于 Folia 多线程区域调度，支持 Paper / Folia / Purpur。

---

## 🎯 像素级自动分页（v1.5 新增）

Minecraft 书本页面物理上只有 **114 像素宽 × 14 行高**，超出部分客户端直接裁剪、不显示也无法滚动。而原版字体是"比例字体"——`i`/`l`/标点很窄，`M`/`W` 较宽，中文全角字符最宽，粗体每字 +1px——所以按"字符个数"分页必然溢出。

BookPrinter 现在按每个字符的真实像素宽度排版，并模拟客户端的断行行为：

* 自动适应空格、符号、宽窄字母、中英文混排
* 英文优先在空格处断行，中文长句逐字硬断（与客户端行为一致）
* 每页严格填满 14 行的物理上限，溢出内容自动流入下一页，**一个字都不会丢**
* 颜色代码跨行、跨页不丢失（十六进制色、渐变、粗斜体在断行处自动恢复）

### 性能与兼容

* **Folia & Paper 支持**（多线程区域调度）
* **异步加载**，不卡主线程
* **NIO.2 文件处理**，多层路径安全校验
* **Java 17+**，单 jar 兼容 1.20.1 – 1.21.x

---

## 🎨 双模式渲染引擎

### 1. Modern Mode (现代模式) — *推荐*

专为排版设计，采用 MiniMessage 格式引擎。

* **RGB 渐变色**：支持 `<gradient:#red:#blue>文字</gradient>`
* **Hex 颜色**：支持 `<#RRGGBB>` 或 `<color:#RRGGBB>`
* **全格式支持**：加粗、斜体、下划线、删除线等所有标准格式
* **兼容并包**：依然识别传统的 `&` 颜色代码和 `&#RRGGBB`
* **自动分页**：`\Line-break\` 之间的超长段落自动拆页，不再溢出

### 2. Classic Mode (经典模式)

* **智能分页**：基于像素宽度的 Smart Split 策略，自动识别空格和换行符断页
* **多种策略**：`smart`(智能) / `lines`(按行) / `hard`(强制) / `marker`(标记符)
* **稳定兼容**：完美支持传统的 `&` 颜色代码和 `&#RRGGBB` Hex
* **回退开关**：`classic.auto_fit: false` 可切回旧的字符数分页模式

## 💰 支付系统

支持金币（Vault 经济）或经验（等级/点数）购买书本。

## 🌍 多语言

内置简体中文 / English / Español 语言包，支持自定义语言文件。

---

## 📦 安装

1. 下载最新版本的 `.jar` 文件。
2. 放入服务器的 `/plugins` 文件夹。
3. **重启服务器**（或使用插件管理器热加载）。
4. 配置文件 `config.yml` 和语言文件将自动生成。
5. 将 `.txt` 文本文件放入 `/plugins/bookprinter/` 目录（文件夹名与 `plugin.yml` 中的插件名一致，Linux 服务器注意大小写）。

---

## ⚙️ 配置

```yaml
# 语言设置: "zh_CN" (简体中文) / "en_US" (English) / "ES" (Español)
language: "zh_CN"

# 模式切换: "classic" 或 "modern"
Switch-mode: "modern"

classic:
  # ★ 自动排版（推荐保持开启）：像素宽度换行 + 14 行分页，
  #   自动适应空格/标点/宽窄字母/中文，杜绝内容超出被裁剪
  auto_fit: true
  # 页面文本区像素宽度（原版 114；材质包改字体的服务器可调小到 110 留边距）
  page_width_pixels: 114
  # 仅 auto_fit: false 时生效
  max_chars_per_page: 148
  split_strategy: "smart"     # smart / lines / hard / marker
  page_marker: "---PAGE---"
  max_lines_per_page: 14
  preserve_newlines: true
  trim_trailing_empty_pages: false

modern:
  trim_whitespace: false
  # ★ 自动分页：\Line-break\ 之间的超长段落按 114px × 14 行自动拆页
  auto_fit: true

# 支付设置（money 需 Vault；exp 无需）
payment:
  method: money          # money / exp
  exp-type: point        # point / level
  buy-price: 100.0
```

---

## 📖 使用指南

| 指令 | 权限 | 说明 |
| :--- | :--- | :--- |
| `/bp print <文件名> [署名]` | `bookprinter.print` | 免费打印书本 |
| `/bp buy <文件名> [署名]` | `bookprinter.buy` | 付费购买书本 |
| `/bp gui` | `bookprinter.gui` | 打开文件选择界面 |
| `/bp reload` | `bookprinter.reload` | 重载配置和语言文件 |
| `/bp info` | `bookprinter.info` | 查看运行模式和状态 |

别名：`/bookprinter`、`/bp`、`/printbook`

---

## ✍️ 写作示例

### Modern Mode（现代模式）

```text
&l第一章：冒险的开始

这是一本关于 &l&6冒险&r 的书。

&n注意：&r本书支持 RGB 渐变色！
这就是 <gradient:#FF5555:#5555FF>彩虹渐变</gradient> 的效果。

(如果你想换页...)
就使用 \Line-break\ 符号！
这里是第二页的内容。
\n
(使用 \n 符号强制换行)
```

### Classic Mode（经典模式）

```text
第一页的内容，&e黄色文字&f。
---PAGE---
这里强制插入了一个分页符，内容会进入下一页。
如果开启了 classic.preserve_newlines，这里的空行也会被保留。
```

---

## 📜 许可证

本项目采用 [LGPL-3.0](LICENSE) 许可证。
