/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

/** Find / Replace window (Ctrl+H), laid out like Notepad's. Works on whichever tab is active. */
final class FindReplaceDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient Supplier<NoteEditor> editor;
    private final JTextField findField = new JTextField(24);
    private final JTextField replaceField = new JTextField(24);
    private final JLabel replaceLabel = new JLabel("Replace with:");
    private final JCheckBox matchCase = new JCheckBox("Match case");
    private final JCheckBox wrapAround = new JCheckBox("Wrap around", true);
    private final JRadioButton up = new JRadioButton("Up");
    private final JRadioButton down = new JRadioButton("Down", true);
    private final JPanel directionPanel = new JPanel(new GridLayout(1, 2));
    private final JButton findButton = new JButton("Find Next");
    private final JButton replaceButton = new JButton("Replace");
    private final JButton replaceAllButton = new JButton("Replace All");
    private boolean replaceMode;

    FindReplaceDialog(JFrame owner, Supplier<NoteEditor> editor) {
        super(owner, "Find", false);
        this.editor = editor;

        ButtonGroup g = new ButtonGroup();
        g.add(up);
        g.add(down);
        directionPanel.add(up);
        directionPanel.add(down);
        directionPanel.setBorder(BorderFactory.createTitledBorder("Direction"));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(new JLabel("Find what:"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(findField, c);
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0; form.add(replaceLabel, c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; form.add(replaceField, c);
        JPanel options = new JPanel(new GridLayout(2, 1));
        options.add(matchCase);
        options.add(wrapAround);
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE; c.weightx = 0; form.add(options, c);
        c.gridx = 1; c.anchor = GridBagConstraints.EAST; form.add(directionPanel, c);

        JButton cancel = new JButton("Cancel");
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        buttons.add(findButton);
        buttons.add(replaceButton);
        buttons.add(replaceAllButton);
        buttons.add(cancel);
        JPanel east = new JPanel(new BorderLayout());
        east.add(buttons, BorderLayout.NORTH);
        east.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(form, BorderLayout.CENTER);
        root.add(east, BorderLayout.EAST);
        setContentPane(root);

        findButton.addActionListener(e -> findNext(replaceMode || down.isSelected()));
        replaceButton.addActionListener(e -> replace());
        replaceAllButton.addActionListener(e -> replaceAll());
        cancel.addActionListener(e -> setVisible(false));
        getRootPane().setDefaultButton(findButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(ActionEvent e) { setVisible(false); }
        });
        setResizable(false);
    }

    void open(boolean replace, String initialQuery) {
        if (editor.get() == null) return;
        replaceMode = replace;
        setTitle(replace ? "Replace" : "Find");
        replaceLabel.setVisible(replace);
        replaceField.setVisible(replace);
        replaceButton.setVisible(replace);
        replaceAllButton.setVisible(replace);
        directionPanel.setVisible(!replace);

        String selected = editor.get().getSelectedText();
        if (selected != null && !selected.isEmpty() && !selected.contains("\n")) findField.setText(selected);
        else if (initialQuery != null && !initialQuery.isEmpty()) findField.setText(initialQuery);
        pack();
        if (!isVisible()) setLocationRelativeTo(getOwner());
        setVisible(true);
        findField.requestFocusInWindow();
        findField.selectAll();
    }

    boolean findNext(boolean forward) {
        NoteEditor ed = editor.get();
        String query = findField.getText();
        if (ed == null || query.isEmpty()) return false;
        String hay = fold(ed.text()), needle = fold(query);

        int index;
        if (forward) {
            index = hay.indexOf(needle, ed.getSelectionEnd());
            if (index < 0 && wrapAround.isSelected()) index = hay.indexOf(needle);
        } else {
            int from = ed.getSelectionStart() - 1;
            index = from < 0 ? -1 : hay.lastIndexOf(needle, from);
            if (index < 0 && wrapAround.isSelected()) index = hay.lastIndexOf(needle);
        }
        if (index < 0) {
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(isVisible() ? this : getOwner(), "Cannot find \"" + query + "\"",
                    AppFrame.APP, JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        ed.setCaretPosition(index);
        ed.moveCaretPosition(index + query.length());
        ed.getCaret().setSelectionVisible(true);
        return true;
    }

    private void replace() {
        NoteEditor ed = editor.get();
        if (ed == null) return;
        String selected = ed.getSelectedText();
        if (selected != null && fold(selected).equals(fold(findField.getText()))) {
            ed.replaceSelection(replaceField.getText());
        }
        findNext(true);
    }

    private void replaceAll() {
        NoteEditor ed = editor.get();
        String query = findField.getText();
        if (ed == null || query.isEmpty()) return;
        String hay = fold(ed.text()), needle = fold(query);
        List<Integer> hits = new ArrayList<>();
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + needle.length())) hits.add(i);
        if (hits.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            JOptionPane.showMessageDialog(this, "Cannot find \"" + query + "\"", AppFrame.APP, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String replacement = replaceField.getText();
        StyledDocument doc = ed.doc;
        ed.inOneUndo(() -> {
            try {
                for (int k = hits.size() - 1; k >= 0; k--) { // back to front so earlier offsets stay valid
                    int at = hits.get(k);
                    SimpleAttributeSet style = NoteEditor.styleOf(doc.getCharacterElement(at));
                    doc.remove(at, query.length());
                    doc.insertString(at, replacement, style);
                }
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private String fold(String s) {
        return matchCase.isSelected() ? s : s.toLowerCase(Locale.ROOT);
    }
}
