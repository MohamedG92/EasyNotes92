/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.io.File;
import java.util.Locale;

/** The language a "prise de note" is about: picks the tab logo and adds that language's keywords to autocomplete. */
enum Language {
    TEXT("Texte", "txt", "notes"),
    PYTHON("Python", "py", "python",
            "def", "class", "import", "from", "return", "print", "range", "append", "lambda", "self",
            "True", "False", "None", "elif", "while", "except", "finally", "yield", "dict", "list", "tuple", "input"),
    JAVA("Java", "java", "java",
            "public", "private", "protected", "static", "final", "class", "interface", "extends", "implements",
            "abstract", "return", "String", "Integer", "Double", "Boolean", "Object", "ArrayList", "HashMap",
            "System", "println", "toString", "equals", "hashCode", "Override", "super", "this", "throws",
            "Exception", "package", "import", "instanceof", "constructeur", "getter", "setter"),
    CSHARP("C#", "cs", "csharp",
            "using", "namespace", "public", "private", "protected", "internal", "static", "class", "interface",
            "override", "virtual", "return", "string", "Console", "WriteLine", "ReadLine", "Parse", "ToString",
            "List", "Dictionary", "foreach", "async", "await", "readonly", "struct"),
    CPP("C++", "cpp", "cpp",
            "include", "iostream", "namespace", "std", "cout", "endl", "class", "public", "private",
            "protected", "virtual", "template", "typename", "vector", "string", "return", "const", "nullptr", "struct"),
    C("C", "c", "c",
            "include", "stdio", "stdlib", "printf", "scanf", "malloc", "free", "struct", "typedef", "return",
            "sizeof", "const", "unsigned", "char", "void", "while"),
    JAVASCRIPT("JavaScript", "js", "javascript",
            "function", "const", "return", "console", "document", "window", "async", "await", "Promise",
            "export", "import", "default", "undefined", "addEventListener", "querySelector", "JSON", "fetch"),
    REACT("React", "jsx", "react",
            "import", "export", "default", "function", "return", "useState", "useEffect", "useRef",
            "useContext", "props", "component", "className", "onClick", "children", "const"),
    HTML("HTML / CSS", "html", "html",
            "html", "head", "body", "div", "span", "class", "style", "script", "link", "display", "flex",
            "grid", "margin", "padding", "border", "color", "background"),
    SQL("SQL", "sql", "sql",
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "DELETE", "CREATE", "TABLE",
            "PRIMARY", "FOREIGN", "REFERENCES", "JOIN", "INNER", "LEFT", "GROUP", "ORDER", "HAVING", "COUNT", "DISTINCT"),
    LINUX("Linux", "sh", "linux",
            "sudo", "chmod", "chown", "grep", "echo", "mkdir", "touch", "apt", "systemctl", "service", "cat",
            "less", "tail", "head", "find", "export", "bash", "ssh");

    final String label;
    final String ext;
    /** Suggested file name when saving a new note, e.g. python.txt (same style as your la_prise_de_note folder). */
    final String noteFileBase;
    final String[] keywords;

    Language(String label, String ext, String noteFileBase, String... keywords) {
        this.label = label;
        this.ext = ext;
        this.noteFileBase = noteFileBase;
        this.keywords = keywords;
    }

    /** Tab title of an unsaved note, like "Prise.py" in the drawing. */
    String untitledNote() {
        return "Prise." + ext;
    }

    /** Words that give a language away when they show up in a note (all lower case). */
    private String[] markers() {
        return switch (this) {
            case PYTHON -> new String[] {"def ", "print(", "elif ", "self.", "python", "len(", "range("};
            case JAVA -> new String[] {"public class", "system.out", "extends ", "java", "jvm", "jdk", "@override"};
            case CSHARP -> new String[] {"console.writeline", "console.readline", "namespace ", "using system", "c#", ".net", ".csproj"};
            case CPP -> new String[] {"#include <iostream>", "cout", "std::", "cin >>", "c++"};
            case C -> new String[] {"printf(", "scanf(", "#include <stdio", "malloc("};
            case JAVASCRIPT -> new String[] {"console.log", "require(", "npm ", "const ", "=>", "express", "module.exports", "function "};
            case REACT -> new String[] {"usestate", "useeffect", "react", "jsx", "props.", "classname"};
            case HTML -> new String[] {"<div", "<html", "<body", "css", "</"};
            case SQL -> new String[] {"select ", "insert into", "create table", "inner join", "primary key"};
            case LINUX -> new String[] {"sudo ", "apt ", "chmod", "ls -", "ubuntu", "bash", "grep ", "cd /"};
            default -> new String[0];
        };
    }

    /** Guesses the language from what the note talks about (used when the file name doesn't say). TEXT if unsure. */
    static Language fromText(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        Language best = TEXT;
        int bestScore = 0;
        for (Language l : values()) {
            int score = 0;
            for (String m : l.markers()) {
                for (int i = t.indexOf(m); i >= 0; i = t.indexOf(m, i + m.length())) score++;
            }
            if (score > bestScore) {
                best = l;
                bestScore = score;
            }
        }
        return bestScore >= 3 ? best : TEXT;
    }

    /** Guesses the language from a file name: python.txt, java_first.txt, csharp.txt, Main.java ... */
    static Language detect(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".jsx") || n.endsWith(".tsx") || n.contains("react") || n.contains("mern")) return REACT;
        if (n.endsWith(".js") || n.endsWith(".ts") || n.contains("javascript") || n.matches(".*\\bjs\\b.*")) return JAVASCRIPT;
        if (n.endsWith(".cs") || n.contains("csharp") || n.contains("c#")) return CSHARP;
        if (n.endsWith(".cpp") || n.endsWith(".hpp") || n.contains("cpp") || n.contains("c++")) return CPP;
        if (n.endsWith(".py") || n.contains("python")) return PYTHON;
        if (n.endsWith(".java") || n.contains("java")) return JAVA;
        if (n.endsWith(".html") || n.endsWith(".css") || n.contains("html") || n.contains("css")) return HTML;
        if (n.endsWith(".sql") || n.contains("sql")) return SQL;
        if (n.endsWith(".sh") || n.contains("linux") || n.contains("ubuntu") || n.contains("ubinto") || n.contains("bash")) return LINUX;
        if (n.endsWith(".c") || n.endsWith(".h")) return C;
        return TEXT;
    }
}
