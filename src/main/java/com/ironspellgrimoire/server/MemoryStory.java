package com.ironspellgrimoire.server;

/**
 * The story told by the Lost Memory Pages (server/MemoryPages): the tale of Elowen, the mage who first bound the
 * Grimoire Tower. An original story for this mod. Three chapters of six pages - five torn away by the great foes of
 * the world, the sixth held by that chapter's Memory Guardian. Shared by both sides (the client reads it in the
 * grimoire's Memories tab).
 */
public final class MemoryStory {
    private MemoryStory() {}

    public static final String[] CHAPTERS = {"I. The Copyist", "II. The Hundred Books", "III. The Last Binding"};
    /** Each chapter's guardian: name, and the line on its title card. */
    public static final String[] GUARDIANS = {"The Copyist's Echo", "Taddeus, Warden of the Stair", "Elowen, the First Binder"};
    public static final String[] EPITHETS = {"The first page she ever wrote", "He held the door a night and a day", "She wrote herself into the tower"};

    public static final String[][] PAGES = {
            {
                    "Before the tower there was only a hill, and a girl named Elowen who could not cast. Every child in her village had been chosen by a grimoire. She had been chosen by nothing - so she carried ink instead, and copied other mages' spells by hand.",
                    "Copied spells do not work. Everyone told her so. But Elowen noticed that her pages grew warm as she wrote them, and warmer still when she wrote them for someone else. Ink, she wrote in a margin, remembers.",
                    "The winter the river froze, a mage died on the road with his grimoire in his hand. The book did not crumble, as books do. It lay open in the snow, turning its own pages, looking for someone. Elowen took it home.",
                    "It would not choose her. It would not choose anyone. So she tied it shut with a thread soaked in her ink, and for the first time a grimoire without a mage lay quiet. She called the knot a binding.",
                    "Word spread. Lost grimoires came to her door in sacks, in coffins, once in the jaws of a wolf. Her cottage could not hold them. On the hill above the village, she laid the first stone.",
                    "I was the first thing she ever wrote that worked: a copy of herself, to keep the books while she slept. If you are reading this, you beat me. Good. She always said the tower would need someone stronger than her."
            },
            {
                    "By her fortieth year the tower had nine floors, a hundred bound books, and a knight named Taddeus guarding its door. He had no magic either. He said that made them even.",
                    "The books changed in the tower. Bound, they stopped hunting for their dead mages and began, slowly, to listen. One morning a shepherd's boy climbed the stairs, and a book unbound itself and flew into his hands.",
                    "A grimoire choosing a shepherd. The lords of the capital did not like it. They sent letters, then tax men, then soldiers. Taddeus met each at the door and sent them home, their pride broken and their bones whole.",
                    "The last army brought mages. Taddeus held the stair for a night and a day. When it was over the tower still stood, and he did not. Elowen carved his name into every step.",
                    "She could not raise the dead. But ink remembers. She bound his memory into the door he had kept, so it would go on keeping it - and so she would not be the only one left who remembered him.",
                    "You came up my stair and I let you. That is what the door is for: not to keep everyone out, only those who would take the books by force. Go on. She is waiting at the top. She has waited a long time."
            },
            {
                    "Elowen grew old the way ink fades: slowly, then all at once. She worried what the books would do without a binder. Unbound, they would hunt again. Bound forever, they would never choose anyone.",
                    "So she wrote one last binding, and it was not for a book. It was for the tower itself: a knot loose enough to let a grimoire choose, and tight enough to let it choose well.",
                    "Every binding needs ink, and this one needed more than a lifetime of it. On the last night she climbed to the top of the tower with an empty pen, and filled it with her memories, one by one.",
                    "She wrote her village, the frozen river, the first book in the snow. She wrote Taddeus. She wrote until she could not remember why she was writing - and then she wrote that down too.",
                    "In the morning there was no binder in the tower. There was only a tower that tests whoever climbs it, and somewhere in its stones a voice, asking each new mage the question she once asked herself.",
                    "You found every page. Then you know me better than I know myself now. The question is simple: will you let your grimoire choose you - and keep choosing it back? Take this. You are a binder too, now."
            }};

    public static final int CH = 3, PER = 6, BOSS_PAGES = 5;

    public static int bit(int ch, int page) { return 1 << (ch * PER + page); }
}
