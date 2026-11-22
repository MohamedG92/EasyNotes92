/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * One document (one tab): text, file, encoding, undo, bold/italic/colour, line duplication.
 * Note-taking helpers (==+, *+, chapters, numbering, autocomplete) live in {@link NoteAssist}.
 */
final class NoteEditor extends JTextPane {

    private static final long serialVersionUID = 1L;
    static final Charset ANSI = Charset.forName("windows-1252");

    /** The only character styles this editor creates (and keeps when text is copied around). */
    static final Object[] STYLE_KEYS = {
        StyleConstants.Bold, StyleConstants.Italic, StyleConstants.Underline, StyleConstants.Foreground
    };

    interface Host {
        boolean wordWrap();
        boolean suggestions();
        /** Called whenever the text or the dirty flag changes. */
        void changed(NoteEditor editor);
        /** Colours for ===== lines, ***** lines and code blocks (Format > Note Colors). */
        Palette palette();
    }

    record Palette(Color equalsLine, Color starsLine, Color code, Color codeBackground,
                   Color keyword, Color string, Color number, Color comment,
                   Color operator, Color paren, Color bracket, Color brace) {}

    /** Colour of a token inside a code block. */
    Color syntaxColor(CodeColors.Tok t) {
        Palette p = host.palette();
        return switch (t) {
            case KEYWORD -> p.keyword();
            case STRING -> p.string();
            case NUMBER -> p.number();
            case COMMENT -> p.comment();
            case OPERATOR -> p.operator();
            case PAREN -> p.paren();
            case BRACKET -> p.bracket();
            case BRACE -> p.brace();
            case STAR -> p.starsLine();             // a lone * looks like your ***** lines
            default -> p.code();
        };
    }

    /** Paragraph styles: changing their colour re-colours every matching line at once. */
    static final String CODE_STYLE = "np-code";
    static final String EQUALS_STYLE = "np-rule-equals";
    static final String STARS_STYLE = "np-rule-stars";

    /** Lines that look like code: ;  {  }  keywords, arrows, comparisons, tags ... */
    private static final java.util.regex.Pattern CODE_LINE = java.util.regex.Pattern.compile(
            "[;{}]\\s*$|^\\s*[{}]|^\\s*(public|private|protected|static|final|class|interface|enum|def|import|from|package"
            + "|#include|#define|return|if|else|elif|for|foreach|while|switch|case|try|catch|except|var|let|const"
            + "|function|using|namespace|int|void|double|float|char|bool|boolean|String|string|System\\.|Console\\."
            + "|console\\.|print|printf|cout|std::|SELECT|INSERT|UPDATE|CREATE|sudo|cd|ls|git|npm|pip)\\b"
            + "|=>|->|::|\\(\\)|==|!=|\\+\\+|&&|\\|\\||</?[a-zA-Z][\\w-]*[ >/]|^\\s*(//|#|/\\*|\\*)");

    final transient Host host;
    final transient NoteAssist assist;
    transient StyledDocument doc;

    File file;
    boolean dirty;
    boolean plainWarned;
    String eol = "\r\n";
    String encoding = "UTF-8";
    Language language;
    boolean noteMode;
    long changeCount;

    private Font baseFont;
    private int zoom = 100;

    private final transient UndoManager undo = new UndoManager();
    private transient CompoundEdit typing;
    private transient CompoundEdit group;
    private transient DocumentEvent.EventType typingType;
    private long lastEditTime;
    /** True while separator lines are re-coloured automatically: those style changes stay out of Ctrl+Z. */
    private boolean autoStyling;
    private int restyleFrom = Integer.MAX_VALUE;
    private int restyleTo = -1;

    /** Grey inline suggestion (accepted with Tab). */
    String ghost;
    int ghostPos = -1;

    NoteEditor(Host host, Vocabulary vocab, Language language, boolean noteMode, Font baseFont, int zoom) {
        this.host = host;
        this.language = language;
        this.noteMode = noteMode;
        this.baseFont = baseFont;
        this.zoom = zoom;
        setOpaque(false);                       // we paint the background + current-line band ourselves
        setMargin(new Insets(18, 28, 18, 28));
        setFocusTraversalKeysEnabled(false);    // lets Tab / Ctrl+Tab reach our key handling
        assist = new NoteAssist(this, vocab);
        setEditorKit(new CodeColors.Kit());     // syntax colours inside code blocks
        useDocument(new DefaultStyledDocument());
        addCaretListener(e -> repaint());
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { clickBelowText(e); }
        });
    }

    // =====================================================================
    // Free space under the text: 20 empty lines you can click into
    // =====================================================================

    static final int EXTRA_LINES = 20;
    /** While typing, keep at least this many lines visible under the cursor, so the writing goes down the page. */
    static final int ROOM_BELOW = 6;

    int lineHeight() {
        return Math.round(getFontMetrics(getFont()).getHeight() * 1.18f);
    }

    private void updateBottomSpace() {
        Insets m = getMargin();
        setMargin(new Insets(m.top, m.left, EXTRA_LINES * lineHeight(), m.right));
    }

    /** A click in the empty space under the text adds the blank lines needed to put the cursor exactly there. */
    void clickBelowText(java.awt.event.MouseEvent e) {
        if (!javax.swing.SwingUtilities.isLeftMouseButton(e) || e.isShiftDown() || e.getClickCount() != 1 || doc == null) return;
        try {
            Rectangle2D last = modelToView2D(doc.getLength());
            if (last == null) return;
            double bottom = last.getY() + last.getHeight();
            if (e.getY() <= bottom) return;
            int lines = Math.min(EXTRA_LINES + 40, (int) ((e.getY() - bottom) / lineHeight()) + 1);
            javax.swing.SwingUtilities.invokeLater(() -> addLinesAtEnd(lines));
        } catch (BadLocationException ignored) {
            // nothing laid out yet
        }
    }

    void addLinesAtEnd(int lines) {
        inOneUndo(() -> {
            try {
                int end = doc.getLength();
                doc.insertString(end, "\n".repeat(lines), null);
                Style base = doc.getStyle(StyleContext.DEFAULT_STYLE);
                Element root = doc.getDefaultRootElement();
                for (int i = root.getElementIndex(end + 1); i < root.getElementCount(); i++) {
                    doc.setLogicalStyle(root.getElement(i).getStartOffset(), base); // new lines are normal text, even after code
                }
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        setCaretPosition(doc.getLength());
        requestFocusInWindow();
    }

    /** Scrolls so a few lines stay free under the cursor: the text moves up as you write, like on paper. */
    void keepRoomBelow() {
        try {
            Rectangle2D r = modelToView2D(getCaretPosition());
            if (r == null) return;
            scrollRectToVisible(new java.awt.Rectangle((int) r.getX(), (int) r.getY(), 1,
                    (int) r.getHeight() + ROOM_BELOW * lineHeight()));
        } catch (BadLocationException ignored) {
            // layout not ready
        }
    }

    // =====================================================================
    // Painting: background, current line, ghost suggestion
    // =====================================================================

    /**
     * One mouse-wheel step = half a line. Windows does 3 steps per notch, so a notch moves about 1.5 lines:
     * slow enough to read while scrolling. (Arrow keys / PageUp / PageDown are unchanged.)
     */
    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? Math.max(4, lineHeight() / 2)
                : super.getScrollableUnitIncrement(visible, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (host == null || host.wordWrap()) return true;
        Component parent = getParent();
        return parent == null || getUI().getPreferredSize(this).width <= parent.getWidth();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        paintCodeBlocks((Graphics2D) g);
        paintSelection((Graphics2D) g);
        if (getSelectionStart() == getSelectionEnd()) {
            try {
                Rectangle2D r = modelToView2D(getCaretPosition());
                if (r != null) {
                    g.setColor(Ui.alpha(getForeground(), Ui.dark() ? 12 : 16));
                    g.fillRect(0, (int) r.getY(), getWidth(), (int) Math.ceil(r.getHeight()));
                }
            } catch (BadLocationException ignored) {
                // caret outside the text for a moment: no highlight
            }
        }
        super.paintComponent(g);
        paintGhost(g);
    }

    /** The standard highlighter's selection is made invisible; {@link #paintSelection} draws it instead. */
    private static final Color NO_SELECTION_FILL = new Color(0, 0, 0, 0);

    /**
     * Selection = one merged shape in a see-through accent tint, drawn UNDER the text so every colour
     * (syntax, your own colours) stays readable. Lines that continue get a little extra width to show the
     * line break is selected, and the shape gets a thin accent outline.
     */
    private void paintSelection(Graphics2D g0) {
        int s = getSelectionStart(), e = getSelectionEnd();
        if (s == e || doc == null || !getCaret().isSelectionVisible()) return;
        try {
            java.awt.Rectangle vis = getVisibleRect();
            s = Math.max(s, javax.swing.text.Utilities.getRowStart(this, viewToModel2D(new java.awt.Point(0, vis.y))));
            e = Math.min(e, viewToModel2D(new java.awt.Point(getWidth(), vis.y + vis.height)) + 1);
            e = Math.min(e, doc.getLength());
            if (s >= e) return;
            float nl = getFontMetrics(getFont()).charWidth(' ');
            java.awt.geom.Area area = new java.awt.geom.Area();
            int pos = s;
            while (pos < e) {
                int rowEnd = javax.swing.text.Utilities.getRowEnd(this, pos);      // last offset on this visual row
                if (rowEnd < pos) break;
                Rectangle2D a = modelToView2D(pos);
                if (a == null) break;
                double x0 = a.getX(), x1;
                if (e <= rowEnd) {                                                  // selection stops on this row
                    x1 = modelToView2D(e).getX();
                } else {                                                            // continues on the next row
                    Rectangle2D last = modelToView2D(rowEnd);
                    boolean lineBreak = doc.getText(rowEnd, 1).charAt(0) == '\n';
                    x1 = lineBreak ? last.getX() + nl
                                   : last.getX() + getFontMetrics(getFont()).charWidth(doc.getText(rowEnd, 1).charAt(0));
                }
                if (x1 - x0 < nl) x1 = x0 + nl;                                     // an empty selected line stays visible
                // rows overlap by 1 px so the rounded rows join into one smooth block
                area.add(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Double(
                        x0 - 1, a.getY() - 0.5, x1 - x0 + 2, a.getHeight() + 1, 6, 6)));
                pos = rowEnd + 1;
            }
            Graphics2D g = (Graphics2D) g0.create();
            Ui.smooth(g);
            boolean focused = isFocusOwner();
            Color tint = focused ? Ui.accent() : getForeground();
            g.setColor(Ui.alpha(tint, focused ? (Ui.dark() ? 78 : 60) : (Ui.dark() ? 34 : 30)));
            g.fill(area);
            g.dispose();
        } catch (BadLocationException | NullPointerException ignored) {
            // layout not ready: skip this frame
        }
    }

    /** Each run of code lines gets a rounded tinted card, a thin coloured bar on the left and a small label. */
    private void paintCodeBlocks(Graphics2D g0) {
        if (host == null || doc == null) return;
        Element root = doc.getDefaultRootElement();
        java.awt.Rectangle vis = getVisibleRect();
        int first = root.getElementIndex(viewToModel2D(new java.awt.Point(0, vis.y)));
        int last = root.getElementIndex(viewToModel2D(new java.awt.Point(0, vis.y + vis.height)));
        while (first > 0 && isCode(root.getElement(first - 1))) first--;
        Palette p = host.palette();
        Graphics2D g = (Graphics2D) g0.create();
        Ui.smooth(g);
        Insets m = getInsets();
        try {
            for (int i = first; i <= last; i++) {
                if (!isCode(root.getElement(i))) continue;
                int j = i;
                while (j + 1 < root.getElementCount() && isCode(root.getElement(j + 1))) j++;
                Rectangle2D top = modelToView2D(root.getElement(i).getStartOffset());
                Element end = root.getElement(j);
                Rectangle2D bottom = modelToView2D(Math.max(end.getStartOffset(), Math.min(end.getEndOffset() - 1, doc.getLength())));
                if (top == null || bottom == null) break;
                double x = m.left - 14, w = getWidth() - m.left - m.right + 28;
                double y = top.getY() - 2, h = bottom.getY() + bottom.getHeight() - top.getY() + 4;
                g.setColor(p.codeBackground());
                g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, 14, 14));
                g.setColor(Ui.alpha(p.code(), 190));
                g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y + 4, 3, h - 8, 3, 3));
                String label = noteMode && language != Language.TEXT ? language.label.toLowerCase(Locale.ROOT) : "code";
                g.setFont(Ui.ui(10.5f, Font.BOLD));
                g.setColor(Ui.alpha(p.code(), 150));
                g.drawString(label, (float) (x + w - 12 - g.getFontMetrics().stringWidth(label)), (float) (y + 15));
                i = j;
            }
        } catch (BadLocationException ignored) {
            // layout not ready yet
        } finally {
            g.dispose();
        }
    }

    private void paintGhost(Graphics g) {
        if (ghost == null || ghostPos < 0 || ghostPos > doc.getLength()) return;
        try {
            Rectangle2D r = modelToView2D(ghostPos);
            if (r == null) return;
            Font f = ghostPos > 0 ? doc.getFont(doc.getCharacterElement(ghostPos - 1).getAttributes()) : getFont();
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Ui.mix(getForeground(), getBackground(), 0.55f));
            g2.drawString(ghost, (float) r.getX() + 1, (float) (r.getY() + fm.getAscent()));
            g2.dispose();
        } catch (BadLocationException ignored) {
            // stale suggestion: just don't draw it
        }
    }

    // =====================================================================
    // Fonts and zoom
    // =====================================================================

    void setBaseFont(Font font, int zoomPercent) {
        baseFont = font;
        zoom = zoomPercent;
        reapplyFont();
    }

    private int scaledSize() {
        return Math.max(4, Math.round(baseFont.getSize() * zoom / 100f));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setSelectionColor(NO_SELECTION_FILL);   // we paint the selection ourselves (paintSelection)
        setSelectedTextColor(null);             // selected text keeps its own colour (syntax, bold colours...)
        if (doc != null) reapplyFont(); // theme change: the UI resets the document font
    }

    private void reapplyFont() {
        if (baseFont == null || doc == null) return;
        setFont(baseFont.deriveFont((float) scaledSize()));
        applyBaseFont(doc, scaledSize());
        updateBottomSpace();   // 20 lines of space follow the zoom
    }

    /** The document-wide font lives in the default style, so a change re-renders every run at once. */
    private void applyBaseFont(StyledDocument d, int size) {
        Style s = d.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setFontFamily(s, baseFont.getFamily());
        StyleConstants.setFontSize(s, size);
        StyleConstants.setBold(s, baseFont.isBold());
        StyleConstants.setItalic(s, baseFont.isItalic());
        StyleConstants.setLineSpacing(s, 0.18f);   // airier lines for reading notes
    }

    // =====================================================================
    // Document + undo
    // =====================================================================

    void useDocument(StyledDocument d) {
        doc = d;
        setStyledDocument(d);
        reapplyFont(); // must come after attaching: the text UI copies the component font into the doc
        MutableAttributeSet input = getInputAttributes();
        input.removeAttributes(input);

        Style base = d.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setLeftIndent(d.addStyle(CODE_STYLE, base), 14f); // code sits a bit in from the coloured bar
        d.addStyle(EQUALS_STYLE, base);
        d.addStyle(STARS_STYLE, base);
        applyPalette();

        d.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                markDirty();
                scheduleRestyle(e.getOffset(), e.getOffset() + e.getLength());
                javax.swing.SwingUtilities.invokeLater(NoteEditor.this::keepRoomBelow);
            }
            @Override public void removeUpdate(DocumentEvent e) { markDirty(); scheduleRestyle(e.getOffset(), e.getOffset()); }
            @Override public void changedUpdate(DocumentEvent e) { /* style changes call markDirty themselves */ }
        });
        d.addUndoableEditListener(this::recordEdit);
        assist.attach(d);
        restyle(0, d.getLength());

        undo.discardAllEdits();
        typing = null;
        group = null;
        ghost = null;
        setCaretPosition(0);
    }

    void markDirty() {
        changeCount++;
        dirty = true;
        if (host != null) host.changed(this);
    }

    void markClean() {
        dirty = false;
        if (host != null) host.changed(this);
    }

    private void recordEdit(UndoableEditEvent e) {
        if (autoStyling) return;
        UndoableEdit edit = e.getEdit();
        if (group != null) {
            group.addEdit(edit);
            return;
        }
        DocumentEvent.EventType type = edit instanceof DocumentEvent de ? de.getType() : null;
        boolean isTyping = type == DocumentEvent.EventType.INSERT || type == DocumentEvent.EventType.REMOVE;
        long now = System.currentTimeMillis();
        if (isTyping && typing != null && type == typingType && now - lastEditTime < 1000) {
            typing.addEdit(edit);
        } else {
            endTyping();
            if (isTyping) {
                typing = new CompoundEdit();
                typing.addEdit(edit);
                typingType = type;
                undo.addEdit(typing);
            } else {
                undo.addEdit(edit);
            }
        }
        lastEditTime = now;
    }

    private void endTyping() {
        if (typing != null) {
            typing.end();
            typing = null;
        }
    }

    /** Runs a multi-step change so that one Ctrl+Z undoes all of it. */
    void inOneUndo(Runnable action) {
        endTyping();
        group = new CompoundEdit();
        try {
            action.run();
        } finally {
            CompoundEdit g = group;
            group = null;
            g.end();
            if (g.isSignificant()) undo.addEdit(g);
        }
    }

    void undo() {
        endTyping();
        if (undo.canUndo()) { undo.undo(); markDirty(); }
    }

    void redo() {
        endTyping();
        if (undo.canRedo()) { undo.redo(); markDirty(); }
    }

    String text() {
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    // =====================================================================
    // Styled-text helpers
    // =====================================================================

    /** The bold/italic/underline/colour a character run sets itself (not inherited). */
    static SimpleAttributeSet styleOf(Element run) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        AttributeSet a = run.getAttributes();
        for (Object key : STYLE_KEYS) {
            if (a.isDefined(key)) set.addAttribute(key, a.getAttribute(key));
        }
        return set;
    }

    /** Copies text [from, to) of src into dst at position at, keeping each run's style. Returns the end position in dst. */
    static int copyRuns(StyledDocument src, int from, int to, StyledDocument dst, int at) throws BadLocationException {
        int pos = from, dest = at;
        while (pos < to) {
            Element run = src.getCharacterElement(pos);
            int end = Math.min(run.getEndOffset(), to);
            String text = src.getText(pos, end - pos);
            dst.insertString(dest, text, styleOf(run));
            dest += text.length();
            pos = end;
        }
        return dest;
    }

    boolean hasFormatting() {
        Element root = doc.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            Element para = root.getElement(i);
            for (int j = 0; j < para.getElementCount(); j++) {
                AttributeSet a = para.getElement(j).getAttributes();
                for (Object key : STYLE_KEYS) {
                    Object v = a.isDefined(key) ? a.getAttribute(key) : null;
                    if (Boolean.TRUE.equals(v) || v instanceof Color) return true;
                }
            }
        }
        return false;
    }

    void toggleStyle(Object key) {
        int s = getSelectionStart(), e = getSelectionEnd();
        if (s == e) {
            MutableAttributeSet input = getInputAttributes();
            input.addAttribute(key, !Boolean.TRUE.equals(input.getAttribute(key)));
            return;
        }
        boolean allOn = true;
        for (int pos = s; pos < e; ) {
            Element run = doc.getCharacterElement(pos);
            if (!Boolean.TRUE.equals(run.getAttributes().getAttribute(key))) { allOn = false; break; }
            pos = run.getEndOffset();
        }
        SimpleAttributeSet set = new SimpleAttributeSet();
        set.addAttribute(key, !allOn);
        doc.setCharacterAttributes(s, e - s, set, false);
        markDirty();
    }

    void applyColor(Color c) {
        int s = getSelectionStart(), e = getSelectionEnd();
        if (s == e) {
            StyleConstants.setForeground(getInputAttributes(), c);
        } else {
            SimpleAttributeSet set = new SimpleAttributeSet();
            StyleConstants.setForeground(set, c);
            doc.setCharacterAttributes(s, e - s, set, false);
            markDirty();
        }
    }

    // =====================================================================
    // Coloured separator lines and code blocks
    // =====================================================================

    /** Pushes the current palette into this document's paragraph styles. */
    void applyPalette() {
        if (doc == null || host == null) return;
        Palette p = host.palette();
        StyleConstants.setForeground(doc.getStyle(EQUALS_STYLE), p.equalsLine());
        StyleConstants.setForeground(doc.getStyle(STARS_STYLE), p.starsLine());
        StyleConstants.setForeground(doc.getStyle(CODE_STYLE), p.code());
        repaint();
    }

    static boolean isEqualsLine(String t) { return t.strip().matches("={2,}"); }   // == alone is one of your small separators

    static boolean isStarsLine(String t) { return t.strip().matches("\\*{3,}"); }

    /** Does pasted text look like code? Most of its non-empty lines must look like code. */
    static boolean looksLikeCode(String text) {
        String[] lines = text.split("\n");
        int nonEmpty = 0, code = 0;
        for (String l : lines) {
            if (l.isBlank()) continue;
            nonEmpty++;
            if (CODE_LINE.matcher(l).find() || l.startsWith("\t") || l.startsWith("    ")) code++;
        }
        if (nonEmpty == 0) return false;
        if (nonEmpty == 1) return text.strip().length() > 3 && CODE_LINE.matcher(text).find() && text.matches("(?s).*[;{}()=<>].*");
        return code * 2 >= nonEmpty;
    }

    private String styleName(Element paragraph) {
        AttributeSet parent = paragraph.getAttributes().getResolveParent();
        return parent instanceof Style s ? s.getName() : null;
    }

    boolean isCode(Element paragraph) {
        return CODE_STYLE.equals(styleName(paragraph));
    }

    private String paragraphText(Element p) throws BadLocationException {
        int end = Math.min(p.getEndOffset() - 1, doc.getLength());
        return doc.getText(p.getStartOffset(), end - p.getStartOffset());
    }

    /** Re-checks the lines touched by an edit, after the edit is finished (a listener may not change the text). */
    private void scheduleRestyle(int from, int to) {
        boolean first = restyleTo < 0;
        restyleFrom = Math.min(restyleFrom, from);
        restyleTo = Math.max(restyleTo, to);
        if (first) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                int a = restyleFrom, b = restyleTo;
                restyleFrom = Integer.MAX_VALUE;
                restyleTo = -1;
                restyle(Math.min(a, doc.getLength()), Math.min(b, doc.getLength()));
            });
        }
    }

    /** Gives ===== / ***** lines their colour and takes it away when they stop being separators. Code lines are left alone. */
    void restyle(int from, int to) {
        Element root = doc.getDefaultRootElement();
        int a = root.getElementIndex(from), b = root.getElementIndex(to);
        Style base = doc.getStyle(StyleContext.DEFAULT_STYLE);
        autoStyling = true;
        try {
            if (doc.getLength() == 0 && isCode(root.getElement(0))) {
                doc.setLogicalStyle(0, base);   // everything deleted: start again with a normal line
            }
            for (int i = a; i <= b && i < root.getElementCount(); i++) {
                Element p = root.getElement(i);
                if (isCode(p)) continue;
                String t = paragraphText(p);
                Style want = isEqualsLine(t) ? doc.getStyle(EQUALS_STYLE) : isStarsLine(t) ? doc.getStyle(STARS_STYLE) : base;
                String have = styleName(p);
                if (have == null || !have.equals(want.getName())) doc.setLogicalStyle(p.getStartOffset(), want);
            }
        } catch (BadLocationException ignored) {
            // the text moved on; the next edit re-checks
        } finally {
            autoStyling = false;
        }
    }

    /** Turns the lines in [from, to] into a code block, or back into normal lines. */
    void setCode(int from, int to, boolean on) {
        Element root = doc.getDefaultRootElement();
        int a = root.getElementIndex(from), b = root.getElementIndex(to);
        Style code = doc.getStyle(CODE_STYLE), base = doc.getStyle(StyleContext.DEFAULT_STYLE);
        for (int i = a; i <= b; i++) {
            doc.setLogicalStyle(root.getElement(i).getStartOffset(), on ? code : base);
        }
        if (!on) restyle(from, to);
        markDirty();
        repaint();
    }

    /** Ctrl+Shift+K: the selected lines (or the current line) become a code block, or stop being one. */
    void toggleCodeBlock() {
        int s = getSelectionStart(), e = getSelectionEnd();
        if (e > s) e--; // a selection ending at a line start doesn't include that line
        Element root = doc.getDefaultRootElement();
        boolean allCode = true;
        for (int i = root.getElementIndex(s); i <= root.getElementIndex(e); i++) {
            if (!isCode(root.getElement(i))) { allCode = false; break; }
        }
        boolean on = !allCode;
        int from = s, to = e;
        inOneUndo(() -> setCode(from, to, on));
    }

    /** Paste from an IDE: the code gets its own lines, styled as a code block; typing continues on a normal line after it. */
    void pasteAsCode(String text) {
        inOneUndo(() -> {
            try {
                replaceSelection("");
                int s = getCaretPosition();
                Element root = doc.getDefaultRootElement();
                Element line = root.getElement(root.getElementIndex(s));
                if (!doc.getText(line.getStartOffset(), s - line.getStartOffset()).isBlank()) {
                    doc.insertString(s, "\n", null);
                    s++;
                }
                boolean endedWithNewline = text.endsWith("\n");
                String body = endedWithNewline ? text.substring(0, text.length() - 1) : text;
                doc.insertString(s, body, null);
                int end = s + body.length();
                setCode(s, end, true);
                Element last = root.getElement(root.getElementIndex(end));
                String after = doc.getText(end, Math.min(last.getEndOffset() - 1, doc.getLength()) - end);
                if (endedWithNewline || !after.isBlank()) {
                    doc.insertString(end, "\n", null);
                    doc.setLogicalStyle(end + 1, doc.getStyle(StyleContext.DEFAULT_STYLE));
                    restyle(end + 1, end + 1);
                    setCaretPosition(end + 1);
                } else {
                    setCaretPosition(end);
                }
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    /** Enter on an empty line at the end of a code block leaves the block (like ending a list). */
    boolean leaveCodeBlockOnEnter() {
        if (getSelectionStart() != getSelectionEnd()) return false;
        Element root = doc.getDefaultRootElement();
        int li = root.getElementIndex(getCaretPosition());
        Element p = root.getElement(li);
        try {
            if (!isCode(p) || !paragraphText(p).isBlank() || li == 0 || !isCode(root.getElement(li - 1))) return false;
        } catch (BadLocationException e) {
            return false;
        }
        inOneUndo(() -> setCode(p.getStartOffset(), p.getStartOffset(), false));
        return true;
    }

    /** Code lines as "3-7,12-12" (0-based line numbers) so they can be remembered with the file. */
    String codeRanges() {
        Element root = doc.getDefaultRootElement();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < root.getElementCount(); i++) {
            if (!isCode(root.getElement(i))) continue;
            int j = i;
            while (j + 1 < root.getElementCount() && isCode(root.getElement(j + 1))) j++;
            if (sb.length() > 0) sb.append(',');
            sb.append(i).append('-').append(j);
            i = j;
        }
        return sb.toString();
    }

    /** Restores code lines saved by {@link #codeRanges()} (ignored where the file has fewer lines now). */
    void applyCodeRanges(String ranges) {
        if (ranges == null || ranges.isBlank()) return;
        Element root = doc.getDefaultRootElement();
        Style code = doc.getStyle(CODE_STYLE);
        autoStyling = true;
        try {
            for (String r : ranges.split(",")) {
                String[] ab = r.split("-");
                int a = Integer.parseInt(ab[0].trim()), b = Integer.parseInt(ab[1].trim());
                for (int i = a; i <= b && i < root.getElementCount(); i++) {
                    doc.setLogicalStyle(root.getElement(i).getStartOffset(), code);
                }
            }
        } catch (RuntimeException ignored) {
            // malformed entry: skip it
        } finally {
            autoStyling = false;
        }
        repaint();
    }

    // =====================================================================
    // Duplicate line (Ctrl+Alt+Up / Ctrl+Alt+Down)
    // =====================================================================

    void duplicateLines(boolean up) {
        Caret caret = getCaret();
        int dot = caret.getDot(), mark = caret.getMark();
        int s = Math.min(dot, mark), e = Math.max(dot, mark);
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(s);
        int last = root.getElementIndex(e > s ? e - 1 : e);
        int start = root.getElement(first).getStartOffset();
        int end = Math.min(root.getElement(last).getEndOffset(), doc.getLength());

        inOneUndo(() -> {
            try {
                boolean endsWithNewline = end > start && doc.getText(end - 1, 1).equals("\n");
                int inserted;
                if (endsWithNewline) {
                    inserted = copyRuns(doc, start, end, doc, end) - end;
                } else {
                    doc.insertString(end, "\n", styleOf(doc.getCharacterElement(Math.max(start, end - 1))));
                    inserted = copyRuns(doc, start, end, doc, end + 1) - end;
                }
                int shift = up ? 0 : inserted;
                caret.setDot(mark + shift);
                caret.moveDot(dot + shift);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    // =====================================================================
    // Delete line (Ctrl+D) / select line (Alt+A)
    // =====================================================================

    /** Offsets [start, end) of the whole lines touched by the caret or selection (end = before the last line break). */
    private int[] lineSpan() {
        Caret caret = getCaret();
        int s = Math.min(caret.getDot(), caret.getMark()), e = Math.max(caret.getDot(), caret.getMark());
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(s);
        int last = root.getElementIndex(e > s ? e - 1 : e);
        return new int[] {first, last, root.getElement(first).getStartOffset(), root.getElement(last).getEndOffset() - 1};
    }

    /** Removes the current line (or every selected line) completely, line break included; one undo. */
    void deleteLines() {
        int[] sp = lineSpan();
        int start = sp[2], end = sp[3];
        Element root = doc.getDefaultRootElement();
        if (end < doc.getLength()) end++;                           // take the line break after it
        else if (start > 0) start--;                                // last line: take the break before it
        int from = start, to = end;
        int col = getCaretPosition() - root.getElement(root.getElementIndex(getCaretPosition())).getStartOffset();
        inOneUndo(() -> {
            try {
                doc.remove(from, to - from);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        // caret: same column on the line that took its place
        Element r = doc.getDefaultRootElement();
        Element line = r.getElement(r.getElementIndex(Math.min(from, doc.getLength())));
        int pos = Math.min(line.getStartOffset() + col, line.getEndOffset() - 1);
        setCaretPosition(Math.max(0, Math.min(pos, doc.getLength())));
        repaint();
    }

    /** Selects the whole current line (or extends the selection to whole lines), without the final line break. */
    void selectLines() {
        int[] sp = lineSpan();
        getCaret().setDot(sp[2]);
        getCaret().moveDot(Math.min(sp[3], doc.getLength()));
    }

    // =====================================================================
    // Move line (Alt+Up / Alt+Down)
    // =====================================================================

    /** Moves the current line (or every selected line) one line up or down, keeping styles and code blocks. */
    void moveLines(boolean up) {
        Caret caret = getCaret();
        int dot = caret.getDot(), mark = caret.getMark();
        int s = Math.min(dot, mark), e = Math.max(dot, mark);
        Element root = doc.getDefaultRootElement();
        int first = root.getElementIndex(s);
        int last = root.getElementIndex(e > s ? e - 1 : e);
        int nb = up ? first - 1 : last + 1;
        if (nb < 0 || nb >= root.getElementCount()) return;
        int lo = Math.min(first, nb), hi = Math.max(last, nb);

        // new order of the lines lo..hi: the neighbour jumps to the other side of the block
        java.util.List<Integer> order = new java.util.ArrayList<>();
        if (up) { for (int i = first; i <= last; i++) order.add(i); order.add(nb); }
        else    { order.add(nb); for (int i = first; i <= last; i++) order.add(i); }

        int a = root.getElement(lo).getStartOffset();
        int b = root.getElement(hi).getEndOffset() - 1;               // without hi's line break
        int nbLen = root.getElement(nb).getEndOffset() - 1 - root.getElement(nb).getStartOffset();
        java.util.List<Style> styles = new java.util.ArrayList<>();
        for (int i : order) styles.add(doc.getLogicalStyle(root.getElement(i).getStartOffset()));

        inOneUndo(() -> {
            try {
                StyledDocument tmp = new DefaultStyledDocument();
                for (int k = 0; k < order.size(); k++) {
                    Element line = root.getElement(order.get(k));
                    int ls = line.getStartOffset(), le = line.getEndOffset() - 1;
                    if (k > 0) tmp.insertString(tmp.getLength(), "\n", null);
                    copyRuns(doc, ls, le, tmp, tmp.getLength());
                }
                doc.remove(a, b - a);
                copyRuns(tmp, 0, tmp.getLength(), doc, a);
                autoStyling = true;
                try {
                    Element r = doc.getDefaultRootElement();
                    int idx = r.getElementIndex(a);
                    for (int k = 0; k < styles.size(); k++) {
                        Style st = styles.get(k);
                        if (st != null) doc.setLogicalStyle(r.getElement(idx + k).getStartOffset(), st);
                    }
                } finally {
                    autoStyling = false;
                }
                int shift = up ? -(nbLen + 1) : nbLen + 1;
                caret.setDot(mark + shift);
                caret.moveDot(dot + shift);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        });
        repaint();
    }

    // =====================================================================
    // Files
    // =====================================================================

    static boolean isRtf(File f) {
        return f != null && f.getName().toLowerCase(Locale.ROOT).endsWith(".rtf");
    }

    void load(File f) throws IOException {
        StyledDocument d = new DefaultStyledDocument();
        try {
            if (isRtf(f)) {
                readRtf(f, d);
                eol = "\r\n";
                encoding = "Rich Text";
            } else {
                String t = decode(Files.readAllBytes(f.toPath()));
                eol = t.contains("\r\n") ? "\r\n" : t.contains("\n") ? "\n" : t.contains("\r") ? "\r" : "\r\n";
                d.insertString(0, t.replace("\r\n", "\n").replace('\r', '\n'), null);
            }
        } catch (BadLocationException e) {
            throw new IOException(e);
        }
        useDocument(d);
        file = f;
        plainWarned = false;
        markClean();
    }

    /** Writes the document to f as .rtf (formatting kept) or plain text with this file's encoding + line ending. */
    void write(File f) throws IOException {
        try {
            if (isRtf(f)) {
                writeRtf(f);
                encoding = "Rich Text";
            } else {
                if (encoding.equals("Rich Text")) encoding = "UTF-8";
                String t = text().replace("\n", eol);
                byte[] body = t.getBytes(encoding.equals("ANSI") ? ANSI : StandardCharsets.UTF_8);
                if (encoding.equals("UTF-8 with BOM")) {
                    byte[] withBom = new byte[body.length + 3];
                    withBom[0] = (byte) 0xEF; withBom[1] = (byte) 0xBB; withBom[2] = (byte) 0xBF;
                    System.arraycopy(body, 0, withBom, 3, body.length);
                    body = withBom;
                }
                Files.write(f.toPath(), body);
            }
        } catch (BadLocationException e) {
            throw new IOException(e);
        }
        file = f;
        markClean();
    }

    /** Session copy for restoring unsaved tabs; doesn't change the file or dirty flag. */
    void writeSessionCopy(File f) throws IOException {
        try {
            writeRtf(f);
        } catch (BadLocationException e) {
            throw new IOException(e);
        }
    }

    void loadSessionCopy(File f) throws IOException {
        StyledDocument d = new DefaultStyledDocument();
        try {
            readRtf(f, d);
        } catch (BadLocationException e) {
            throw new IOException(e);
        }
        useDocument(d);
    }

    private static void readRtf(File f, StyledDocument into) throws IOException, BadLocationException {
        DefaultStyledDocument raw = new DefaultStyledDocument();
        try (InputStream in = Files.newInputStream(f.toPath())) {
            new RTFEditorKit().read(in, raw, 0);
        }
        int len = raw.getLength();
        if (len > 0 && raw.getText(len - 1, 1).equals("\n")) len--; // the RTF reader adds one extra line break
        // Java's RTF reader stamps foreground = black on EVERY run (even with no colour in the file): that made
        // restored tabs black-on-dark and switched the syntax colours off. Pure black = "no colour of mine".
        for (int pos = 0; pos < len; ) {
            Element run = raw.getCharacterElement(pos);
            int end = Math.min(run.getEndOffset(), len);
            AttributeSet a = run.getAttributes();
            if (Color.BLACK.equals(a.getAttribute(StyleConstants.Foreground))) {
                SimpleAttributeSet clean = new SimpleAttributeSet(a);
                clean.removeAttribute(StyleConstants.Foreground);
                raw.setCharacterAttributes(pos, end - pos, clean, true);
            }
            pos = end;
        }
        copyRuns(raw, 0, len, into, 0);
    }

    /** Writes a copy at 100% zoom so the saved font size isn't the zoomed one. */
    private void writeRtf(File f) throws IOException, BadLocationException {
        DefaultStyledDocument out = new DefaultStyledDocument();
        applyBaseFont(out, baseFont.getSize());
        copyRuns(doc, 0, doc.getLength(), out, 0);
        try (OutputStream os = Files.newOutputStream(f.toPath())) {
            new RTFEditorKit().write(os, out, 0, out.getLength());
        }
    }

    private String decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            encoding = "UTF-8 with BOM";
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            encoding = "UTF-8";
            return s;
        } catch (CharacterCodingException notUtf8) {
            encoding = "ANSI";
            return new String(bytes, ANSI);
        }
    }
}
