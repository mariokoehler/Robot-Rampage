package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.ServerLink;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;

import java.util.List;

/**
 * An accepted connection, handed on from the connect flow to the screen that follows it.
 *
 * @param link          the open connection; its owner from now on is the screen that receives this object
 * @param welcome       the server's acceptance, with the seat and the session token
 * @param earlyMessages the messages that arrived together with the acceptance, in order; they belong to the next screen
 *                      and must be handled before anything is polled from the link
 * @param closedAlready whether the connection ended right after the acceptance
 * @author Mario Koehler
 */
public record ConnectedServer(ServerLink link, HandshakeResponse welcome, List<Object> earlyMessages,
                              boolean closedAlready) {

    /**
     * Copies the list of early messages, so the record cannot be changed afterwards.
     */
    public ConnectedServer {
        earlyMessages = List.copyOf(earlyMessages);
    }
}
