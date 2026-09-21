package de.mkoehler.robotrampage.client.lobby;

import java.util.Locale;

/**
 * How the eight robots look and what they are called. Every seat has its own robot: the seat number is also the robot's
 * id, so the name and the picture follow from the seat alone and never travel over the network.
 *
 * @author Mario Koehler
 */
public final class RobotLook {

    private static final String[] NAMES = {"Bolt", "Twin", "Cog", "Beacon", "Ears", "Spring", "Fin", "Stack"};

    /**
     * The number of robots, and so of seats.
     */
    public static final int COUNT = NAMES.length;

    /**
     * Not instantiable; this class only holds static lookups.
     */
    private RobotLook() {
    }

    /**
     * Returns the name of the robot of a seat.
     *
     * @param seat the seat, 0 to 7
     * @return the name, such as {@code Bolt}
     * @throws IllegalArgumentException if the seat is out of range
     */
    public static String name(int seat) {
        check(seat);
        return NAMES[seat];
    }

    /**
     * Returns the path of the picture of the robot of a seat, relative to the asset folder.
     *
     * @param seat the seat, 0 to 7
     * @return the path, such as {@code robots/robot-1-bolt.png}
     * @throws IllegalArgumentException if the seat is out of range
     */
    public static String picture(int seat) {
        check(seat);
        return "robots/robot-" + (seat + 1) + "-" + NAMES[seat].toLowerCase(Locale.ROOT) + ".png";
    }

    /**
     * Checks that a seat has a robot.
     *
     * @param seat the seat
     * @throws IllegalArgumentException if the seat is out of range
     */
    private static void check(int seat) {
        if (seat < 0 || seat >= COUNT) {
            throw new IllegalArgumentException("Seat out of range: " + seat);
        }
    }
}
