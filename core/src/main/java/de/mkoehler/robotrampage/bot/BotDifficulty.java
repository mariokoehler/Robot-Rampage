package de.mkoehler.robotrampage.bot;

/**
 * How carefully a computer-controlled robot plays (design.md 2.14). {@link BotBrain} always tries every program its
 * hand allows through the real rules engine, whatever the difficulty; only how the best one is picked, and how
 * cautiously the bot powers down, differ. It never changes what the bot is allowed to see — the fair-play rule
 * (design.md 2.14) applies at every difficulty alike.
 *
 * @author Mario Koehler
 */
public enum BotDifficulty {
    EASY, NORMAL, HARD;

    /**
     * Returns the difficulty the lobby's cycle button moves to next: Easy to Normal to Hard and back to Easy.
     *
     * @return the next difficulty in the cycle
     */
    public BotDifficulty next() {
        BotDifficulty[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
