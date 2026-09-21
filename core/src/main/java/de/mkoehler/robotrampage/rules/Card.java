package de.mkoehler.robotrampage.rules;

/**
 * One programming card: a {@link CardType} plus the unique priority that decides
 * in which order robots execute their cards within a register (higher priority
 * moves first, design.md 2.4).
 *
 * @param type     what the card makes a robot do
 * @param priority the card's priority; unique across the whole deck
 * @author Mario Koehler
 */
public record Card(CardType type, int priority) {
}
