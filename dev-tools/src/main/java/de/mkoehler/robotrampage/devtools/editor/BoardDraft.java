package de.mkoehler.robotrampage.devtools.editor;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.BoardConverter;
import de.mkoehler.robotrampage.board.BoardDefinition;
import de.mkoehler.robotrampage.board.BoardValidator;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.board.ValidationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A 12x12 board being edited: mutable, and always in a shape that can be turned into a {@link Board} and a canonical
 * {@link BoardDefinition}, even while it is not yet a valid board (no flag, a flag on a pit, ...). Whether it is valid is
 * a separate question answered by {@link #validate()}.
 * <p>
 * The draft keeps plain walls, lasers and pushers apart, unlike {@link Board}, which folds the wall a laser or pusher
 * implies into its walls: removing a laser therefore never leaves a stray wall behind. A laser or pusher is mounted on one
 * side of one square (a laser on the east side of a square fires west, a laser on the west side of its eastern neighbour
 * fires east, although both sit on the same edge), and one side holds at most one of them. A plain wall on an edge that a
 * mount already walls off is dropped, exactly as the canonical export would drop it.
 * <p>
 * A square has at most one belt and one feature, and only a crusher may share its square with a belt: painting a pit,
 * gear or repair site clears the belt, and painting a belt clears any feature but a crusher. Flags and start squares are
 * ordered lists (flag 1 first, seat 1 first); moving one keeps its number.
 *
 * @author Mario Koehler
 */
public final class BoardDraft {

    /**
     * The width and height of every board the editor makes.
     */
    public static final int SIZE = 12;

    /**
     * One side of one square, the place a laser or pusher is mounted on.
     *
     * @param position the square
     * @param side     the side of it
     */
    public record Mount(Position position, Direction side) {
    }

    private String id;
    private String name;
    private String author;
    private final Map<Position, Belt> belts = new HashMap<>();
    private final Map<Position, SquareFeature> features = new HashMap<>();
    private final Map<Position, Set<Integer>> crusherRegisters = new HashMap<>();
    private final Set<Mount> walls = new HashSet<>();
    private final Map<Mount, Integer> lasers = new LinkedHashMap<>();
    private final Map<Mount, Set<Integer>> pushers = new LinkedHashMap<>();
    private final List<Position> flags = new ArrayList<>();
    private final List<StartSquare> starts = new ArrayList<>();

    /**
     * Creates an empty board: plain floor, no walls, no flags, no start squares.
     *
     * @param id     the board's identifier
     * @param name   the board's name
     * @param author the author, or {@code null}
     */
    public BoardDraft(String id, String name, String author) {
        this.id = id;
        this.name = name;
        this.author = author;
    }

    /**
     * Creates a draft of an existing board.
     *
     * @param definition the board; it must be {@value #SIZE}x{@value #SIZE} and structurally sound (as every board read by
     *                   {@code BoardLoader} is)
     * @return the draft
     * @throws IllegalArgumentException if the board has another size
     */
    public static BoardDraft of(BoardDefinition definition) {
        if (definition.width() != SIZE || definition.height() != SIZE) {
            throw new IllegalArgumentException("The editor only edits " + SIZE + "x" + SIZE + " boards, this one is "
                + definition.width() + "x" + definition.height());
        }
        BoardDraft draft = new BoardDraft(definition.id(), definition.name(), definition.author());
        for (BoardDefinition.Square square : definition.squares()) {
            Position position = new Position(square.x(), square.y());
            if (square.belt() != null) {
                draft.belts.put(position, new Belt(square.belt().dir(), square.belt().express()));
            }
            if (square.feature() == SquareFeature.CRUSHER) {
                draft.features.put(position, SquareFeature.CRUSHER);
                draft.crusherRegisters.put(position, new TreeSet<>(square.registers()));
            } else if (square.feature() != null && square.feature() != SquareFeature.NONE) {
                draft.features.put(position, square.feature());
            }
        }
        for (BoardDefinition.Edge edge : definition.edges()) {
            Mount mount = new Mount(new Position(edge.x(), edge.y()), edge.side());
            if (edge.laser() != null) {
                draft.lasers.put(mount, edge.laser().beams());
            } else if (edge.pusher() != null) {
                draft.pushers.put(mount, new TreeSet<>(edge.pusher().registers()));
            } else if (edge.wall()) {
                draft.walls.add(canonical(mount));
            }
        }
        draft.walls.removeIf(draft::isMountedEdge);
        definition.flags().forEach(flag -> draft.flags.add(new Position(flag.x(), flag.y())));
        definition.startSquares().forEach(start ->
            draft.starts.add(new StartSquare(new Position(start.x(), start.y()), start.facing())));
        return draft;
    }

    /**
     * Returns an independent copy, for example to remember for undo.
     *
     * @return the copy
     */
    public BoardDraft copy() {
        BoardDraft copy = new BoardDraft(id, name, author);
        copy.belts.putAll(belts);
        copy.features.putAll(features);
        crusherRegisters.forEach((position, registers) -> copy.crusherRegisters.put(position, new TreeSet<>(registers)));
        copy.walls.addAll(walls);
        copy.lasers.putAll(lasers);
        pushers.forEach((mount, registers) -> copy.pushers.put(mount, new TreeSet<>(registers)));
        copy.flags.addAll(flags);
        copy.starts.addAll(starts);
        return copy;
    }

    /**
     * Returns the board's identifier, which is also its file name.
     *
     * @return the identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the board's name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the author.
     *
     * @return the author, or {@code null}
     */
    public String author() {
        return author;
    }

    /**
     * Sets the identifier, the name and the author.
     *
     * @param newId     the identifier
     * @param newName   the name
     * @param newAuthor the author; blank means none
     */
    public void setMetadata(String newId, String newName, String newAuthor) {
        this.id = newId;
        this.name = newName;
        this.author = newAuthor == null || newAuthor.isBlank() ? null : newAuthor;
    }

    /**
     * Returns whether a square lies on the board.
     *
     * @param position the square
     * @return {@code true} if it does
     */
    public static boolean inBounds(Position position) {
        return position.x() >= 0 && position.x() < SIZE && position.y() >= 0 && position.y() < SIZE;
    }

    /**
     * Paints a pit, a gear or a repair site on a square, clearing any belt and crusher there.
     *
     * @param position the square
     * @param feature  {@link SquareFeature#PIT}, a gear or {@link SquareFeature#REPAIR}
     * @return {@code true} if the square changed
     * @throws IllegalArgumentException for {@link SquareFeature#NONE} or {@link SquareFeature#CRUSHER}, which have their
     *                                  own methods
     */
    public boolean paintFeature(Position position, SquareFeature feature) {
        if (feature == SquareFeature.NONE || feature == SquareFeature.CRUSHER) {
            throw new IllegalArgumentException("Use clearSquare or paintCrusher for " + feature);
        }
        requireInBounds(position);
        if (features.get(position) == feature && !belts.containsKey(position)) {
            return false;
        }
        belts.remove(position);
        crusherRegisters.remove(position);
        features.put(position, feature);
        return true;
    }

    /**
     * Puts a crusher on a square, replacing any other feature; a belt there stays, since a crusher may sit on a belt.
     *
     * @param position  the square
     * @param registers the registers (1 to 5) it is active in, at least one
     * @return {@code true} if the square changed
     * @throws IllegalArgumentException if no register or an invalid one is given
     */
    public boolean paintCrusher(Position position, Set<Integer> registers) {
        requireInBounds(position);
        Set<Integer> active = registers(registers);
        if (features.get(position) == SquareFeature.CRUSHER && crusherRegisters.get(position).equals(active)) {
            return false;
        }
        features.put(position, SquareFeature.CRUSHER);
        crusherRegisters.put(position, active);
        return true;
    }

    /**
     * Lays a belt on a square, replacing a belt that is there and clearing any feature but a crusher.
     *
     * @param position  the square
     * @param direction the direction the belt moves robots in
     * @param express   {@code true} for an express belt
     * @return {@code true} if the square changed
     */
    public boolean paintBelt(Position position, Direction direction, boolean express) {
        requireInBounds(position);
        Belt belt = new Belt(direction, express);
        SquareFeature feature = features.get(position);
        boolean keepsFeature = feature == null || feature == SquareFeature.CRUSHER;
        if (belt.equals(belts.get(position)) && keepsFeature) {
            return false;
        }
        if (!keepsFeature) {
            features.remove(position);
        }
        belts.put(position, belt);
        return true;
    }

    /**
     * Turns a square back into plain floor: removes its belt, feature, flag and start square, and the lasers and pushers
     * mounted on its sides. Plain walls stay, since an edge belongs to the neighbouring square as much as to this one; the
     * wall tool removes them. A removed flag or start square moves the later ones up one number, as removing it with its
     * own tool does.
     *
     * @param position the square
     * @return {@code true} if the square changed
     */
    public boolean clearSquare(Position position) {
        boolean changed = belts.remove(position) != null | features.remove(position) != null;
        crusherRegisters.remove(position);
        changed |= removeFlag(position);
        changed |= removeStart(position);
        for (Direction side : Direction.values()) {
            changed |= removeMount(position, side);
        }
        return changed;
    }

    /**
     * Removes only the belt of a square.
     *
     * @param position the square
     * @return {@code true} if there was a belt
     */
    public boolean removeBelt(Position position) {
        return belts.remove(position) != null;
    }

    /**
     * Removes only the feature of a square, if it is the given one.
     *
     * @param position the square
     * @param feature  the feature to remove
     * @return {@code true} if the square had that feature
     */
    public boolean removeFeature(Position position, SquareFeature feature) {
        if (features.get(position) != feature) {
            return false;
        }
        features.remove(position);
        crusherRegisters.remove(position);
        return true;
    }

    /**
     * Puts a plain wall on one side of a square. Nothing happens where a laser or pusher already walls the edge off.
     *
     * @param position the square
     * @param side     the side
     * @return {@code true} if a wall was added
     */
    public boolean addWall(Position position, Direction side) {
        requireInBounds(position);
        Mount mount = new Mount(position, side);
        return !isMountedEdge(mount) && walls.add(canonical(mount));
    }

    /**
     * Removes the plain wall on one side of a square; the wall of a laser or pusher stays with it.
     *
     * @param position the square
     * @param side     the side
     * @return {@code true} if a wall was removed
     */
    public boolean removeWall(Position position, Direction side) {
        return walls.remove(canonical(new Mount(position, side)));
    }

    /**
     * Mounts a laser on one side of a square, replacing a laser or pusher there. It fires away from that side.
     *
     * @param position the square
     * @param side     the side it is mounted on
     * @param beams    the number of beams, 1 to 3
     * @return {@code true} if the board changed
     * @throws IllegalArgumentException if {@code beams} is not 1 to 3
     */
    public boolean mountLaser(Position position, Direction side, int beams) {
        requireInBounds(position);
        if (beams < 1 || beams > 3) {
            throw new IllegalArgumentException("A laser has 1 to 3 beams, was " + beams);
        }
        Mount mount = new Mount(position, side);
        if (Integer.valueOf(beams).equals(lasers.get(mount))) {
            return false;
        }
        pushers.remove(mount);
        lasers.put(mount, beams);
        walls.remove(canonical(mount));
        return true;
    }

    /**
     * Mounts a pusher on one side of a square, replacing a laser or pusher there. It pushes away from that side.
     *
     * @param position  the square
     * @param side      the side it is mounted on
     * @param registers the registers (1 to 5) it is active in, at least one
     * @return {@code true} if the board changed
     * @throws IllegalArgumentException if no register or an invalid one is given
     */
    public boolean mountPusher(Position position, Direction side, Set<Integer> registers) {
        requireInBounds(position);
        Set<Integer> active = registers(registers);
        Mount mount = new Mount(position, side);
        if (active.equals(pushers.get(mount))) {
            return false;
        }
        lasers.remove(mount);
        pushers.put(mount, active);
        walls.remove(canonical(mount));
        return true;
    }

    /**
     * Removes the laser or pusher on one side of a square, together with the wall it implied.
     *
     * @param position the square
     * @param side     the side
     * @return {@code true} if there was one
     */
    public boolean removeMount(Position position, Direction side) {
        Mount mount = new Mount(position, side);
        return lasers.remove(mount) != null | pushers.remove(mount) != null;
    }

    /**
     * Adds the next flag on a square that has none.
     *
     * @param position the square
     * @return {@code true} if a flag was added
     */
    public boolean addFlag(Position position) {
        requireInBounds(position);
        if (flags.contains(position)) {
            return false;
        }
        flags.add(position);
        return true;
    }

    /**
     * Removes the flag on a square; the flags after it move up one number.
     *
     * @param position the square
     * @return {@code true} if there was a flag
     */
    public boolean removeFlag(Position position) {
        return flags.remove(position);
    }

    /**
     * Moves a flag to another square, keeping its number. Nothing happens if the target already has a flag.
     *
     * @param from the square the flag is on
     * @param to   the square to move it to
     * @return {@code true} if it moved
     */
    public boolean moveFlag(Position from, Position to) {
        int index = flags.indexOf(from);
        if (index < 0 || !inBounds(to) || flags.contains(to)) {
            return false;
        }
        flags.set(index, to);
        return true;
    }

    /**
     * Adds a start square for the next seat, on a square that is not one yet. A board has at most
     * {@value BoardValidator#MAX_START_SQUARES}.
     *
     * @param position the square
     * @param facing   the direction a robot starting there faces
     * @return {@code true} if a start square was added
     */
    public boolean addStart(Position position, Direction facing) {
        requireInBounds(position);
        if (startAt(position).isPresent() || starts.size() >= BoardValidator.MAX_START_SQUARES) {
            return false;
        }
        starts.add(new StartSquare(position, facing));
        return true;
    }

    /**
     * Removes the start square on a square; the seats after it move up one number.
     *
     * @param position the square
     * @return {@code true} if there was one
     */
    public boolean removeStart(Position position) {
        return starts.removeIf(start -> start.position().equals(position));
    }

    /**
     * Moves a start square to another square, keeping its seat and facing. Nothing happens if the target is already one.
     *
     * @param from the square the start square is on
     * @param to   the square to move it to
     * @return {@code true} if it moved
     */
    public boolean moveStart(Position from, Position to) {
        int index = indexOfStart(from);
        if (index < 0 || !inBounds(to) || startAt(to).isPresent()) {
            return false;
        }
        starts.set(index, new StartSquare(to, starts.get(index).facing()));
        return true;
    }

    /**
     * Turns the start square on a square to face another way.
     *
     * @param position the square
     * @param facing   the new facing
     * @return {@code true} if it changed
     */
    public boolean faceStart(Position position, Direction facing) {
        int index = indexOfStart(position);
        if (index < 0 || starts.get(index).facing() == facing) {
            return false;
        }
        starts.set(index, new StartSquare(position, facing));
        return true;
    }

    /**
     * Returns the flags in order.
     *
     * @return flag 1 first
     */
    public List<Position> flags() {
        return List.copyOf(flags);
    }

    /**
     * Returns the start squares in seat order.
     *
     * @return seat 1 first
     */
    public List<StartSquare> starts() {
        return List.copyOf(starts);
    }

    /**
     * Returns the start square on a square.
     *
     * @param position the square
     * @return the start square, or empty
     */
    public Optional<StartSquare> startAt(Position position) {
        return starts.stream().filter(start -> start.position().equals(position)).findFirst();
    }

    /**
     * Returns the crusher registers of every crusher.
     *
     * @return the registers by square
     */
    public Map<Position, Set<Integer>> crushers() {
        return Map.copyOf(crusherRegisters);
    }

    /**
     * Returns whether a side of a square carries a laser or pusher.
     *
     * @param position the square
     * @param side     the side
     * @return {@code true} if something is mounted there
     */
    public boolean hasMount(Position position, Direction side) {
        Mount mount = new Mount(position, side);
        return lasers.containsKey(mount) || pushers.containsKey(mount);
    }

    /**
     * Returns whether the edge on a side of a square has a plain wall.
     *
     * @param position the square
     * @param side     the side
     * @return {@code true} if there is a plain wall
     */
    public boolean hasWall(Position position, Direction side) {
        return walls.contains(canonical(new Mount(position, side)));
    }

    /**
     * Builds the runtime board, for drawing and checking. Works for every draft, valid or not.
     *
     * @return the board
     */
    public Board toBoard() {
        Board.Builder builder = new Board.Builder(SIZE, SIZE);
        belts.forEach((position, belt) -> builder.belt(position, belt.direction(), belt.express()));
        features.forEach((position, feature) -> {
            if (feature == SquareFeature.CRUSHER) {
                builder.crusher(position, crusherRegisters.get(position).stream().mapToInt(Integer::intValue).toArray());
            } else {
                builder.feature(position, feature);
            }
        });
        for (Mount wall : walls) {
            Mount onBoard = inBounds(wall.position()) ? wall
                : new Mount(wall.position().step(wall.side()), wall.side().opposite());
            builder.wall(onBoard.position(), onBoard.side());
        }
        lasers.forEach((mount, beams) -> builder.laser(mount.position(), mount.side(), beams));
        pushers.forEach((mount, registers) -> builder.pusher(mount.position(), mount.side(),
            registers.stream().mapToInt(Integer::intValue).toArray()));
        flags.forEach(builder::flag);
        starts.forEach(start -> builder.startSquare(start.position(), start.facing()));
        return builder.build();
    }

    /**
     * Exports the board in the canonical form {@code BoardLoader} writes and the game reads.
     *
     * @return the definition
     */
    public BoardDefinition toDefinition() {
        return BoardConverter.toDefinition(toBoard(), id, name, author, null, null);
    }

    /**
     * Checks the board with the same validator the game uses to load it.
     *
     * @return the errors (the board could not be loaded) and warnings (legal but suspicious), each listed once although
     *         the validator's two levels report some problems (no flag, no start square) twice
     */
    public ValidationResult validate() {
        ValidationResult both = BoardValidator.validate(toDefinition()).plus(BoardValidator.validate(toBoard()));
        return new ValidationResult(both.errors().stream().distinct().toList(),
            both.warnings().stream().distinct().toList());
    }

    /**
     * Returns whether the edge of a mount is already walled off by a laser or pusher, from either side.
     *
     * @param mount the side of a square
     * @return {@code true} if a laser or pusher sits on that edge
     */
    private boolean isMountedEdge(Mount mount) {
        Mount other = new Mount(mount.position().step(mount.side()), mount.side().opposite());
        return lasers.containsKey(mount) || pushers.containsKey(mount)
            || lasers.containsKey(other) || pushers.containsKey(other);
    }

    /**
     * Returns the index of the start square on a square.
     *
     * @param position the square
     * @return the index, or -1
     */
    private int indexOfStart(Position position) {
        for (int index = 0; index < starts.size(); index++) {
            if (starts.get(index).position().equals(position)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Names an edge independently of the side it is described from: by the square south or west of it, and
     * {@code NORTH} or {@code EAST}. That square may lie off the board for an outer edge.
     *
     * @param mount a side of a square
     * @return the same edge in canonical form
     */
    private static Mount canonical(Mount mount) {
        return switch (mount.side()) {
            case NORTH, EAST -> mount;
            case SOUTH -> new Mount(mount.position().step(Direction.SOUTH), Direction.NORTH);
            case WEST -> new Mount(mount.position().step(Direction.WEST), Direction.EAST);
        };
    }

    /**
     * Checks and copies a register list.
     *
     * @param registers the registers
     * @return a sorted copy
     * @throws IllegalArgumentException if it is empty or holds a number outside 1 to 5
     */
    private static Set<Integer> registers(Set<Integer> registers) {
        if (registers.isEmpty()) {
            throw new IllegalArgumentException("At least one register is needed");
        }
        for (int register : registers) {
            if (register < 1 || register > 5) {
                throw new IllegalArgumentException("Register must be 1 to 5, was " + register);
            }
        }
        return new TreeSet<>(registers);
    }

    /**
     * Rejects squares off the board.
     *
     * @param position the square
     * @throws IllegalArgumentException if it is off the board
     */
    private static void requireInBounds(Position position) {
        if (!inBounds(position)) {
            throw new IllegalArgumentException(position + " is off the " + SIZE + "x" + SIZE + " board");
        }
    }
}
