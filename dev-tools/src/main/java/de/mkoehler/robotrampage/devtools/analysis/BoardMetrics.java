package de.mkoehler.robotrampage.devtools.analysis;

import de.mkoehler.robotrampage.board.Belt;
import de.mkoehler.robotrampage.board.Board;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Laser;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;
import de.mkoehler.robotrampage.board.StartSquare;
import de.mkoehler.robotrampage.board.WalkingDistances;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The facts about a board that can be worked out at once, without playing it (design.md 3.13): how far each seat has to
 * walk, how fair that is between seats, and how much of the board is hazard or moves robots around. Cheap enough to be
 * recomputed on every edit, and meant to be the quick half of a board generator's rating as well.
 *
 * @param seats            one entry per start square, in seat order
 * @param flagRoute        the walking distance from flag 1 to the last flag, leg by leg, the same for every seat;
 *                         {@link WalkingDistances#UNREACHABLE} or more if some leg cannot be walked
 * @param firstFlagSpread  how many steps further the farthest seat is from flag 1 than the nearest
 * @param allReachable     whether every seat can walk to flag 1 and every flag to the next
 * @param squares          how many squares the board has
 * @param pits             how many squares are pits
 * @param belts            how many squares carry a belt of either kind
 * @param expressBelts     how many of those belts are express
 * @param gears            how many squares are gears
 * @param pushers          how many pushers there are
 * @param crushers         how many squares are crushers
 * @param repairSites      how many squares are repair sites
 * @param lasers           how many board lasers there are
 * @param laserSquares     how many squares lie in some board laser's line of fire
 * @author Mario Koehler
 */
public record BoardMetrics(List<SeatRoute> seats, int flagRoute, int firstFlagSpread, boolean allReachable, int squares,
                           int pits, int belts, int expressBelts, int gears, int pushers, int crushers, int repairSites,
                           int lasers, int laserSquares) {

    /**
     * How far one seat has to walk.
     *
     * @param seat        the seat, 0 for start square 1
     * @param toFirstFlag the walking distance from the start square to flag 1
     * @param route       the whole walk: to flag 1 and on through every flag in order
     */
    public record SeatRoute(int seat, int toFirstFlag, int route) {
    }

    /**
     * Rates a board.
     *
     * @param board the board; it needs at least one flag for the distances to mean anything
     * @return the metrics
     */
    public static BoardMetrics of(Board board) {
        List<Position> flags = board.flags();
        boolean reachable = !flags.isEmpty();
        int flagRoute = 0;
        for (int leg = 1; leg < flags.size(); leg++) {
            Position from = flags.get(leg - 1);
            int steps = WalkingDistances.to(board, flags.get(leg))[from.x()][from.y()];
            reachable &= steps < WalkingDistances.UNREACHABLE;
            flagRoute += steps;
        }
        List<SeatRoute> seats = new ArrayList<>();
        int nearest = Integer.MAX_VALUE;
        int farthest = 0;
        if (!flags.isEmpty()) {
            int[][] toFirst = WalkingDistances.to(board, flags.get(0));
            for (int seat = 0; seat < board.startSquares().size(); seat++) {
                StartSquare start = board.startSquares().get(seat);
                int steps = toFirst[start.position().x()][start.position().y()];
                reachable &= steps < WalkingDistances.UNREACHABLE;
                seats.add(new SeatRoute(seat, steps, steps + flagRoute));
                nearest = Math.min(nearest, steps);
                farthest = Math.max(farthest, steps);
            }
        }
        int spread = seats.isEmpty() ? 0 : farthest - nearest;

        int pits = 0;
        int gears = 0;
        int crushers = 0;
        int repairs = 0;
        for (SquareFeature feature : board.features().values()) {
            switch (feature) {
                case PIT -> pits++;
                case GEAR_CLOCKWISE, GEAR_COUNTERCLOCKWISE -> gears++;
                case CRUSHER -> crushers++;
                case REPAIR -> repairs++;
                case NONE -> {
                }
            }
        }
        int express = (int) board.belts().values().stream().filter(Belt::express).count();
        return new BoardMetrics(seats, flagRoute, spread, reachable, board.width() * board.height(), pits,
            board.belts().size(), express, gears, board.pushers().size(), crushers, repairs, board.lasers().size(),
            laserSquares(board).size());
    }

    /**
     * Returns every square some board laser's beam crosses when nobody stands in its way: from the emitter's own square
     * onwards until a wall or the edge of the board.
     *
     * @param board the board
     * @return the squares
     */
    public static Set<Position> laserSquares(Board board) {
        Set<Position> squares = new HashSet<>();
        for (Laser laser : board.lasers()) {
            Direction direction = laser.firingDirection();
            Position at = laser.position();
            squares.add(at);
            while (!board.hasWall(at, direction) && board.inBounds(at.step(direction))) {
                at = at.step(direction);
                squares.add(at);
            }
        }
        return squares;
    }

    /**
     * Returns the share of squares that are pits, crushers or in a laser's line of fire: how deadly the board is.
     *
     * @return the share, 0 to 1
     */
    public double hazardShare() {
        return squares == 0 ? 0 : (double) (pits + crushers + laserSquares) / squares;
    }

    /**
     * Returns the share of squares that move or turn a robot by themselves: belts and gears.
     *
     * @return the share, 0 to 1
     */
    public double movingShare() {
        return squares == 0 ? 0 : (double) (belts + gears) / squares;
    }
}
