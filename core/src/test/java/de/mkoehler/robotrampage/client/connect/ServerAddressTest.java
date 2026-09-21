package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.NetworkConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies how {@link ServerAddress} reads what a player types into the address field.
 *
 * @author Mario Koehler
 */
class ServerAddressTest {

    /**
     * A host without a port gets the default port.
     */
    @Test
    void hostAloneUsesTheDefaultPort() {
        ServerAddress address = ServerAddress.parse("robots.example.org");

        assertEquals("robots.example.org", address.host());
        assertEquals(NetworkConstants.TCP_PORT, address.port());
    }

    /**
     * A host and a port are split at the colon, and blanks around the text are ignored.
     */
    @Test
    void hostAndPortAreSplit() {
        ServerAddress address = ServerAddress.parse("  192.168.0.5:4000 ");

        assertEquals("192.168.0.5", address.host());
        assertEquals(4000, address.port());
    }

    /**
     * An IPv6 address in brackets works with and without a port.
     */
    @Test
    void bracketedIpv6IsAccepted() {
        assertEquals(new ServerAddress("::1", 4000), ServerAddress.parse("[::1]:4000"));
        assertEquals(new ServerAddress("fe80::1", NetworkConstants.TCP_PORT), ServerAddress.parse("[fe80::1]"));
    }

    /**
     * An IPv6 address without brackets is ambiguous, so it is refused with a hint.
     */
    @Test
    void bareIpv6IsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("::1"));

        assertEquals("Put an IPv6 address in brackets, like [::1]:" + NetworkConstants.TCP_PORT + ".", e.getMessage());
    }

    /**
     * Text that is no address is refused: nothing, blanks inside, broken brackets and bad ports.
     */
    @Test
    void malformedTextIsRefused() {
        for (String text : new String[] {null, "", "   ", "my server", "host:", "host:abc", "host:0", "host:65536",
            "host:123456", "host:-1", ":4000", "[::1", "[::1]x", "[::1]:", "[]:4000"}) {
            assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse(text), String.valueOf(text));
        }
    }

    /**
     * The highest and lowest ports are valid.
     */
    @Test
    void portLimitsAreInclusive() {
        assertEquals(1, ServerAddress.parse("h:1").port());
        assertEquals(65535, ServerAddress.parse("h:65535").port());
    }

    /**
     * The string form always shows the port and brackets an IPv6 host, so it can be parsed back.
     */
    @Test
    void stringFormCanBeParsedBack() {
        assertEquals("host:45725", ServerAddress.parse("host").toString());
        assertEquals("[::1]:4000", ServerAddress.parse("[::1]:4000").toString());
        assertEquals(new ServerAddress("::1", 4000), ServerAddress.parse(new ServerAddress("::1", 4000).toString()));
    }
}
