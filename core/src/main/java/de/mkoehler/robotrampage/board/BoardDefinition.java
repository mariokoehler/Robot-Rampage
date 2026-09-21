package de.mkoehler.robotrampage.board;

import java.util.List;

/**
 * The JSON form of a board (design.md 3.6): plain, immutable data that {@link BoardLoader}
 * reads and writes with Jackson. It is what boards look like on disk and on the wire; the
 * rules engine only ever sees the {@link Board} built from it.
 * <p>
 * The format is <em>sparse</em> and <em>edge-based</em>: squares default to plain floor and
 * only interesting ones are listed, and walls, lasers and pushers sit on edges. Flags are
 * numbered by their position in {@link #flags()}, so their numbers are always contiguous.
 * Absent lists read as empty. A definition is not checked for sense by itself; that is the
 * job of {@link BoardValidator}.
 *
 * @param formatVersion the version of this format, currently {@value #FORMAT_VERSION}
 * @param id            a short unique identifier, e.g. {@code proving-grounds}
 * @param name          the human-readable name
 * @param author        who made the board, or {@code null}
 * @param generator     the id of the generator that produced the board, or {@code null} for a
 *                      hand-made one
 * @param seed          the seed a generator used, so the board can be reproduced, or
 *                      {@code null}
 * @param width         the number of columns
 * @param height        the number of rows
 * @param squares       the squares that have a belt and/or a feature
 * @param edges         walls, lasers and pushers, each edge listed once
 * @param flags         the flag squares in order: the first is flag 1
 * @param startSquares  the start squares in the order seats are assigned
 * @author Mario Koehler
 */
public record BoardDefinition(int formatVersion, String id, String name, String author, String generator, Long seed,
                              int width, int height, List<Square> squares, List<Edge> edges, List<Flag> flags,
                              List<Start> startSquares) {

    /**
     * The format version this code reads and writes.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * Creates a definition; absent (null) lists become empty lists and all lists are copied.
     *
     * @param formatVersion the format version
     * @param id            the identifier
     * @param name          the name
     * @param author        the author, or {@code null}
     * @param generator     the generator id, or {@code null}
     * @param seed          the generator seed, or {@code null}
     * @param width         the number of columns
     * @param height        the number of rows
     * @param squares       the squares with a belt or feature, or {@code null} for none
     * @param edges         the edge elements, or {@code null} for none
     * @param flags         the flags in order, or {@code null} for none
     * @param startSquares  the start squares, or {@code null} for none
     */
    public BoardDefinition {
        squares = squares == null ? List.of() : List.copyOf(squares);
        edges = edges == null ? List.of() : List.copyOf(edges);
        flags = flags == null ? List.of() : List.copyOf(flags);
        startSquares = startSquares == null ? List.of() : List.copyOf(startSquares);
    }

    /**
     * A square with a belt and/or a feature.
     *
     * @param x         the column
     * @param y         the row
     * @param belt      the belt on the square, or {@code null}
     * @param feature   the feature, or {@code null} for none
     * @param registers for a {@link SquareFeature#CRUSHER}: the registers (1 to 5) it is active
     *                  in; otherwise absent
     */
    public record Square(int x, int y, Belt belt, SquareFeature feature, List<Integer> registers) {

        /**
         * Creates a square entry; an absent register list becomes empty.
         *
         * @param x         the column
         * @param y         the row
         * @param belt      the belt, or {@code null}
         * @param feature   the feature, or {@code null}
         * @param registers the crusher registers, or {@code null}
         */
        public Square {
            registers = registers == null ? List.of() : List.copyOf(registers);
        }
    }

    /**
     * A conveyor belt.
     *
     * @param dir     the direction it moves robots in
     * @param express whether it is an express belt
     */
    public record Belt(Direction dir, boolean express) {
    }

    /**
     * One edge of a square. It carries a wall, a laser or a pusher; lasers and pushers are
     * mounted on the given side and imply a wall there.
     *
     * @param x      the column of the square the edge belongs to
     * @param y      the row of that square
     * @param side   which side of that square
     * @param wall   {@code true} for a plain wall
     * @param laser  a laser mounted on this side, or {@code null}
     * @param pusher a pusher mounted on this side, or {@code null}
     */
    public record Edge(int x, int y, Direction side, boolean wall, Laser laser, Pusher pusher) {
    }

    /**
     * A wall-mounted laser.
     *
     * @param beams the number of beams, 1 to 3
     */
    public record Laser(int beams) {
    }

    /**
     * A wall-mounted pusher.
     *
     * @param registers the registers (1 to 5) it is active in
     */
    public record Pusher(List<Integer> registers) {

        /**
         * Creates a pusher entry; an absent register list becomes empty.
         *
         * @param registers the registers, or {@code null}
         */
        public Pusher {
            registers = registers == null ? List.of() : List.copyOf(registers);
        }
    }

    /**
     * A flag square.
     *
     * @param x the column
     * @param y the row
     */
    public record Flag(int x, int y) {
    }

    /**
     * A start square.
     *
     * @param x      the column
     * @param y      the row
     * @param facing the direction a robot starting here faces
     */
    public record Start(int x, int y, Direction facing) {
    }
}
