package de.mkoehler.robotrampage.client.board;

import java.util.List;

/**
 * The entries of the board key next to the board (design.md 4.3): what each kind of square or edge looks like, and what
 * it does to a robot, worded for a player hovering over the key rather than for the rules engine. Every explanation says
 * what the element does and when in the register it acts (design.md 2.4), and describes this game's rules (design.md
 * 2.9), which differ from the printed board game in places: flags, for instance, do not repair.
 *
 * @author Mario Koehler
 */
public enum BoardKey {

    /** A normal conveyor belt. */
    BELT("Belt", List.of("tiles/belt.png"),
        "After every robot has played its card, moves a robot standing on it one square in the direction of its arrow. "
            + "A robot carried round a curve turns with it; one that just walks onto a curve does not. Belts never push: "
            + "a wall, a robot standing still or two robots heading for the same square stop it. A belt can carry a "
            + "robot into a pit or off the board."),

    /** An express conveyor belt. */
    EXPRESS_BELT("Express belt", List.of("tiles/belt-express.png"),
        "Works like a belt, but moves a robot twice each register: once before the normal belts move and once with "
            + "them, so two squares instead of one."),

    /** A gear, clockwise or counter-clockwise. */
    GEAR("Gear", List.of("tiles/gear-clockwise.png"),
        "Every register, after the belts and pushers, turns a robot standing on it 90° the way its arrows point: "
            + "clockwise or counter-clockwise. It never moves the robot to another square."),

    /** A pit. */
    PIT("Pit", List.of("tiles/pit.png"),
        "Destroys a robot the moment it moves, is pushed or is carried onto it. Leaving the board does the same. A "
            + "destroyed robot loses a life, sits out the rest of the turn and comes back next turn on its archive "
            + "marker, undamaged, facing whichever way its player picks."),

    /** A repair site. */
    REPAIR_SITE("Repair site", List.of("tiles/repair-site.png"),
        "At the end of the turn, after register 5, a robot standing here repairs 1 damage and its archive marker moves "
            + "here, so this is where it comes back if it is destroyed later."),

    /** A flag, one of the checkpoints. */
    FLAG("Flag", List.of("tiles/floor.png", "board/flag.png"),
        "Touch the flags in the order of their numbers; the last one wins the game. A flag only counts if the robot "
            + "ends a register on it (driving across it does not) and it is the next one the robot needs. Touching it "
            + "moves the archive marker here. Flags do not repair damage."),

    /** A wall on the edge of a square. */
    WALL("Wall", List.of("tiles/floor.png", "board/wall.png"),
        "Nothing crosses it: a robot walking into it stops and loses the rest of that move, a push through it fails "
            + "for every robot in the line, belts and pushers cannot move a robot through it, and it stops laser "
            + "beams."),

    /** A board laser, mounted on a wall. */
    LASER("Laser", List.of("tiles/floor.png", "board/laser-emitter.png"),
        "Fires every register, after the gears, along its red line until it hits a wall or the first robot. Each beam "
            + "does 1 damage. Robots fire a laser straight ahead at the same time. At 5 damage and more, registers "
            + "start to lock with the last card played in them; at 10 damage the robot is destroyed."),

    /** A pusher, mounted on a wall. */
    PUSHER("Pusher", List.of("board/pusher.png"),
        "Only works in the registers printed on its bar. In those registers, after the belts, it shoves a robot "
            + "standing on its square one square away from its wall, even into a pit or off the board."),

    /** A crusher. */
    CRUSHER("Crusher", List.of("board/crusher.png"),
        "Only works in the registers printed on it. At the end of those registers, after the lasers, it destroys any "
            + "robot standing on it. It can sit on a belt, which moves robots on and off it first.");

    private final String title;
    private final List<String> pictures;
    private final String explanation;

    /**
     * Creates an entry.
     *
     * @param title       the name shown in the key
     * @param pictures    the pictures its icon is drawn from, bottom first, as paths below {@code assets}
     * @param explanation what it does to a robot and when
     */
    BoardKey(String title, List<String> pictures, String explanation) {
        this.title = title;
        this.pictures = pictures;
        this.explanation = explanation;
    }

    /**
     * Returns the name shown in the key.
     *
     * @return the name
     */
    public String title() {
        return title;
    }

    /**
     * Returns the pictures the entry's icon is drawn from, bottom first.
     *
     * @return paths below {@code assets}, such as {@code "tiles/belt.png"}
     */
    public List<String> pictures() {
        return pictures;
    }

    /**
     * Returns what the element does to a robot and when in the register it acts.
     *
     * @return the explanation, one paragraph
     */
    public String explanation() {
        return explanation;
    }
}
