/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Small language logos drawn as vectors (crisp at any size / zoom). Designed on a 16x16 grid. */
final class LangIcon implements Icon {

    private final Language lang;
    private final int size;

    LangIcon(Language lang, int size) {
        this.lang = lang;
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
        switch (lang) {
            case PYTHON -> python(g);
            case JAVA -> java(g);
            case CSHARP -> hexagon(g, new Color(0x9B4F96), "C#", 6.4f);
            case CPP -> hexagon(g, new Color(0x0B6CC2), "C++", 5.0f);
            case C -> hexagon(g, new Color(0x5C7CBA), "C", 7.5f);
            case JAVASCRIPT -> javascript(g);
            case REACT -> react(g);
            case HTML -> html(g);
            case SQL -> sql(g);
            case LINUX -> linux(g);
            default -> page(g);
        }
        g.dispose();
    }

    private static void page(Graphics2D g) {
        Path2D p = new Path2D.Double();
        p.moveTo(3, 1); p.lineTo(10, 1); p.lineTo(13, 4); p.lineTo(13, 15); p.lineTo(3, 15); p.closePath();
        g.setColor(new Color(0x9DB0D3));
        g.fill(p);
        Path2D fold = new Path2D.Double();
        fold.moveTo(10, 1); fold.lineTo(10, 4); fold.lineTo(13, 4); fold.closePath();
        g.setColor(new Color(0x60739A));
        g.fill(fold);
        g.setColor(new Color(0x3A4865));
        g.fill(new Rectangle2D.Double(5, 7, 6, 1.1));
        g.fill(new Rectangle2D.Double(5, 9.4, 6, 1.1));
        g.fill(new Rectangle2D.Double(5, 11.8, 4, 1.1));
    }

    private static void python(Graphics2D g) {
        Area blue = new Area(new RoundRectangle2D.Double(4.4, 0.7, 7.2, 7.4, 3.6, 3.6));
        blue.add(new Area(new RoundRectangle2D.Double(0.7, 4.4, 7.4, 7.2, 3.6, 3.6)));
        Area yellow = new Area(new RoundRectangle2D.Double(4.4, 7.9, 7.2, 7.4, 3.6, 3.6));
        yellow.add(new Area(new RoundRectangle2D.Double(7.9, 4.4, 7.4, 7.2, 3.6, 3.6)));
        Area gap = new Area(new BasicStroke(1.3f).createStrokedShape(yellow));
        gap.add(yellow);
        blue.subtract(gap);
        g.setColor(new Color(0x3776AB));
        g.fill(blue);
        g.setColor(new Color(0xFFD43B));
        g.fill(yellow);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(6.1, 2.1, 1.6, 1.6));
        g.fill(new Ellipse2D.Double(8.3, 12.3, 1.6, 1.6));
    }

    private static void java(Graphics2D g) {
        g.setColor(new Color(0xE76F00));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D s1 = new Path2D.Double();
        s1.moveTo(7.2, 0.9); s1.curveTo(5.4, 2.7, 8.8, 3.6, 7.0, 5.6);
        Path2D s2 = new Path2D.Double();
        s2.moveTo(9.8, 2.0); s2.curveTo(8.5, 3.4, 10.9, 4.1, 9.5, 5.7);
        g.draw(s1);
        g.draw(s2);
        g.setColor(new Color(0x5B8FC7));
        Path2D cup = new Path2D.Double();
        cup.moveTo(2.8, 7); cup.lineTo(12.2, 7); cup.lineTo(11.4, 11.4);
        cup.quadTo(11.0, 13.0, 9.4, 13.0); cup.lineTo(5.6, 13.0);
        cup.quadTo(4.0, 13.0, 3.6, 11.4); cup.closePath();
        g.fill(cup);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Arc2D.Double(10.4, 7.8, 3.8, 3.4, -90, 180, Arc2D.OPEN));
        g.fill(new RoundRectangle2D.Double(1.8, 13.9, 12.4, 1.4, 1.4, 1.4));
    }

    private static void hexagon(Graphics2D g, Color color, String label, float fontSize) {
        Path2D hex = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            double px = 8 + 7.4 * Math.cos(a), py = 8 + 7.4 * Math.sin(a);
            if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
        }
        hex.closePath();
        g.setColor(color);
        g.fill(hex);
        g.setColor(Color.WHITE);
        centered(g, label, fontSize, 8, 8.1);
    }

    private static void javascript(Graphics2D g) {
        g.setColor(new Color(0xF7DF1E));
        g.fill(new RoundRectangle2D.Double(1, 1, 14, 14, 3, 3));
        g.setColor(new Color(0x1E1E1E));
        centered(g, "JS", 6.6f, 10.0, 11.0);
    }

    private static void react(Graphics2D g) {
        g.setColor(new Color(0x61DAFB));
        g.setStroke(new BasicStroke(0.95f));
        Shape orbit = new Ellipse2D.Double(0.9, 5.4, 14.2, 5.2);
        for (int angle : new int[] {0, 60, 120}) {
            g.draw(AffineTransform.getRotateInstance(Math.toRadians(angle), 8, 8).createTransformedShape(orbit));
        }
        g.fill(new Ellipse2D.Double(6.6, 6.6, 2.8, 2.8));
    }

    private static void html(Graphics2D g) {
        Path2D shield = new Path2D.Double();
        shield.moveTo(1.8, 1); shield.lineTo(14.2, 1); shield.lineTo(13.0, 13.4);
        shield.lineTo(8, 15.2); shield.lineTo(3.0, 13.4); shield.closePath();
        g.setColor(new Color(0xE34F26));
        g.fill(shield);
        g.setColor(Color.WHITE);
        centered(g, "</>", 5.4f, 8, 7.8);
    }

    private static void sql(Graphics2D g) {
        Color body = new Color(0x3F78B5), top = new Color(0x7DB0E6);
        g.setColor(body);
        g.fill(new Rectangle2D.Double(2.5, 3, 11, 10));
        g.fill(new Ellipse2D.Double(2.5, 11, 11, 4));
        g.setColor(top);
        g.fill(new Ellipse2D.Double(2.5, 1, 11, 4));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(new Arc2D.Double(2.5, 5.2, 11, 4, 180, 180, Arc2D.OPEN));
        g.draw(new Arc2D.Double(2.5, 8.4, 11, 4, 180, 180, Arc2D.OPEN));
    }

    private static void linux(Graphics2D g) {
        g.setColor(new Color(0x202124));
        g.fill(new Ellipse2D.Double(3, 1.4, 10, 13.2));
        g.setColor(new Color(0xF4F4F4));
        g.fill(new Ellipse2D.Double(4.9, 6.2, 6.2, 8.0));
        g.fill(new Ellipse2D.Double(5.4, 3.2, 2.2, 2.8));
        g.fill(new Ellipse2D.Double(8.4, 3.2, 2.2, 2.8));
        g.setColor(new Color(0x202124));
        g.fill(new Ellipse2D.Double(6.3, 4.2, 0.95, 1.3));
        g.fill(new Ellipse2D.Double(8.75, 4.2, 0.95, 1.3));
        g.setColor(new Color(0xF4B400));
        Path2D beak = new Path2D.Double();
        beak.moveTo(6.3, 6.3); beak.lineTo(9.7, 6.3); beak.lineTo(8, 8.1); beak.closePath();
        g.fill(beak);
        g.fill(new Ellipse2D.Double(2.6, 13.1, 4.6, 2.5));
        g.fill(new Ellipse2D.Double(8.8, 13.1, 4.6, 2.5));
    }

    /** Draws text centred on (cx, cy) using its real glyph outline, so it is exact at tiny sizes. */
    private static void centered(Graphics2D g, String text, float fontSize, double cx, double cy) {
        Font f = new Font("Segoe UI", Font.BOLD, 1).deriveFont(fontSize);
        Shape outline = f.createGlyphVector(g.getFontRenderContext(), text).getOutline();
        Rectangle2D b = outline.getBounds2D();
        g.fill(AffineTransform.getTranslateInstance(cx - b.getCenterX(), cy - b.getCenterY()).createTransformedShape(outline));
    }
}
