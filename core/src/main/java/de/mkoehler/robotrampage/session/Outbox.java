package de.mkoehler.robotrampage.session;

/**
 * Where a {@link GameSession} puts the messages it wants delivered. The session knows players only by
 * <em>seat</em>; mapping seats to network connections, and dropping messages for seats that are not
 * connected, is the job of whatever implements this interface.
 *
 * @author Mario Koehler
 */
public interface Outbox {

    /**
     * Delivers a message to one player.
     *
     * @param seat    the receiving player's seat
     * @param message the message, one of the classes in {@code net.messages}
     */
    void send(int seat, Object message);

    /**
     * Delivers a message to every connected player.
     *
     * @param message the message, one of the classes in {@code net.messages}
     */
    void broadcast(Object message);
}
