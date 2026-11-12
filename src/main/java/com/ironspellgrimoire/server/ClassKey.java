package com.ironspellgrimoire.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fingerprints for the automatic test runs (GameTests, client/UiShots), so they skip what hasn't changed since it last
 * passed. A test's key is a hash of the compiled code it depends on: its own classes and every class of this mod they
 * reach, followed through the class files' constant pools - so a change anywhere in that code (or a new build of it)
 * gives a new key and the test runs again; an untouched test keeps its key and is skipped.
 * <p>The keys of tests that passed are kept between runs in a small text file ({@link Cache}).
 */
public final class ClassKey {
    private ClassKey() {}

    private static final Pattern OURS = Pattern.compile("com/ironspellgrimoire/[A-Za-z0-9_/$]+");
    private static final Map<String, byte[]> BYTES = new java.util.HashMap<>();

    /** The key for these roots (classes) plus any extra text that should change it (an id, a version). */
    public static String of(String extra, Class<?>... roots) {
        try {
            Map<String, byte[]> all = new TreeMap<>();
            Deque<String> todo = new ArrayDeque<>();
            for (Class<?> c : roots) if (c != null) todo.add(c.getName().replace('.', '/'));
            ClassLoader cl = roots.length > 0 && roots[0] != null ? roots[0].getClassLoader() : ClassKey.class.getClassLoader();
            while (!todo.isEmpty()) {
                String n = todo.poll();
                if (all.containsKey(n)) continue;
                byte[] b = bytes(cl, n);
                if (b == null) continue;
                all.put(n, b);
                for (String ref : refs(b)) if (!all.containsKey(ref)) todo.add(ref);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(extra.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> e : all.entrySet()) { md.update(e.getKey().getBytes(StandardCharsets.UTF_8)); md.update(e.getValue()); }
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "nokey-" + System.nanoTime();   // can't fingerprint it: always run it
        }
    }

    /** A key for one class's own code only (the test harness itself: changing the tests reruns them all). */
    public static String single(Class<?> c) {
        try {
            byte[] b = bytes(c.getClassLoader(), c.getName().replace('.', '/'));
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            if (b != null) md.update(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            return sb.substring(0, 12);
        } catch (Exception e) { return "nokey"; }
    }

    private static byte[] bytes(ClassLoader cl, String internal) {
        return BYTES.computeIfAbsent(internal, n -> {
            try (InputStream in = cl.getResourceAsStream(n + ".class")) {
                if (in == null) return null;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                in.transferTo(bo);
                return bo.toByteArray();
            } catch (IOException e) { return null; }
        });
    }

    /** This mod's classes named anywhere in a class file's constant pool (class refs and type descriptors). */
    private static List<String> refs(byte[] b) {
        List<String> out = new ArrayList<>();
        int p = 8, n = u2(b, p);
        p += 2;
        for (int i = 1; i < n; i++) {
            int tag = b[p] & 0xFF;
            p++;
            switch (tag) {
                case 1 -> {
                    int len = u2(b, p);
                    String s = new String(b, p + 2, len, StandardCharsets.UTF_8);
                    Matcher m = OURS.matcher(s);
                    while (m.find()) out.add(m.group());
                    p += 2 + len;
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> p += 4;
                case 5, 6 -> { p += 8; i++; }
                case 7, 8, 16, 19, 20 -> p += 2;
                case 15 -> p += 3;
                default -> { return out; }   // something unexpected: stop reading
            }
        }
        return out;
    }

    private static int u2(byte[] b, int p) { return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF); }

    /** The keys of what passed last time: "name=key" lines in a file in the game folder. */
    public static final class Cache {
        private final Path file;
        private final Map<String, String> old = new LinkedHashMap<>(), now = new LinkedHashMap<>();

        public Cache(Path file) {
            this.file = file;
            try {
                if (Files.exists(file)) for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int i = l.indexOf('=');
                    if (i > 0) old.put(l.substring(0, i), l.substring(i + 1));
                }
            } catch (IOException ignored) {}
        }

        /** Whether this test passed last time with the same key (then it can be skipped). */
        public boolean unchanged(String name, String key) { return key.equals(old.get(name)); }
        /** Records a pass (or a skip of something that passed before). */
        public void passed(String name, String key) { now.put(name, key); }

        public void save() {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> e : now.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
            try { Files.write(file, lines, StandardCharsets.UTF_8); } catch (IOException ignored) {}
        }
    }
}
