/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.LookAndFeel;

import com.formdev.flatlaf.FlatLaf;

/** View > Theme. "Night Blue" is this app's own theme; the rest come from FlatLaf's theme pack. */
final class Theme {

    record Entry(String name, String className, boolean dark, Map<String, String> extras) {}

    private static final String CORE = "com.formdev.flatlaf.";
    private static final String IJ = "com.formdev.flatlaf.intellijthemes.";
    private static final String MT = IJ + "materialthemeuilite.";

    private static final Map<String, String> NIGHT_BLUE = Map.ofEntries(
            Map.entry("@background", "#0e1729"),
            Map.entry("@foreground", "#d5def0"),
            Map.entry("@accentColor", "#4f8cff"),
            Map.entry("@selectionBackground", "#2a4f8f"),
            Map.entry("@selectionForeground", "#ffffff"),
            Map.entry("TextPane.background", "#111d33"),
            Map.entry("TextPane.foreground", "#e3e9f5"),
            Map.entry("TextPane.selectionBackground", "#2a4f8f"),
            Map.entry("TextPane.caretForeground", "#7fb0ff"),
            Map.entry("TitlePane.background", "#0b1322"),
            Map.entry("Component.focusColor", "#4f8cff"));

    static final List<Entry> ALL = List.of(
            new Entry("Night Blue", CORE + "FlatDarkLaf", true, NIGHT_BLUE),
            new Entry("Deep Ocean", MT + "FlatMTMaterialDeepOceanIJTheme", true, null),
            new Entry("Midnight Blue", IJ + "FlatGradiantoMidnightBlueIJTheme", true, null),
            new Entry("Night Owl", MT + "FlatMTNightOwlIJTheme", true, null),
            new Entry("Moonlight", MT + "FlatMTMoonlightIJTheme", true, null),
            new Entry("Nord", IJ + "FlatNordIJTheme", true, null),
            new Entry("One Dark", IJ + "FlatOneDarkIJTheme", true, null),
            new Entry("Dracula", IJ + "FlatDraculaIJTheme", true, null),
            new Entry("GitHub Dark", MT + "FlatMTGitHubDarkIJTheme", true, null),
            new Entry("Palenight", MT + "FlatMTMaterialPalenightIJTheme", true, null),
            new Entry("Monokai Pro", IJ + "FlatMonokaiProIJTheme", true, null),
            new Entry("Solarized Dark", IJ + "FlatSolarizedDarkIJTheme", true, null),
            new Entry("Gruvbox Dark", IJ + "FlatGruvboxDarkHardIJTheme", true, null),
            new Entry("Arc Dark", IJ + "FlatArcDarkIJTheme", true, null),
            new Entry("Carbon", IJ + "FlatCarbonIJTheme", true, null),
            new Entry("Xcode Dark", IJ + "FlatXcodeDarkIJTheme", true, null),
            new Entry("Mac Dark", CORE + "themes.FlatMacDarkLaf", true, null),
            new Entry("Darcula", CORE + "FlatDarculaLaf", true, null),
            new Entry("Light", CORE + "FlatLightLaf", false, null),
            new Entry("Mac Light", CORE + "themes.FlatMacLightLaf", false, null),
            new Entry("GitHub Light", MT + "FlatMTGitHubIJTheme", false, null),
            new Entry("Atom One Light", MT + "FlatMTAtomOneLightIJTheme", false, null),
            new Entry("Light Owl", MT + "FlatMTLightOwlIJTheme", false, null),
            new Entry("Solarized Light", IJ + "FlatSolarizedLightIJTheme", false, null),
            new Entry("Cyan Light", IJ + "FlatCyanLightIJTheme", false, null),
            new Entry("Arc Light", IJ + "FlatArcIJTheme", false, null));

    private Theme() {}

    static Entry byName(String name) {
        for (Entry e : ALL) if (e.name().equals(name)) return e;
        return ALL.get(0);
    }

    /** Installs the theme. Call FlatLaf.updateUI() afterwards when windows already exist. */
    static void apply(Entry e) {
        Map<String, String> extras = new HashMap<>();
        extras.put("defaultFont", "+1");                 // slightly larger UI text
        extras.put("Component.arc", "10");
        extras.put("Button.arc", "10");
        extras.put("TextComponent.arc", "10");
        extras.put("ScrollBar.width", "12");
        extras.put("ScrollBar.thumbArc", "999");
        extras.put("ScrollBar.thumbInsets", "2,2,2,2");
        extras.put("PopupMenu.borderCornerRadius", "10");
        extras.put("MenuItem.selectionArc", "8");
        if (e.extras() != null) extras.putAll(e.extras());
        FlatLaf.setGlobalExtraDefaults(extras);
        try {
            FlatLaf.setup((LookAndFeel) Class.forName(e.className()).getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException ex) {
            FlatLaf.setup(new com.formdev.flatlaf.FlatDarkLaf());
        }
    }
}
