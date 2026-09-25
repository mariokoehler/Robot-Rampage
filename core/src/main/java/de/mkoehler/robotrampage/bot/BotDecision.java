package de.mkoehler.robotrampage.bot;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.rules.Card;

import java.util.List;

/**
 * What a bot does in a turn: the cards for its free registers, the facing it re-enters with, and whether it powers down.
 *
 * @param program   the cards for the unlocked registers, register 1 first; exactly as many as the robot has unlocked
 *                  registers, all from its hand
 * @param facing    the facing to re-enter with, or {@code null} to keep the facing it has (always {@code null} for a robot
 *                  that did not re-enter this turn)
 * @param powerDown whether the bot announces a power-down after this turn
 * @author Mario Koehler
 */
public record BotDecision(List<Card> program, Direction facing, boolean powerDown) {

    /**
     * Makes the program list unmodifiable.
     */
    public BotDecision {
        program = List.copyOf(program);
    }
}
