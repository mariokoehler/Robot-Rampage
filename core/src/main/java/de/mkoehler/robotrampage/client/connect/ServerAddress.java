package de.mkoehler.robotrampage.client.connect;

import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * Where a server can be reached: a host name or IP address and a TCP port, as a player types them on the connect screen.
 *
 * @param host the host name or IP address, without brackets or port
 * @param port the TCP port, 1 to 65535
 * @author Mario Koehler
 */
public record ServerAddress(String host, int port) {

    private static final int MAX_PORT = 65535;

    /**
     * Checks the parts of an address.
     *
     * @throws IllegalArgumentException if the host is blank or the port is out of range
     */
    public ServerAddress {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Enter the address of the server.");
        }
        if (port < 1 || port > MAX_PORT) {
            throw new IllegalArgumentException("The port must be a number from 1 to " + MAX_PORT + ".");
        }
    }

    /**
     * Reads what a player typed. Accepted forms are {@code host}, {@code host:port}, {@code 192.168.0.5:4000} and, for IPv6,
     * {@code [::1]} and {@code [::1]:4000}. Without a port the default {@link NetworkConstants#TCP_PORT} is used. Leading and
     * trailing blanks are ignored.
     *
     * @param text the text of the address field
     * @return the address
     * @throws IllegalArgumentException with a message that can be shown to the player, if the text is not an address
     */
    public static ServerAddress parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter the address of the server.");
        }
        if (trimmed.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("An address has no spaces in it.");
        }
        if (trimmed.startsWith("[")) {
            return parseBracketed(trimmed);
        }
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return new ServerAddress(trimmed, NetworkConstants.TCP_PORT);
        }
        if (colon != trimmed.lastIndexOf(':')) {
            throw new IllegalArgumentException(
                "Put an IPv6 address in brackets, like [::1]:" + NetworkConstants.TCP_PORT + ".");
        }
        return new ServerAddress(trimmed.substring(0, colon), parsePort(trimmed.substring(colon + 1)));
    }

    /**
     * Reads an address that starts with a bracketed IPv6 host.
     *
     * @param text the trimmed text, starting with {@code [}
     * @return the address
     * @throws IllegalArgumentException if the brackets or the port are wrong
     */
    private static ServerAddress parseBracketed(String text) {
        int close = text.indexOf(']');
        if (close < 0) {
            throw new IllegalArgumentException("The bracket after the IPv6 address is missing.");
        }
        String host = text.substring(1, close);
        String rest = text.substring(close + 1);
        if (rest.isEmpty()) {
            return new ServerAddress(host, NetworkConstants.TCP_PORT);
        }
        if (!rest.startsWith(":")) {
            throw new IllegalArgumentException(
                "After the bracket, only a port like :" + NetworkConstants.TCP_PORT + " can follow.");
        }
        return new ServerAddress(host, parsePort(rest.substring(1)));
    }

    /**
     * Reads a port number.
     *
     * @param text the digits after the colon
     * @return the port
     * @throws IllegalArgumentException if the text is not a number
     */
    private static int parsePort(String text) {
        if (text.isEmpty() || text.length() > 5 || !text.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("The port must be a number from 1 to " + MAX_PORT + ".");
        }
        return Integer.parseInt(text);
    }

    /**
     * Formats the address the way it is typed, with the port always shown and an IPv6 host in brackets.
     *
     * @return {@code host:port}
     */
    @Override
    public String toString() {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }
}
