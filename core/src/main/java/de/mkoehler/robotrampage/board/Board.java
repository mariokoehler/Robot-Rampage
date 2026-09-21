package de.mkoehler.robotrampage.board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The immutable, flat rectangular grid the rules engine plays on (design.md 2.1,
 * 3.6). Whatever produced it &mdash; a hand-authored JSON file, a composition of
 * several sections or a generator &mdash; the engine only ever sees this class.
 * <p>
 * A board consists of squares that may carry a {@link Belt} and/or a
 * {@link SquareFeature}, walls on the edges between squares, wall-mounted
 * {@link Laser}s and {@link Pusher}s, numbered flags and {@link StartSquare}s.
 * Instances are created through {@link Builder}, which keeps walls symmetric: a
 * wall between two squares is visible from both of them.
 * <p>
 * The board does not validate that it is <em>playable</em> (flags reachable,
 * start squares safe, ...); that is the job of a separate validator.
 *
 * @author Mario Koehler
 */
public final class Board {

    private final int width;
    private final int height;
    private final Map<Position, Set<Direction>> walls;
    private final Map<Position, Belt> belts;
    private final Map<Position, SquareFeature> features;
    private final Map<Position, Set<Integer>> crusherRegisters;
    private final List<Laser> lasers;
    private final List<Pusher> pushers;
    private final List<Position> flags;
    private final List<StartSquare> startSquares;

    /**
     * Creates a board from a builder's state, copying everything so later builder
     * changes cannot leak in.
     *
     * @param builder the builder to take the contents from
     */
    private Board(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        Map<Position, Set<Direction>> wallCopy = new HashMap<>();
        builder.walls.forEach((position, sides) -> wallCopy.put(position, Collections.unmodifiableSet(EnumSet.copyOf(sides))));
        this.walls = Collections.unmodifiableMap(wallCopy);
        this.belts = Map.copyOf(builder.belts);
        this.features = Map.copyOf(builder.features);
        this.crusherRegisters = Map.copyOf(builder.crusherRegisters);
        this.lasers = List.copyOf(builder.lasers);
        this.pushers = List.copyOf(builder.pushers);
        this.flags = List.copyOf(builder.flags);
        this.startSquares = List.copyOf(builder.startSquares);
    }

    /**
     * Returns the number of columns.
     *
     * @return the board width in squares
     */
    public int width() {
        return width;
    }

    /**
     * Returns the number of rows.
     *
     * @return the board height in squares
     */
    public int height() {
        return height;
    }

    /**
     * Returns whether a position lies on this board.
     *
     * @param position the position to test
     * @return {@code true} if both coordinates are within the grid
     */
    public boolean inBounds(Position position) {
        return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
    }

    /**
     * Returns whether there is a wall on the given side of the given square.
     * Walls are symmetric, so the answer is the same when asked from the
     * neighbouring square about the opposite side.
     *
     * @param position the square
     * @param side     the side of that square
     * @return {@code true} if a wall blocks movement and lasers across that edge
     */
    public boolean hasWall(Position position, Direction side) {
        return walls.getOrDefault(position, Set.of()).contains(side);
    }

    /**
     * Returns every wall side of every square, so a board can be exported. A wall between two
     * squares appears once for each of them.
     *
     * @return an unmodifiable map from square to the sides of it that have a wall
     */
    public Map<Position, Set<Direction>> walls() {
        return walls;
    }

    /**
     * Returns every belt of the board, so a board can be exported.
     *
     * @return an unmodifiable map from square to its belt
     */
    public Map<Position, Belt> belts() {
        return belts;
    }

    /**
     * Returns every square that has a feature, so a board can be exported.
     *
     * @return an unmodifiable map from square to its feature; plain floor is not included
     */
    public Map<Position, SquareFeature> features() {
        return features;
    }

    /**
     * Returns the registers a crusher on a square is active in.
     *
     * @param position the square
     * @return the registers, or an empty set if the square has no crusher
     */
    public Set<Integer> crusherRegisters(Position position) {
        return crusherRegisters.getOrDefault(position, Set.of());
    }

    /**
     * Returns the belt on a square, if any.
     *
     * @param position the square
     * @return the belt, or empty if the square has none
     */
    public Optional<Belt> beltAt(Position position) {
        return Optional.ofNullable(belts.get(position));
    }

    /**
     * Returns the feature of a square.
     *
     * @param position the square
     * @return the feature, or {@link SquareFeature#NONE} for plain floor
     */
    public SquareFeature featureAt(Position position) {
        return features.getOrDefault(position, SquareFeature.NONE);
    }

    /**
     * Returns whether a square is a pit.
     *
     * @param position the square
     * @return {@code true} if it has the {@link SquareFeature#PIT} feature
     */
    public boolean isPit(Position position) {
        return featureAt(position) == SquareFeature.PIT;
    }

    /**
     * Returns whether a crusher on the given square is active in a register.
     *
     * @param position the square
     * @param register the register number, 1 to 5
     * @return {@code true} if the square has a crusher that is active in that
     *         register
     */
    public boolean isCrusherActive(Position position, int register) {
        return crusherRegisters.getOrDefault(position, Set.of()).contains(register);
    }

    /**
     * Returns all squares that have a crusher, in no particular order.
     *
     * @return the crusher squares
     */
    public List<Position> crusherPositions() {
        return new ArrayList<>(crusherRegisters.keySet());
    }

    /**
     * Returns all board lasers.
     *
     * @return the lasers, in the order they were added
     */
    public List<Laser> lasers() {
        return lasers;
    }

    /**
     * Returns all pushers.
     *
     * @return the pushers, in the order they were added
     */
    public List<Pusher> pushers() {
        return pushers;
    }

    /**
     * Returns the flag squares in flag order: element {@code 0} is flag 1.
     *
     * @return the flag positions
     */
    public List<Position> flags() {
        return flags;
    }

    /**
     * Returns the start squares in the order seats are assigned.
     *
     * @return the start squares
     */
    public List<StartSquare> startSquares() {
        return startSquares;
    }

    /**
     * Collects a {@link Board}'s contents. Every element is checked to lie on the
     * board, and walls added here are mirrored onto the neighbouring square so a
     * wall is never one-sided.
     */
    public static final class Builder {

        private final int width;
        private final int height;
        private final Map<Position, Set<Direction>> walls = new HashMap<>();
        private final Map<Position, Belt> belts = new HashMap<>();
        private final Map<Position, SquareFeature> features = new HashMap<>();
        private final Map<Position, Set<Integer>> crusherRegisters = new HashMap<>();
        private final List<Laser> lasers = new ArrayList<>();
        private final List<Pusher> pushers = new ArrayList<>();
        private final List<Position> flags = new ArrayList<>();
        private final List<StartSquare> startSquares = new ArrayList<>();

        /**
         * Starts an empty board of plain floor.
         *
         * @param width  the number of columns, at least 1
         * @param height the number of rows, at least 1
         * @throws IllegalArgumentException if either dimension is below 1
         */
        public Builder(int width, int height) {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("Board must be at least 1x1, was " + width + "x" + height);
            }
            this.width = width;
            this.height = height;
        }

        /**
         * Adds a wall on one side of a square, and the matching wall on the
         * neighbouring square if that lies on the board.
         *
         * @param position the square
         * @param side     the side of that square to put the wall on
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board
         */
        public Builder wall(Position position, Direction side) {
            requireInBounds(position);
            addWallSide(position, side);
            Position neighbour = position.step(side);
            if (inBounds(neighbour)) {
                addWallSide(neighbour, side.opposite());
            }
            return this;
        }

        /**
         * Puts a belt on a square.
         *
         * @param position  the square
         * @param direction the direction the belt moves robots in
         * @param express   whether it is an express belt
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board or already
         *                                  has a belt
         */
        public Builder belt(Position position, Direction direction, boolean express) {
            requireInBounds(position);
            if (belts.putIfAbsent(position, new Belt(direction, express)) != null) {
                throw new IllegalArgumentException("Square " + position + " already has a belt");
            }
            return this;
        }

        /**
         * Gives a square a feature other than a crusher; use
         * {@link #crusher(Position, int...)} for crushers.
         *
         * @param position the square
         * @param feature  the feature; not {@link SquareFeature#CRUSHER}
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board, already
         *                                  has a feature, or {@code feature} is a
         *                                  crusher
         */
        public Builder feature(Position position, SquareFeature feature) {
            if (feature == SquareFeature.CRUSHER) {
                throw new IllegalArgumentException("Use crusher(...) to add a crusher");
            }
            requireInBounds(position);
            if (features.putIfAbsent(position, feature) != null) {
                throw new IllegalArgumentException("Square " + position + " already has a feature");
            }
            return this;
        }

        /**
         * Puts a crusher on a square. A crusher may share its square with a belt.
         *
         * @param position  the square
         * @param registers the registers (1 to 5) the crusher is active in
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board, already
         *                                  has a feature, or a register is not in
         *                                  the range 1 to 5
         */
        public Builder crusher(Position position, int... registers) {
            requireInBounds(position);
            Set<Integer> active = new HashSet<>();
            for (int register : registers) {
                requireRegister(register);
                active.add(register);
            }
            if (features.putIfAbsent(position, SquareFeature.CRUSHER) != null) {
                throw new IllegalArgumentException("Square " + position + " already has a feature");
            }
            crusherRegisters.put(position, Set.copyOf(active));
            return this;
        }

        /**
         * Mounts a laser on one side of a square, which also puts a wall there.
         *
         * @param position the square
         * @param side     the side to mount the laser on; it fires across the
         *                 board from there
         * @param beams    the number of beams, 1 to 3
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board or
         *                                  {@code beams} is not in the range 1 to 3
         */
        public Builder laser(Position position, Direction side, int beams) {
            requireInBounds(position);
            if (beams < 1 || beams > 3) {
                throw new IllegalArgumentException("A laser has 1 to 3 beams, was " + beams);
            }
            wall(position, side);
            lasers.add(new Laser(position, side, beams));
            return this;
        }

        /**
         * Mounts a pusher on one side of a square, which also puts a wall there.
         *
         * @param position  the square
         * @param side      the side to mount the pusher on; it pushes away from
         *                  that wall
         * @param registers the registers (1 to 5) the pusher is active in
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board or a
         *                                  register is not in the range 1 to 5
         */
        public Builder pusher(Position position, Direction side, int... registers) {
            requireInBounds(position);
            Set<Integer> active = new HashSet<>();
            for (int register : registers) {
                requireRegister(register);
                active.add(register);
            }
            wall(position, side);
            pushers.add(new Pusher(position, side, active));
            return this;
        }

        /**
         * Adds the next flag; the first call adds flag 1, the second flag 2, and
         * so on.
         *
         * @param position the flag's square
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board
         */
        public Builder flag(Position position) {
            requireInBounds(position);
            flags.add(position);
            return this;
        }

        /**
         * Adds a start square; seats are assigned in the order they are added.
         *
         * @param position the square
         * @param facing   the direction a robot starting there faces
         * @return this builder
         * @throws IllegalArgumentException if the square is off the board
         */
        public Builder startSquare(Position position, Direction facing) {
            requireInBounds(position);
            startSquares.add(new StartSquare(position, facing));
            return this;
        }

        /**
         * Creates the immutable board from everything added so far.
         *
         * @return the new board
         */
        public Board build() {
            return new Board(this);
        }

        /**
         * Records one side of a wall.
         *
         * @param position the square
         * @param side     the side of that square
         */
        private void addWallSide(Position position, Direction side) {
            walls.computeIfAbsent(position, p -> EnumSet.noneOf(Direction.class)).add(side);
        }

        /**
         * Returns whether a position is on the board under construction.
         *
         * @param position the position to test
         * @return {@code true} if it is within the grid
         */
        private boolean inBounds(Position position) {
            return position.x() >= 0 && position.x() < width && position.y() >= 0 && position.y() < height;
        }

        /**
         * Rejects positions that are off the board.
         *
         * @param position the position to check
         * @throws IllegalArgumentException if the position is off the board
         */
        private void requireInBounds(Position position) {
            if (!inBounds(position)) {
                throw new IllegalArgumentException("Position " + position + " is outside the " + width + "x" + height + " board");
            }
        }

        /**
         * Rejects register numbers outside 1 to 5.
         *
         * @param register the register number to check
         * @throws IllegalArgumentException if it is not in the range 1 to 5
         */
        private static void requireRegister(int register) {
            if (register < 1 || register > 5) {
                throw new IllegalArgumentException("Register must be 1 to 5, was " + register);
            }
        }
    }
}
