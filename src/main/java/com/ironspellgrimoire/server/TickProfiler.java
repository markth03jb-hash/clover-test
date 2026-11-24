package com.ironspellgrimoire.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sampling profiler for the automatic test run (server/GameTests): every couple of milliseconds it looks at what the
 * server and render threads are doing, and at the end reports how much of their busy time was this mod's code - by
 * system (the outermost mod method on the stack: the event handler or entity tick that called in) and by hot spot (the
 * innermost). Off unless the tests run; costs nothing otherwise.
 */
public final class TickProfiler {
    private TickProfiler() {}

    private static final String OURS = "com.ironspellgrimoire.";
    private static volatile boolean on;
    private static final Map<String, Counts> BY = new HashMap<>();

    static final class Counts {
        long busy, mod;
        final Map<String, Integer> entry = new HashMap<>(), hot = new HashMap<>();
    }

    public static void start() {
        if (on) return;
        on = true;
        Thread s = new Thread(TickProfiler::run, "ISG tick profiler");
        s.setDaemon(true);
        s.start();
    }

    public static void stop() { on = false; }

    private static void run() {
        while (on) {
            for (Thread th : Thread.getAllStackTraces().keySet()) {
                String n = th.getName();
                if (!n.equals("Server thread") && !n.equals("Render thread")) continue;
                StackTraceElement[] st = th.getStackTrace();
                if (st.length == 0 || idle(st[0])) continue;
                Counts c;
                synchronized (BY) { c = BY.computeIfAbsent(n, k -> new Counts()); }
                c.busy++;
                String inner = null, outer = null;
                for (StackTraceElement e : st) {
                    if (!e.getClassName().startsWith(OURS) || e.getClassName().contains("TickProfiler") || e.getClassName().contains("GameTest")) continue;
                    String m = e.getClassName().substring(OURS.length()) + "." + e.getMethodName();
                    if (inner == null) inner = m;
                    outer = m;
                }
                if (inner == null) continue;
                c.mod++;
                c.hot.merge(inner, 1, Integer::sum);
                c.entry.merge(outer, 1, Integer::sum);
            }
            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
        }
    }

    /** Waiting for the next tick or frame: not work. */
    private static boolean idle(StackTraceElement top) {
        String c = top.getClassName(), m = top.getMethodName();
        return m.equals("park") || m.equals("sleep") || m.equals("parkNanos") || m.equals("wait") || c.contains("LockSupport")
                || m.equals("nglfwSwapBuffers") || m.equals("glfwSwapBuffers") || m.equals("nglfwPollEvents");
    }

    /** The report's lines ("PROF | ..."): per thread, the mod's share of busy time and the top systems and hot spots. */
    public static List<String> report() {
        List<String> out = new ArrayList<>();
        synchronized (BY) {
            for (Map.Entry<String, Counts> t : BY.entrySet()) {
                Counts c = t.getValue();
                if (c.busy == 0) continue;
                out.add(String.format("PROF | %s | this mod: %.1f%% of busy time (%d samples)", t.getKey(), 100.0 * c.mod / c.busy, c.busy));
                top(out, t.getKey() + " system", c.entry, c.busy, 15);
                top(out, t.getKey() + " hot spot", c.hot, c.busy, 15);
            }
        }
        return out;
    }

    private static void top(List<String> out, String what, Map<String, Integer> m, long busy, int n) {
        m.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(n)
                .forEach(e -> out.add(String.format("PROF | %s | %5.2f%% %s", what, 100.0 * e.getValue() / busy, e.getKey())));
    }
}
