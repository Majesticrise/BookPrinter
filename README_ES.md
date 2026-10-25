<div align="center">

[简体中文](README.md) | [English](README_EN.md) | **Español**

</div>

[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Paper](https://img.shields.io/badge/Paper-1.20.1+-lightgrey.svg)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-green.svg)](https://papermc.io/)

# BookPrinter 📖

Convierte archivos `.txt` en libros escritos en servidores **Paper / Folia** (1.20.1 – 1.21.x, Java 17+).

> **Novedades de la v1.5:** motor de **paginación automática píxel-perfecta** — el texto se maqueta con los anchos reales de la fuente vanilla, solucionando por completo el problema del contenido cortado en las páginas. Basado en la programación multihilo por regiones de Folia; compatible con Paper / Folia / Purpur.

---

## 🎯 Paginación automática píxel-perfecta (Novedad v1.5)

Las páginas de los libros de Minecraft miden exactamente **114 píxeles de ancho × 14 líneas** — todo lo que sobrepase es descartado silenciosamente por el cliente: sin scroll ni aviso. Además, la fuente vanilla es proporcional: `i`/`l`/puntuación son estrechas, `M`/`W` son anchas, los caracteres CJK de ancho completo son los más anchos, y la negrita añade 1px por carácter. Paginar por "número de caracteres" desborda la página.

BookPrinter ahora mide el ancho real en píxeles de cada carácter y simula el comportamiento de salto de línea del propio cliente:

* Se adapta automáticamente a espacios, símbolos, letras estrechas/anchas y texto mixto inglés/chino
* El texto en inglés prefiere cortar en los espacios; las frases largas en CJK se cortan carácter a carácter (igual que el cliente)
* Cada página se llena hasta el límite físico de 14 líneas; el exceso pasa a la página siguiente — **no se pierde ni un solo carácter**
* El formato sobrevive a los saltos: colores hex, degradados, negrita/cursiva se restauran en cada corte

### Rendimiento y compatibilidad

* **Soporte para Folia y Paper** (programación multihilo por regiones)
* **Carga asíncrona** — sin lag en el hilo principal
* **Manejo de archivos NIO.2** con múltiples capas de seguridad de rutas
* **Java 17+**, un solo jar para 1.20.1 – 1.21.x

---

## 🎨 Doble modo de análisis

### 1. Modern Mode (modo moderno) — *recomendado*

Maquetación de texto enriquecido con el motor MiniMessage.

* **Degradados RGB**: `<gradient:#red:#blue>texto</gradient>`
* **Colores hex**: `<#RRGGBB>` o `<color:#RRGGBB>`
* **Todos los formatos**: negrita, cursiva, subrayado, tachado y más
* **Retrocompatible**: los códigos de color tradicionales `&` y `&#RRGGBB` siguen funcionando
* **Paginación automática**: las secciones largas entre marcadores `\Line-break\` se dividen en páginas del tamaño correcto

### 2. Classic Mode (modo clásico)

* **División inteligente**: estrategia smart basada en anchos de píxel que corta en espacios y saltos de línea
* **Múltiples estrategias**: `smart` / `lines` / `hard` / `marker`
* **Compatibilidad estable**: soporte completo de los códigos `&` tradicionales y hex `&#RRGGBB`
* **Interruptor de retroceso**: con `classic.auto_fit: false` vuelves a la paginación antigua por número de caracteres

## 💰 Sistema de pago

Vende libros por dinero (economía de Vault) o experiencia (niveles/puntos).

## 🌍 Multiidioma

Archivos de idioma incluidos: 简体中文 / English / Español; se pueden añadir idiomas personalizados.

---

## 📦 Instalación

1. Descarga el `.jar` más reciente.
2. Colócalo en la carpeta `/plugins` de tu servidor.
3. **Reinicia el servidor** (o cárgalo en caliente con un gestor de plugins).
4. `config.yml` y los archivos de idioma se generan automáticamente.
5. Coloca tus archivos `.txt` en `/plugins/bookprinter/` (el nombre de la carpeta coincide con el del plugin en `plugin.yml` — cuida las mayúsculas en Linux).

---

## ⚙️ Configuración

```yaml
# Idioma: "zh_CN" (简体中文) / "en_US" (English) / "ES" (Español)
language: "ES"

# Modo de análisis: "classic" o "modern"
Switch-mode: "modern"

classic:
  # ★ Auto-fit (mantenlo activado): ajuste por ancho de píxel + 14 líneas por página.
  #   Se adapta a espacios/puntuación/anchos de letras/CJK — adiós a las páginas cortadas.
  auto_fit: true
  # Ancho del área de texto de la página en píxeles (vanilla = 114;
  # bájalo a 110 si un paquete de recursos cambia la fuente)
  page_width_pixels: 114
  # Solo se usa con auto_fit: false
  max_chars_per_page: 148
  split_strategy: "smart"     # smart / lines / hard / marker
  page_marker: "---PAGE---"
  max_lines_per_page: 14
  preserve_newlines: true
  trim_trailing_empty_pages: false

modern:
  trim_whitespace: false
  # ★ Paginación automática: las secciones largas se dividen a 114px × 14 líneas
  auto_fit: true

# Pago (money requiere Vault; exp no)
payment:
  method: money          # money / exp
  exp-type: point        # point / level
  buy-price: 100.0
```

---

## 📖 Comandos

| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/bp print <archivo> [autor]` | `bookprinter.print` | Imprimir un libro gratis |
| `/bp buy <archivo> [autor]` | `bookprinter.buy` | Comprar un libro (de pago) |
| `/bp gui` | `bookprinter.gui` | Abrir el selector de archivos |
| `/bp reload` | `bookprinter.reload` | Recargar configuración e idiomas |
| `/bp info` | `bookprinter.info` | Ver el modo y el estado del plugin |

Alias: `/bookprinter`, `/bp`, `/printbook`

---

## ✍️ Ejemplos de escritura

### Modern Mode

```text
&lCapítulo 1: Comienza la aventura

Este es un libro sobre &l&6aventura&r.

&nAtención:&r ¡se admiten degradados RGB!
Esto es un <gradient:#FF5555:#5555FF>degradado arcoíris</gradient>.

(Si quieres forzar una página nueva...)
¡usa el marcador \Line-break\!
Esta es la segunda página.
\n
(Usa \n para forzar un salto de línea)
```

### Classic Mode

```text
Contenido de la primera página, &etexto amarillo&f.
---PAGE---
El marcador fuerza aquí un salto de página.
Con classic.preserve_newlines activado, las líneas en blanco también se conservan.
```

---

## 📜 Licencia

Este proyecto está bajo la licencia [LGPL-2.1](LICENSE).
