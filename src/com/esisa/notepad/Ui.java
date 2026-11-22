/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Map;

import javax.swing.UIManager;

/** Colours and fonts derived from the active theme, so every custom-painted part follows theme changes. */
final class Ui {

    private Ui() {}

    static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    /** Editor / content background. */
    static Color surface() { return color("TextPane.background", new Color(0x111D33)); }

    static Color text() { return color("TextPane.foreground", new Color(0xE3E9F5)); }

    static boolean dark() {
        Color s = surface();
        return (0.299 * s.getRed() + 0.587 * s.getGreen() + 0.114 * s.getBlue()) < 128;
    }

    /** Window chrome (tab row, status bar): a bit darker than the content. */
    static Color chrome() { return mix(surface(), Color.BLACK, dark() ? 0.28f : 0.06f); }

    static Color accent() {
        Color c = UIManager.getColor("Component.accentColor");
        if (c == null) c = UIManager.getColor("Component.focusColor");
        return c != null ? c : new Color(0x4F8CFF);
    }

    /** Secondary text. */
    static Color dim() { return mix(text(), surface(), 0.45f); }

    /** Subtle fill for pills, hovered tabs, cards. */
    static Color soft(float amount) { return mix(surface(), text(), amount); }

    static Color mix(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    static Font ui(float size, int style) {
        Font f = UIManager.getFont("defaultFont");
        if (f == null) f = new Font("Segoe UI", Font.PLAIN, 13);
        return f.deriveFont(style, size);
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map<?, ?> m) g.addRenderingHints(m);
        else g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
