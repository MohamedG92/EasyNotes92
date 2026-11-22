/*
 * EasyNotes92 — Copyright (c) 2026 Mohamed Iraqi Houssaini. Creator and author.
 * Free software, licensed under the PolyForm Noncommercial License 1.0.0 (see LICENSE.md and NOTICE.md).
 * You may modify and share it, but you may NOT sell it or use it commercially.
 */
package com.esisa.notepad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Only one EasyNotes92 window owns your tabs. Starting the app again (shortcut, "Open with")
 * hands the request to the window that is already open instead of opening an empty second one.
 * Talks over a local-only socket (127.0.0.1) protected by a random token.
 */
final class SingleInstance {

    private SingleInstance() {}

    /** First instance: listen for later launches. Each message is a list of file paths (empty = just show the window). */
    static void listen(Path dir, Consumer<List<String>> onRequest) {
        try {
            ServerSocket server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
            String token = UUID.randomUUID().toString();
            Files.writeString(dir.resolve("instance.port"), server.getLocalPort() + "\n" + token, StandardCharsets.UTF_8);
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                        s.setSoTimeout(2000);
                        if (!token.equals(in.readLine())) continue;   // not from our own app: ignore
                        List<String> files = new ArrayList<>();
                        for (String line; (line = in.readLine()) != null && !line.equals("END"); ) files.add(line);
                        onRequest.accept(files);
                    } catch (IOException ignored) {
                        // a broken request: wait for the next one
                    }
                }
            }, "single-instance");
            t.setDaemon(true);
            t.start();
        } catch (IOException ignored) {
            // can't listen: the app still works, a second launch just opens its own window
        }
    }

    /** Later instance: pass the files to the running window. Returns false if no window answered. */
    static boolean sendToRunning(Path dir, String[] files) {
        try {
            List<String> lines = Files.readAllLines(dir.resolve("instance.port"), StandardCharsets.UTF_8);
            int port = Integer.parseInt(lines.get(0).trim());
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                out.println(lines.get(1).trim());
                for (String f : files) out.println(Path.of(f).toAbsolutePath());
                out.println("END");
                return !out.checkError();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
