package de.mkoehler.robotrampage.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The shared programming deck: a draw pile and a discard pile (design.md 2.5, 2.7).
 * <p>
 * All shuffling is derived from a {@code seed} and a counter of how many shuffles
 * happened so far, never from a live {@link Random}, so a deck's future is fully
 * determined by three plain values (seed, shuffle count, pile contents) and can be
 * persisted and resumed exactly.
 * <p>
 * Cards a player holds in locked registers are simply not in either pile, so they
 * can neither be dealt nor reshuffled; callers must not discard them.
 *
 * @author Mario Koehler
 */
public final class Deck {

    /**
     * Mixed into the seed per shuffle so consecutive shuffles use unrelated
     * random sequences.
     */
    private static final long SHUFFLE_STRIDE = 0x9E3779B97F4A7C15L;

    private final long seed;
    private int shuffleCount;
    private final List<Card> drawPile;
    private final List<Card> discardPile;

    /**
     * Creates a deck from its raw state.
     *
     * @param seed         the seed all shuffles derive from
     * @param shuffleCount how many shuffles have already been performed
     * @param drawPile     the draw pile, with the top card <em>last</em>
     * @param discardPile  the discard pile
     */
    private Deck(long seed, int shuffleCount, List<Card> drawPile, List<Card> discardPile) {
        this.seed = seed;
        this.shuffleCount = shuffleCount;
        this.drawPile = drawPile;
        this.discardPile = discardPile;
    }

    /**
     * Creates the full 84-card deck (design.md 2.5), shuffled.
     * <p>
     * Priorities run from 10 to 840 in steps of 10, ascending by card type:
     * U-Turn, then Rotate Left/Right alternating, Back Up, Move 1, Move 2, Move 3.
     *
     * @param seed the seed all of this deck's shuffles derive from
     * @return a freshly shuffled full deck with an empty discard pile
     */
    public static Deck standard(long seed) {
        Deck deck = new Deck(seed, 0, new ArrayList<>(fullDeckCards()), new ArrayList<>());
        deck.shuffleDrawPile();
        return deck;
    }

    /**
     * Creates a deck with a fixed draw order, for tests that need to know exactly
     * which cards come out.
     *
     * @param seed         the seed used for any later reshuffle of the discard pile
     * @param topFirstCards the draw pile in dealing order: element {@code 0} is
     *                      dealt first
     * @return a deck with exactly those cards in the draw pile and nothing discarded
     */
    public static Deck stacked(long seed, List<Card> topFirstCards) {
        List<Card> reversed = new ArrayList<>(topFirstCards);
        Collections.reverse(reversed);
        return new Deck(seed, 0, reversed, new ArrayList<>());
    }

    /**
     * Builds the 84 cards of the full deck in ascending priority order.
     *
     * @return the unshuffled full deck
     */
    static List<Card> fullDeckCards() {
        List<Card> cards = new ArrayList<>();
        int priority = 10;
        for (int i = 0; i < CardType.U_TURN.copiesInDeck(); i++, priority += 10) {
            cards.add(new Card(CardType.U_TURN, priority));
        }
        int rotations = CardType.ROTATE_LEFT.copiesInDeck() + CardType.ROTATE_RIGHT.copiesInDeck();
        for (int i = 0; i < rotations; i++, priority += 10) {
            cards.add(new Card(i % 2 == 0 ? CardType.ROTATE_LEFT : CardType.ROTATE_RIGHT, priority));
        }
        for (CardType type : new CardType[] {CardType.BACK_UP, CardType.MOVE_1, CardType.MOVE_2, CardType.MOVE_3}) {
            for (int i = 0; i < type.copiesInDeck(); i++, priority += 10) {
                cards.add(new Card(type, priority));
            }
        }
        return cards;
    }

    /**
     * Deals cards from the top of the draw pile. If the draw pile runs out, the
     * discard pile is shuffled into a new draw pile and dealing continues.
     *
     * @param count how many cards to deal
     * @return the dealt cards, in the order they were drawn; shorter than
     *         {@code count} only if the deck holds fewer cards in total
     */
    public List<Card> deal(int count) {
        List<Card> dealt = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (drawPile.isEmpty() && !discardPile.isEmpty()) {
                drawPile.addAll(discardPile);
                discardPile.clear();
                shuffleDrawPile();
            }
            if (drawPile.isEmpty()) {
                break;
            }
            dealt.add(drawPile.remove(drawPile.size() - 1));
        }
        return dealt;
    }

    /**
     * Puts a card on the discard pile.
     *
     * @param card the card to discard
     */
    public void discard(Card card) {
        discardPile.add(card);
    }

    /**
     * Puts several cards on the discard pile, in iteration order.
     *
     * @param cards the cards to discard
     */
    public void discardAll(Collection<Card> cards) {
        discardPile.addAll(cards);
    }

    /**
     * Returns how many cards are left in the draw pile.
     *
     * @return the draw pile size
     */
    public int drawPileSize() {
        return drawPile.size();
    }

    /**
     * Returns how many cards are on the discard pile.
     *
     * @return the discard pile size
     */
    public int discardPileSize() {
        return discardPile.size();
    }

    /**
     * Returns a deep copy that can be modified without affecting this deck.
     *
     * @return an independent copy with identical piles, seed and shuffle count
     */
    public Deck copy() {
        return new Deck(seed, shuffleCount, new ArrayList<>(drawPile), new ArrayList<>(discardPile));
    }

    /**
     * Shuffles the draw pile with a random sequence derived from the seed and the
     * number of shuffles performed so far, then counts this shuffle.
     */
    private void shuffleDrawPile() {
        Random random = new Random(seed + SHUFFLE_STRIDE * shuffleCount);
        shuffleCount++;
        Collections.shuffle(drawPile, random);
    }
}
