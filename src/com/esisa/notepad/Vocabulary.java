/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words from your previous notes, ranked by how often you used them. Feeds the grey
 * Tab-completion and the Ctrl+Space list. Each source (a file on disk, an open tab) is
 * counted separately so re-scanning a file replaces its old counts instead of adding up.
 */
final class Vocabulary {

    private static final Pattern WORD = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]{3,}");
    private static final int MAX_FILE_BYTES = 2_000_000;

    private final Map<String, Map<String, Integer>> sources = new HashMap<>();
    private volatile Map<String, Integer> merged = Map.of();

    synchronized void setSource(String key, String text) {
        sources.put(key, count(text));
        rebuild();
    }

    synchronized void removeSource(String key) {
        if (sources.remove(key) != null) rebuild();
    }

    /** Reads note files in a background thread (doesn't slow down startup). */
    void scanAsync(List<File> files) {
        Thread t = new Thread(() -> {
            for (File f : files) {
                if (!isTextNote(f) || !f.isFile() || f.length() > MAX_FILE_BYTES) continue;
                try {
                    setSource("file:" + f.getAbsolutePath().toLowerCase(Locale.ROOT),
                            new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    // unreadable file: skip it
                }
            }
        }, "vocabulary-scan");
        t.setDaemon(true);
        t.start();
    }

    static boolean isTextNote(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(txt|md|py|java|cs|cpp|hpp|c|h|js|jsx|ts|tsx|html|css|sql|sh)$");
    }

    /** The rest of the best word starting with prefix, or null. */
    String complete(String prefix, Language lang) {
        List<String> best = candidates(prefix, lang, 1);
        return best.isEmpty() ? null : best.get(0).substring(prefix.length());
    }

    List<String> candidates(String prefix, Language lang, int max) {
        if (prefix.length() < 2) return List.of();
        Map<String, Integer> scored = new HashMap<>();
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            if (matches(e.getKey(), prefix)) scored.put(e.getKey(), e.getValue());
        }
        if (lang != null) {
            for (String kw : lang.keywords) {
                if (matches(kw, prefix)) scored.merge(kw, 3, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> list = new ArrayList<>(scored.entrySet());
        list.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparingInt(e -> e.getKey().length())
                .thenComparing(Map.Entry::getKey));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(max, list.size()); i++) out.add(list.get(i).getKey());
        return out;
    }

    private static boolean matches(String word, String prefix) {
        return word.length() > prefix.length() && word.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static Map<String, Integer> count(String text) {
        Map<String, Integer> m = new HashMap<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) m.merge(matcher.group(), 1, Integer::sum);
        return m;
    }

    private void rebuild() {
        Map<String, Integer> m = new HashMap<>();
        for (Map<String, Integer> s : sources.values()) {
            for (Map.Entry<String, Integer> e : s.entrySet()) m.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        merged = m;
    }
}
