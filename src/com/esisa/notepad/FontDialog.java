/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/** Format > Font... : family, style and size for the whole document, like Notepad. */
final class FontDialog {

    private static final String[] STYLE_NAMES = {"Regular", "Italic", "Bold", "Bold Italic"};
    private static final int[] STYLES = {Font.PLAIN, Font.ITALIC, Font.BOLD, Font.BOLD | Font.ITALIC};

    private FontDialog() {}

    /** Returns the chosen font, or null if the user pressed Cancel. */
    static Font choose(Component parent, Font initial) {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        JList<String> familyList = new JList<>(families);
        JList<String> styleList = new JList<>(STYLE_NAMES);
        JSpinner sizeSpinner = new JSpinner(new SpinnerNumberModel(initial.getSize(), 4, 400, 1));
        familyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        familyList.setSelectedValue(initial.getFamily(), true);
        for (int i = 0; i < STYLES.length; i++) {
            if (STYLES[i] == initial.getStyle()) styleList.setSelectedIndex(i);
        }

        JLabel sample = new JLabel("AaBbYyZz", SwingConstants.CENTER);
        sample.setBorder(BorderFactory.createTitledBorder("Sample"));
        sample.setPreferredSize(new Dimension(320, 90));

        Runnable preview = () -> sample.setFont(current(familyList, styleList, sizeSpinner, initial));
        familyList.addListSelectionListener(e -> preview.run());
        styleList.addListSelectionListener(e -> preview.run());
        sizeSpinner.addChangeListener(e -> preview.run());
        preview.run();

        JPanel lists = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = 0;
        c.gridx = 0; lists.add(new JLabel("Font:"), c);
        c.gridx = 1; lists.add(new JLabel("Font style:"), c);
        c.gridx = 2; lists.add(new JLabel("Size:"), c);
        c.gridy = 1;
        c.gridx = 0; lists.add(scroll(familyList, 200), c);
        c.gridx = 1; lists.add(scroll(styleList, 110), c);
        c.gridx = 2; lists.add(sizeSpinner, c);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(lists, BorderLayout.CENTER);
        panel.add(sample, BorderLayout.SOUTH);

        int r = JOptionPane.showConfirmDialog(parent, panel, "Font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? current(familyList, styleList, sizeSpinner, initial) : null;
    }

    private static JScrollPane scroll(JList<String> list, int width) {
        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(width, 160));
        return sp;
    }

    private static Font current(JList<String> family, JList<String> style, JSpinner size, Font fallback) {
        String f = family.getSelectedValue() != null ? family.getSelectedValue() : fallback.getFamily();
        int s = style.getSelectedIndex() >= 0 ? STYLES[style.getSelectedIndex()] : fallback.getStyle();
        return new Font(f, s, (Integer) size.getValue());
    }
}
