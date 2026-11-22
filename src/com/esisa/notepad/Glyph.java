/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Tiny UI glyphs (back arrow, plus, search, folder, palette) painted in the component's foreground colour. */
final class Glyph implements Icon {

    enum Kind { BACK, PLUS, SEARCH, FOLDER, PALETTE, UP, DOWN }

    private final Kind kind;
    private final int size;

    Glyph(Kind kind, int size) {
        this.kind = kind;
        this.size = size;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        Ui.smooth(g);
        g.translate(x, y);
        g.scale(size / 16.0, size / 16.0);
        g.setColor(c != null && c.isEnabled() ? c.getForeground() : Ui.dim());
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case BACK -> {
                Path2D t = new Path2D.Double();
                t.moveTo(11, 3); t.lineTo(4, 8); t.lineTo(11, 13); t.closePath();
                g.draw(t);
            }
            case UP -> {
                Path2D v = new Path2D.Double();
                v.moveTo(3.5, 10.5); v.lineTo(8, 5.5); v.lineTo(12.5, 10.5);
                g.draw(v);
            }
            case DOWN -> {
                Path2D v = new Path2D.Double();
                v.moveTo(3.5, 5.5); v.lineTo(8, 10.5); v.lineTo(12.5, 5.5);
                g.draw(v);
            }
            case PLUS -> {
                g.draw(new Line2D.Double(8, 3, 8, 13));
                g.draw(new Line2D.Double(3, 8, 13, 8));
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(2.5, 2.5, 8, 8));
                g.draw(new Line2D.Double(9.4, 9.4, 13.5, 13.5));
            }
            case FOLDER -> {
                Path2D f = new Path2D.Double();
                f.moveTo(1.5, 4); f.lineTo(6, 4); f.lineTo(7.5, 5.5); f.lineTo(14.5, 5.5);
                f.lineTo(14.5, 13); f.lineTo(1.5, 13); f.closePath();
                g.draw(f);
            }
            case PALETTE -> { // "A" over a colour bar, like Word's font-colour button
                Path2D a = new Path2D.Double();
                a.moveTo(4, 11); a.lineTo(8, 1.5); a.lineTo(12, 11);
                g.draw(a);
                g.draw(new Line2D.Double(5.6, 7.5, 10.4, 7.5));
                g.setPaint(new java.awt.LinearGradientPaint(2, 0, 14, 0, new float[] {0f, 0.5f, 1f},
                        new java.awt.Color[] {new java.awt.Color(0xFF5A5A), new java.awt.Color(0xFFC247), new java.awt.Color(0x4F8CFF)}));
                g.fill(new RoundRectangle2D.Double(2, 12.6, 12, 2.6, 1.5, 1.5));
            }
        }
        g.dispose();
    }
}
