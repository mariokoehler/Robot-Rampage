package de.mkoehler.robotrampage.net;

/**
 * Fixed values shared by the dedicated server and every client.
 * <p>
 * Robot Rampage is turn-based, so the whole protocol runs over KryoNet's
 * reliable TCP channel; there is no UDP port and no simulation tick rate (see
 * design.md 3.2).
 *
 * @author Mario Koehler
 */
public final class NetworkConstants {

    /**
     * The default TCP port the dedicated server listens on. Deliberately
     * different from the StarWars project's port so both servers can run side
     * by side on one machine.
     */
    public static final int TCP_PORT = 45725;

    /**
     * Size, in bytes, of the buffer used to write queued objects to the network. Several messages can
     * be queued at once, so it is a multiple of {@link #OBJECT_BUFFER_SIZE}.
     */
    public static final int WRITE_BUFFER_SIZE = 262144;

    /**
     * Size, in bytes, of the buffer used to serialize or deserialize a single object. Must be larger
     * than the biggest single message, which is a {@code TurnResolved} with the events of a whole
     * turn; {@code WireProtocolTest} serialises the biggest turns a randomised game produces and
     * asserts they fit with plenty of room to spare.
     */
    public static final int OBJECT_BUFFER_SIZE = 65536;

    /**
     * Maximum time, in milliseconds, a client waits for a connection attempt to
     * complete before giving up.
     */
    public static final int CONNECTION_TIMEOUT_MILLIS = 5000;

    /**
     * Not instantiable; this class only holds constants.
     */
    private NetworkConstants() {
    }
}
