package de.mkoehler.robotrampage.rules;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deck composition and priorities of design.md 2.5 and the deal, discard
 * and reshuffle behaviour of {@link Deck}.
 *
 * @author Mario Koehler
 */
class DeckTest {

    /**
     * Deals a whole deck and returns every card in dealing order.
     *
     * @param deck the deck to empty
     * @return all cards that were in the draw pile
     */
    private static List<Card> dealEverything(Deck deck) {
        return deck.deal(deck.drawPileSize());
    }

    /**
     * The full deck must hold 84 cards with the classic number of each type.
     */
    @Test
    void fullDeckHasTheClassicComposition() {
        List<Card> cards = dealEverything(Deck.standard(1L));

        assertEquals(84, cards.size());
        for (CardType type : CardType.values()) {
            assertEquals(type.copiesInDeck(), cards.stream().filter(card -> card.type() == type).count(),
                "copies of " + type);
        }
    }

    /**
     * Priorities must be the 84 distinct values 10, 20, ... 840, so no tie is ever possible.
     */
    @Test
    void prioritiesAreUniqueMultiplesOfTenFromTenTo840() {
        Set<Integer> priorities = new HashSet<>();
        for (Card card : dealEverything(Deck.standard(1L))) {
            priorities.add(card.priority());
        }

        assertEquals(84, priorities.size());
        for (int priority = 10; priority <= 840; priority += 10) {
            assertTrue(priorities.contains(priority), "missing priority " + priority);
        }
    }

    /**
     * Priorities must ascend by card type: U-Turn, rotations, Back Up, Move 1, Move 2, Move 3.
     */
    @Test
    void prioritiesAscendByCardType() {
        List<Card> cards = Deck.fullDeckCards();
        List<List<CardType>> groups = List.of(
            List.of(CardType.U_TURN),
            List.of(CardType.ROTATE_LEFT, CardType.ROTATE_RIGHT),
            List.of(CardType.BACK_UP),
            List.of(CardType.MOVE_1),
            List.of(CardType.MOVE_2),
            List.of(CardType.MOVE_3));
        int previousMax = 0;
        for (List<CardType> group : groups) {
            List<Integer> priorities = cards.stream()
                .filter(card -> group.contains(card.type()))
                .map(Card::priority).toList();
            int min = priorities.stream().min(Integer::compare).orElseThrow();
            assertTrue(min > previousMax, group + " starts too low");
            previousMax = priorities.stream().max(Integer::compare).orElseThrow();
        }
        assertEquals(10, cards.get(0).priority());
        assertEquals(840, cards.get(83).priority());
    }

    /**
     * Rotate Left and Rotate Right alternate through the rotation priorities.
     */
    @Test
    void rotationsAlternateBetweenLeftAndRight() {
        List<Card> rotations = Deck.fullDeckCards().stream()
            .filter(card -> card.type() == CardType.ROTATE_LEFT || card.type() == CardType.ROTATE_RIGHT)
            .toList();

        assertEquals(36, rotations.size());
        assertEquals(CardType.ROTATE_LEFT, rotations.get(0).type());
        assertEquals(CardType.ROTATE_RIGHT, rotations.get(1).type());
        assertEquals(CardType.ROTATE_LEFT, rotations.get(2).type());
    }

    /**
     * Two decks built from the same seed shuffle identically; a different seed differs.
     */
    @Test
    void shufflingIsDeterministicPerSeed() {
        assertEquals(dealEverything(Deck.standard(42L)), dealEverything(Deck.standard(42L)));
        assertNotEquals(dealEverything(Deck.standard(42L)), dealEverything(Deck.standard(43L)));
    }

    /**
     * Dealing takes cards from the top of the draw pile, first card first.
     */
    @Test
    void dealTakesCardsFromTheTopInOrder() {
        List<Card> order = List.of(new Card(CardType.MOVE_1, 1), new Card(CardType.MOVE_2, 2), new Card(CardType.U_TURN, 3));
        Deck deck = Deck.stacked(7L, order);

        List<Card> dealt = deck.deal(2);

        assertEquals(order.subList(0, 2), dealt);
        assertEquals(1, deck.drawPileSize());
    }

    /**
     * When the draw pile runs out mid-deal, the discard pile is reshuffled into a new
     * one and dealing carries on.
     */
    @Test
    void emptyDrawPileIsRefilledFromTheDiscardPile() {
        Card a = new Card(CardType.MOVE_1, 1);
        Card b = new Card(CardType.MOVE_2, 2);
        Deck deck = Deck.stacked(7L, List.of(a, b));
        deck.discardAll(List.of(new Card(CardType.U_TURN, 3), new Card(CardType.BACK_UP, 4), new Card(CardType.MOVE_3, 5)));

        List<Card> dealt = deck.deal(4);

        assertEquals(4, dealt.size());
        assertEquals(List.of(a, b), dealt.subList(0, 2));
        assertEquals(1, deck.drawPileSize());
        assertEquals(0, deck.discardPileSize());
    }

    /**
     * A deck holding fewer cards in total than requested deals what it has.
     */
    @Test
    void dealingMoreThanExistsReturnsWhatIsLeft() {
        Deck deck = Deck.stacked(7L, List.of(new Card(CardType.MOVE_1, 1)));

        assertEquals(1, deck.deal(5).size());
        assertEquals(0, deck.deal(1).size());
    }

    /**
     * Discarded cards are not dealt until the draw pile is empty.
     */
    @Test
    void discardedCardsWaitForTheNextReshuffle() {
        Deck deck = Deck.stacked(7L, List.of(new Card(CardType.MOVE_1, 1), new Card(CardType.MOVE_2, 2)));
        deck.discard(new Card(CardType.U_TURN, 3));

        assertEquals(1, deck.discardPileSize());
        assertEquals(2, deck.drawPileSize());
        deck.deal(2);
        assertEquals(1, deck.discardPileSize());
    }

    /**
     * The reshuffle after a discard is reproducible: identical decks in identical states
     * deal identical cards, which is what makes a saved game resumable.
     */
    @Test
    void reshuffleIsReproducible() {
        List<Card> playedCards = new ArrayList<>(Deck.fullDeckCards().subList(0, 10));
        Deck first = Deck.stacked(9L, List.of());
        Deck second = Deck.stacked(9L, List.of());
        first.discardAll(playedCards);
        second.discardAll(playedCards);

        assertEquals(first.deal(10), second.deal(10));
    }

    /**
     * Dealing from a completely empty deck must not use up a shuffle: otherwise the
     * shuffles of a resumed game would diverge from those of an uninterrupted one.
     */
    @Test
    void dealingFromAnExhaustedDeckDoesNotAdvanceTheShuffleSequence() {
        List<Card> played = Deck.fullDeckCards().subList(0, 12);
        Deck untouched = Deck.stacked(11L, List.of());
        Deck pestered = Deck.stacked(11L, List.of());
        for (int i = 0; i < 5; i++) {
            assertEquals(0, pestered.deal(3).size());
        }
        untouched.discardAll(played);
        pestered.discardAll(played);

        assertEquals(untouched.deal(12), pestered.deal(12));
    }

    /**
     * A copy is independent: dealing from it does not touch the original.
     */
    @Test
    void copyIsIndependentOfTheOriginal() {
        Deck original = Deck.standard(5L);
        Deck copy = original.copy();

        copy.deal(9);
        copy.discard(new Card(CardType.MOVE_1, 1));

        assertEquals(84, original.drawPileSize());
        assertEquals(0, original.discardPileSize());
        assertEquals(75, copy.drawPileSize());
    }
}
