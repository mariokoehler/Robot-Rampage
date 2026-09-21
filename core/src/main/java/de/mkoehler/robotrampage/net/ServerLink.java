package de.mkoehler.robotrampage.net;

import java.io.IOException;

/**
 * The client's end of the connection to a server, as far as the code that drives a connection attempt needs it. The one
 * real implementation is {@link NetworkClient}; the interface exists so that the connect flow can be tested without
 * sockets, including the slow and racing cases that are hard to provoke with a real network.
 * <p>
 * The threading contract of {@link NetworkClient} applies to every implementation: {@link #connect(String, int)} and
 * {@link #disconnect()} may block, {@link #poll(NetworkClient.Handler)} and {@link #send(Object)} are for the loop
 * thread.
 *
 * @author Mario Koehler
 */
public interface ServerLink {

    /**
     * Connects to a server, blocking until the connection is up or has failed.
     *
     * @param host    the server's host name or address
     * @param tcpPort the server's TCP port
     * @throws IOException if the connection could not be established
     */
    void connect(String host, int tcpPort) throws IOException;

    /**
     * Hands every event that arrived since the last call to a handler, in arrival order.
     *
     * @param handler where the events go
     */
    void poll(NetworkClient.Handler handler);

    /**
     * Sends a message to the server.
     *
     * @param message the message
     */
    void send(Object message);

    /**
     * Closes the connection. Safe to call repeatedly and on a link that never connected.
     */
    void disconnect();
}
