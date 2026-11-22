/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.awt.Graphics;
import java.awt.Shape;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;

/**
 * Syntax colours inside code blocks: keywords, strings, numbers, comments, operators, ( ), [ ], { }.
 * Only the screen drawing changes: the document text and its saved file stay plain.
 */
final class CodeColors {

    enum Tok { PLAIN, KEYWORD, STRING, NUMBER, COMMENT, OPERATOR, PAREN, BRACKET, BRACE, STAR }

    /**
     * Lighter colouring for ordinary note lines (prose mixed with bits of code): // comments,
     * every *, ( ) [ ] { } and operators. Apostrophes, dots, commas and colons stay plain so French text reads normally.
     */
    static Tok[] classifyNote(String line) {
        Tok[] out = new Tok[line.length()];
        java.util.Arrays.fill(out, Tok.PLAIN);
        int n = line.length();
        for (int i = 0; i < n; i++) {
            char c = line.charAt(i);
            boolean url = i > 0 && line.charAt(i - 1) == ':';            // http://, https://
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/' && !url) {
                fill(out, i, n, Tok.COMMENT);
                return out;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                int end = line.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                fill(out, i, end, Tok.COMMENT);
                i = end - 1;
                continue;
            }
            switch (c) {
                case '*' -> out[i] = Tok.STAR;
                case '(', ')' -> out[i] = Tok.PAREN;
                case '[', ']' -> out[i] = Tok.BRACKET;
                case '{', '}' -> out[i] = Tok.BRACE;
                case '=', '+', '/', '%', '<', '>', '!', '&', '|', '^', '~', ';' -> out[i] = Tok.OPERATOR;
                case '-' -> {   // a hyphen inside a word (auto-complete) is not an operator
                    boolean inWord = i > 0 && i + 1 < n && Character.isLetter(line.charAt(i - 1)) && Character.isLetter(line.charAt(i + 1));
                    if (!inWord) out[i] = Tok.OPERATOR;
                }
                default -> { /* plain */ }
            }
        }
        return out;
    }

    private static final Set<String> COMMON_KEYWORDS = new HashSet<>(List.of(
            "if", "else", "elif", "for", "foreach", "while", "do", "switch", "case", "default", "break", "continue",
            "return", "new", "delete", "class", "struct", "interface", "enum", "record", "extends", "implements",
            "public", "private", "protected", "internal", "static", "final", "abstract", "virtual", "override",
            "const", "readonly", "void", "int", "long", "short", "byte", "double", "float", "char", "boolean", "bool",
            "string", "String", "var", "let", "def", "function", "lambda", "import", "from", "package", "using",
            "namespace", "try", "catch", "except", "finally", "throw", "throws", "raise", "this", "super", "self",
            "null", "None", "nullptr", "true", "false", "True", "False", "and", "or", "not", "in", "is", "async",
            "await", "yield", "template", "typename", "auto", "unsigned", "sizeof", "instanceof", "typeof", "with",
            "as", "pass", "print", "echo", "sudo", "select", "from", "where", "SELECT", "FROM", "WHERE", "INSERT",
            "INTO", "VALUES", "UPDATE", "DELETE", "CREATE", "TABLE", "JOIN", "ON", "AND", "OR", "NOT", "NULL"));

    private CodeColors() {}

    /** Token type of every character of one line. */
    static Tok[] classify(String line, Language lang) {
        Tok[] out = new Tok[line.length()];
        java.util.Arrays.fill(out, Tok.PLAIN);
        boolean hashComments = lang == Language.PYTHON || lang == Language.LINUX;
        boolean dashComments = lang == Language.SQL;
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            // comments
            if ((c == '/' && i + 1 < n && line.charAt(i + 1) == '/')
                    || (c == '#' && hashComments)
                    || (c == '-' && dashComments && i + 1 < n && line.charAt(i + 1) == '-')) {
                fill(out, i, n, Tok.COMMENT);
                return out;
            }
            if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                int end = line.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                fill(out, i, end, Tok.COMMENT);
                i = end;
                continue;
            }
            if (c == '*' && line.strip().startsWith("*") && line.indexOf('*') == i) { // " * javadoc line"
                fill(out, i, n, Tok.COMMENT);
                return out;
            }
            // strings
            if (c == '"' || c == '\'' || c == '`') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c) j += line.charAt(j) == '\\' ? 2 : 1;
                int end = Math.min(n, j + 1);
                fill(out, i, end, Tok.STRING);
                i = end;
                continue;
            }
            // numbers
            if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.' || line.charAt(j) == '_')) j++;
                fill(out, i, j, Tok.NUMBER);
                i = j;
                continue;
            }
            // words: keywords, annotations (@Override), #include
            if (Character.isLetter(c) || c == '_' || c == '$' || c == '@' || c == '#') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_' || line.charAt(j) == '$')) j++;
                String w = line.substring(i, j);
                boolean kw = c == '@' || c == '#' || COMMON_KEYWORDS.contains(w) || isLangKeyword(w, lang);
                if (kw) fill(out, i, j, Tok.KEYWORD);
                i = j;
                continue;
            }
            switch (c) {
                case '(', ')' -> out[i] = Tok.PAREN;
                case '[', ']' -> out[i] = Tok.BRACKET;
                case '{', '}' -> out[i] = Tok.BRACE;
                case '+', '-', '*', '/', '%', '=', '<', '>', '!', '&', '|', '^', '~', '?', ':', ';', '.', ',' -> out[i] = Tok.OPERATOR;
                default -> { /* plain */ }
            }
            i++;
        }
        return out;
    }

    private static boolean isLangKeyword(String w, Language lang) {
        if (lang == null) return false;
        for (String k : lang.keywords) if (k.equals(w)) return true;
        return false;
    }

    private static void fill(Tok[] out, int from, int to, Tok t) {
        for (int k = from; k < to && k < out.length; k++) out[k] = t;
    }

    /** Editor kit whose text runs paint syntax colours when they sit in a code block. */
    static final class Kit extends StyledEditorKit {
        private static final long serialVersionUID = 1L;
        private final transient ViewFactory base = new StyledEditorKit().getViewFactory();
        private final transient ViewFactory factory = elem ->
                AbstractDocument.ContentElementName.equals(elem.getName()) ? new CodeView(elem) : base.create(elem);

        @Override public ViewFactory getViewFactory() { return factory; }
    }

    /** A normal text run, except inside code blocks where each token is drawn in its own colour. */
    static final class CodeView extends LabelView {
        CodeView(Element e) { super(e); }

        @Override
        public void paint(Graphics g, Shape a) {
            Element para = getElement().getParentElement();
            if (!(getContainer() instanceof NoteEditor ed) || para == null) {
                super.paint(g, a);
                return;
            }
            boolean code = ed.isCode(para);
            // ordinary lines: only in prise-de-note mode, never over a colour you picked yourself, never on ===== / ***** lines
            if (!code && (!ed.noteMode || getElement().getAttributes().isDefined(javax.swing.text.StyleConstants.Foreground))) {
                super.paint(g, a);
                return;
            }
            try {
                int ps = para.getStartOffset();
                int pe = Math.min(para.getEndOffset() - 1, ed.doc.getLength());
                String line = ed.doc.getText(ps, pe - ps);
                if (!code && (NoteEditor.isEqualsLine(line) || NoteEditor.isStarsLine(line))) {
                    super.paint(g, a);
                    return;
                }
                Tok[] toks = code ? classify(line, ed.language) : classifyNote(line);
                int p0 = getStartOffset(), p1 = Math.min(getEndOffset(), pe);
                checkPainter();
                GlyphPainter painter = getGlyphPainter();
                g.setFont(getFont());
                int i = p0;
                while (i < p1) {
                    Tok t = toks[i - ps];
                    int j = i + 1;
                    while (j < p1 && toks[j - ps] == t) j++;
                    g.setColor(t == Tok.PLAIN && !code ? getForeground() : ed.syntaxColor(t));
                    painter.paint(this, g, a, i, j);
                    i = j;
                }
            } catch (BadLocationException | RuntimeException e) {
                super.paint(g, a);
            }
        }
    }
}
