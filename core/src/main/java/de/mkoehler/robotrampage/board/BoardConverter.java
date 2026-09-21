package de.mkoehler.robotrampage.board;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts between the JSON model ({@link BoardDefinition}) and the runtime {@link Board}.
 * <p>
 * The export is <em>canonical</em>: a wall between two squares is written once (as the north or
 * east side of one of them, whichever way it was added), a wall that is only implied by a laser
 * or pusher mount is not written separately, and everything is sorted. Exporting, importing and
 * exporting again therefore gives the identical definition, which is what lets generators and
 * editors produce stable, diff-friendly files.
 *
 * @author Mario Koehler
 */
public final class BoardConverter {

    /**
     * One physical edge between two squares (or on the outer border), identified independently of
     * which of its two squares it is described from: {@code square} is always the south or west one,
     * and {@code direction} is {@code NORTH} or {@code EAST}. The square may lie off the board for an
     * outer south or west edge.
     *
     * @param square    the square on the south or west side of the edge
     * @param direction {@code NORTH} for a horizontal edge, {@code EAST} for a vertical one
     */
    private record EdgeKey(Position square, Direction direction) {

        /**
         * Identifies the edge on a given side of a given square.
         *
         * @param square the square
         * @param side   the side of it
         * @return the canonical key of that edge
         */
        static EdgeKey of(Position square, Direction side) {
            return switch (side) {
                case NORTH, EAST -> new EdgeKey(square, side);
                case SOUTH -> new EdgeKey(square.step(Direction.SOUTH), Direction.NORTH);
                case WEST -> new EdgeKey(square.step(Direction.WEST), Direction.EAST);
            };
        }
    }

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private BoardConverter() {
    }

    /**
     * Builds a runtime board from a definition. The definition must have passed
     * {@link BoardValidator#validate(BoardDefinition)}.
     *
     * @param definition the validated definition
     * @return the board
     * @throws IllegalArgumentException if the definition contains something the board builder
     *                                  rejects, which a validated definition never does
     */
    public static Board toBoard(BoardDefinition definition) {
        Board.Builder builder = new Board.Builder(definition.width(), definition.height());
        for (BoardDefinition.Square square : definition.squares()) {
            Position position = new Position(square.x(), square.y());
            if (square.belt() != null) {
                builder.belt(position, square.belt().dir(), square.belt().express());
            }
            SquareFeature feature = square.feature();
            if (feature == SquareFeature.CRUSHER) {
                builder.crusher(position, toArray(square.registers()));
            } else if (feature != null && feature != SquareFeature.NONE) {
                builder.feature(position, feature);
            }
        }
        for (BoardDefinition.Edge edge : definition.edges()) {
            Position position = new Position(edge.x(), edge.y());
            if (edge.wall()) {
                builder.wall(position, edge.side());
            }
            if (edge.laser() != null) {
                builder.laser(position, edge.side(), edge.laser().beams());
            }
            if (edge.pusher() != null) {
                builder.pusher(position, edge.side(), toArray(edge.pusher().registers()));
            }
        }
        for (BoardDefinition.Flag flag : definition.flags()) {
            builder.flag(new Position(flag.x(), flag.y()));
        }
        for (BoardDefinition.Start start : definition.startSquares()) {
            builder.startSquare(new Position(start.x(), start.y()), start.facing());
        }
        return builder.build();
    }

    /**
     * Exports a runtime board as a canonical definition.
     *
     * @param board     the board to export
     * @param id        the board's identifier
     * @param name      the board's name
     * @param author    the author, or {@code null}
     * @param generator the id of the generator that made the board, or {@code null}
     * @param seed      the generator's seed, or {@code null}
     * @return the definition
     */
    public static BoardDefinition toDefinition(Board board, String id, String name, String author, String generator,
                                               Long seed) {
        Set<Position> squarePositions = new HashSet<>(board.belts().keySet());
        squarePositions.addAll(board.features().keySet());
        List<BoardDefinition.Square> squares = squarePositions.stream()
            .sorted(Comparator.comparingInt(Position::y).thenComparingInt(Position::x))
            .map(position -> toSquare(board, position))
            .toList();

        List<BoardDefinition.Edge> edges = new ArrayList<>();
        Set<EdgeKey> mounted = new HashSet<>();
        for (Laser laser : board.lasers()) {
            mounted.add(EdgeKey.of(laser.position(), laser.side()));
            edges.add(new BoardDefinition.Edge(laser.position().x(), laser.position().y(), laser.side(), false,
                new BoardDefinition.Laser(laser.beams()), null));
        }
        for (Pusher pusher : board.pushers()) {
            mounted.add(EdgeKey.of(pusher.position(), pusher.side()));
            edges.add(new BoardDefinition.Edge(pusher.position().x(), pusher.position().y(), pusher.side(), false, null,
                new BoardDefinition.Pusher(pusher.registers().stream().sorted().toList())));
        }
        Set<EdgeKey> written = new LinkedHashSet<>();
        board.walls().forEach((position, sides) -> {
            for (Direction side : sides) {
                EdgeKey key = EdgeKey.of(position, side);
                if (!mounted.contains(key) && written.add(key)) {
                    edges.add(wallEdge(board, key, position, side));
                }
            }
        });
        edges.sort(Comparator.<BoardDefinition.Edge>comparingInt(BoardDefinition.Edge::y)
            .thenComparingInt(BoardDefinition.Edge::x)
            .thenComparing(edge -> edge.side().ordinal()));

        List<BoardDefinition.Flag> flags = board.flags().stream()
            .map(position -> new BoardDefinition.Flag(position.x(), position.y())).toList();
        List<BoardDefinition.Start> starts = board.startSquares().stream()
            .map(start -> new BoardDefinition.Start(start.position().x(), start.position().y(), start.facing())).toList();
        return new BoardDefinition(BoardDefinition.FORMAT_VERSION, id, name, author, generator, seed, board.width(),
            board.height(), squares, edges, flags, starts);
    }

    /**
     * Describes the belt and feature of one square.
     *
     * @param board    the board
     * @param position the square
     * @return the square entry
     */
    private static BoardDefinition.Square toSquare(Board board, Position position) {
        BoardDefinition.Belt belt = board.beltAt(position)
            .map(b -> new BoardDefinition.Belt(b.direction(), b.express())).orElse(null);
        SquareFeature feature = board.featureAt(position);
        List<Integer> registers = feature == SquareFeature.CRUSHER
            ? board.crusherRegisters(position).stream().sorted().toList() : List.of();
        return new BoardDefinition.Square(position.x(), position.y(), belt,
            feature == SquareFeature.NONE ? null : feature, registers);
    }

    /**
     * Describes a plain wall, from the square on the in-bounds side of its edge where there is
     * one.
     *
     * @param board    the board
     * @param key      the wall's canonical edge
     * @param position the square the wall was found on
     * @param side     the side of that square it was found on
     * @return the edge entry
     */
    private static BoardDefinition.Edge wallEdge(Board board, EdgeKey key, Position position, Direction side) {
        if (board.inBounds(key.square())) {
            return new BoardDefinition.Edge(key.square().x(), key.square().y(), key.direction(), true, null, null);
        }
        return new BoardDefinition.Edge(position.x(), position.y(), side, true, null, null);
    }

    /**
     * Converts a list of registers to the array the board builder takes.
     *
     * @param registers the registers
     * @return the same numbers as an array
     */
    private static int[] toArray(List<Integer> registers) {
        return registers.stream().mapToInt(Integer::intValue).toArray();
    }
}
