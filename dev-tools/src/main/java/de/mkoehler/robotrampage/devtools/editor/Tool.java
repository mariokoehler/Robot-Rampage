package de.mkoehler.robotrampage.devtools.editor;

/**
 * What a click on the board does in the editor. Square tools act on the square under the pointer; edge tools act on the
 * side of that square nearest to the pointer.
 *
 * @author Mario Koehler
 */
public enum Tool {

    /** Turns squares back into plain floor. */
    ERASE("Floor", false),
    /** Paints pits. */
    PIT("Pit", false),
    /** Paints repair sites. */
    REPAIR("Repair site", false),
    /** Paints clockwise gears. */
    GEAR_CLOCKWISE("Gear CW", false),
    /** Paints counter-clockwise gears. */
    GEAR_COUNTERCLOCKWISE("Gear CCW", false),
    /** Places crushers, active in the chosen registers. */
    CRUSHER("Crusher", false),
    /** Lays belts; a drag lays them in the direction of the drag. */
    BELT("Belt", false),
    /** Lays express belts; a drag lays them in the direction of the drag. */
    EXPRESS_BELT("Express belt", false),
    /** Puts plain walls on a side of a square. */
    WALL("Wall", true),
    /** Mounts lasers, with the chosen number of beams, on a side of a square; they fire away from it. */
    LASER("Laser", true),
    /** Mounts pushers, active in the chosen registers, on a side of a square; they push away from it. */
    PUSHER("Pusher", true),
    /** Adds the next flag, or moves a flag by dragging it. */
    FLAG("Flag", false),
    /** Adds a start square facing the chosen direction, moves one by dragging it, or turns it by clicking it. */
    START("Start", false);

    private final String label;
    private final boolean onEdge;

    /**
     * Creates a tool.
     *
     * @param label  the name shown on its button
     * @param onEdge {@code true} if it acts on a side of a square
     */
    Tool(String label, boolean onEdge) {
        this.label = label;
        this.onEdge = onEdge;
    }

    /**
     * Returns the name shown on the tool's button.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns whether the tool acts on a side of a square rather than on the square.
     *
     * @return {@code true} for walls, lasers and pushers
     */
    public boolean onEdge() {
        return onEdge;
    }
}
