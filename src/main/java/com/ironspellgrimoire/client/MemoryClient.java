package com.ironspellgrimoire.client;

import com.ironspellgrimoire.network.FxPacket;

/** The client's copy of your Lost Memory Pages (server/MemoryPages, shape 77): pages read, chapters done, the chapter you're on. */
public final class MemoryClient {
    private MemoryClient() {}

    private static int read, done, chapter;

    public static void set(FxPacket p) { read = p.sweep; done = (int) p.h; chapter = (int) p.a0; }
    public static boolean read(int ch, int page) { return (read & com.ironspellgrimoire.server.MemoryStory.bit(ch, page)) != 0; }
    public static boolean done(int ch) { return (done & (1 << ch)) != 0; }
    public static int chapter() { return chapter; }
}
