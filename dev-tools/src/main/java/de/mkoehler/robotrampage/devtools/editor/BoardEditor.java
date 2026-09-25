package de.mkoehler.robotrampage.devtools.editor;

import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.board.SquareFeature;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What the board editor does with the pointer, without any drawing: the draft being edited, the chosen tool and its
 * options, and the gestures that change the draft. A gesture is a press, any number of drags onto other squares, and a
 * release; each gesture is one undo step.
 * <p>
 * Gestures per tool: a square tool paints every square the pointer passes (the second mouse button removes instead). A
 * belt drag lays each belt pointing to the next square, so a belt is drawn by dragging along its path. An edge tool keeps
 * the side it was pressed on for the whole drag, so a wall can be dragged along a row. Pressing a flag or a start square
 * picks it up and a drag moves it, keeping its number; pressing an empty square adds a new one there, which the same drag
 * can then move. Clicking a start square without dragging turns it a quarter clockwise.
 *
 * @author Mario Koehler
 */
public final class BoardEditor {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final EditHistory history = new EditHistory();
    private BoardDraft draft;
    private Tool tool = Tool.BELT;
    private Direction direction = Direction.NORTH;
    private int beams = 1;
    private final Set<Integer> registers = new TreeSet<>(Set.of(2, 4));
    private boolean dirty;
    private int revision;

    private boolean inGesture;
    private boolean removing;
    private boolean changedInGesture;
    private boolean moved;
    private Position last;
    private Direction side;
    private Position grabbed;
    private boolean grabbedExisting;

    /**
     * Creates an editor for a draft.
     *
     * @param draft the board to edit
     */
    public BoardEditor(BoardDraft draft) {
        this.draft = draft;
    }

    /**
     * Returns the board being edited.
     *
     * @return the draft
     */
    public BoardDraft draft() {
        return draft;
    }

    /**
     * Starts editing another board, with no undo history and nothing unsaved.
     *
     * @param replacement the board to edit
     */
    public void load(BoardDraft replacement) {
        draft = replacement;
        history.clear();
        dirty = false;
        revision++;
    }

    /**
     * Returns a number that changes whenever the draft does, so a view knows when to redraw.
     *
     * @return the revision
     */
    public int revision() {
        return revision;
    }

    /**
     * Returns whether the draft has changes that were not saved.
     *
     * @return {@code true} if there are unsaved changes
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Records that the draft was just saved.
     */
    public void markSaved() {
        dirty = false;
    }

    /**
     * Records a change that is not a gesture on the board, such as a new name, as unsaved.
     */
    public void markChanged() {
        dirty = true;
        revision++;
    }

    /**
     * Returns the chosen tool.
     *
     * @return the tool
     */
    public Tool tool() {
        return tool;
    }

    /**
     * Chooses a tool.
     *
     * @param chosen the tool
     */
    public void setTool(Tool chosen) {
        this.tool = chosen;
    }

    /**
     * Returns the direction new belts and start squares get.
     *
     * @return the direction
     */
    public Direction direction() {
        return direction;
    }

    /**
     * Chooses the direction new belts and start squares get.
     *
     * @param chosen the direction
     */
    public void setDirection(Direction chosen) {
        this.direction = chosen;
    }

    /**
     * Turns the chosen direction a quarter turn.
     *
     * @param clockwise {@code true} to turn clockwise
     */
    public void rotateDirection(boolean clockwise) {
        direction = clockwise ? direction.rotateRight() : direction.rotateLeft();
    }

    /**
     * Returns the number of beams new lasers get.
     *
     * @return 1 to 3
     */
    public int beams() {
        return beams;
    }

    /**
     * Chooses the number of beams new lasers get.
     *
     * @param chosen 1 to 3
     * @throws IllegalArgumentException if it is not 1 to 3
     */
    public void setBeams(int chosen) {
        if (chosen < 1 || chosen > 3) {
            throw new IllegalArgumentException("A laser has 1 to 3 beams, was " + chosen);
        }
        this.beams = chosen;
    }

    /**
     * Returns the registers new crushers and pushers are active in.
     *
     * @return the registers, never empty
     */
    public Set<Integer> registers() {
        return Set.copyOf(registers);
    }

    /**
     * Switches one register on or off for new crushers and pushers. The last register cannot be switched off, since a
     * crusher or pusher needs at least one.
     *
     * @param register 1 to 5
     * @return {@code true} if it changed
     */
    public boolean toggleRegister(int register) {
        if (registers.contains(register)) {
            return registers.size() > 1 && registers.remove(register);
        }
        return register >= 1 && register <= 5 && registers.add(register);
    }

    /**
     * Starts a gesture on a square.
     *
     * @param position the square pressed, on the board
     * @param pressedSide the side of that square nearest the pointer, used by edge tools
     * @param remove   {@code true} to remove what the tool places instead of placing it
     */
    public void press(Position position, Direction pressedSide, boolean remove) {
        history.record(draft);
        inGesture = true;
        removing = remove;
        changedInGesture = false;
        moved = false;
        last = position;
        side = pressedSide;
        grabbed = null;
        grabbedExisting = false;
        if (tool == Tool.FLAG && !remove) {
            grabbedExisting = draft.flags().contains(position);
            grabbed = position;
            if (!grabbedExisting) {
                change(draft.addFlag(position));
            }
        } else if (tool == Tool.START && !remove) {
            grabbedExisting = draft.startAt(position).isPresent();
            grabbed = position;
            if (!grabbedExisting) {
                change(draft.addStart(position, direction));
                grabbed = draft.startAt(position).isPresent() ? position : null;
            }
        } else if (isBelt(tool) && !remove) {
            change(draft.paintBelt(position, direction, tool == Tool.EXPRESS_BELT));
        } else {
            apply(position);
        }
    }

    /**
     * Continues a gesture onto the square now under the pointer. Nothing happens while the pointer stays on the square
     * it was last on, or outside a gesture.
     *
     * @param position the square under the pointer, on the board
     */
    public void drag(Position position) {
        if (!inGesture || position.equals(last)) {
            return;
        }
        moved = true;
        if (grabbed != null) {
            boolean movedIt = tool == Tool.FLAG ? draft.moveFlag(grabbed, position) : draft.moveStart(grabbed, position);
            if (movedIt) {
                grabbed = position;
                change(true);
            }
        } else if (isBelt(tool) && !removing) {
            walk(position);
        } else {
            apply(position);
        }
        last = position;
    }

    /**
     * Ends the gesture. A click on a start square that did not drag turns it a quarter clockwise. A gesture that
     * changed nothing leaves no undo step.
     */
    public void release() {
        if (!inGesture) {
            return;
        }
        if (tool == Tool.START && grabbedExisting && !moved) {
            draft.startAt(grabbed).ifPresent(start -> change(draft.faceStart(grabbed, start.facing().rotateRight())));
        }
        if (!changedInGesture) {
            history.dropLast();
        }
        inGesture = false;
        grabbed = null;
    }

    /**
     * Replaces the board with a generated one as a single step that undo takes back; the id, name and author stay.
     *
     * @param generated the generated board; a copy is taken
     */
    public void applyGenerated(BoardDraft generated) {
        history.record(draft);
        replaceWith(generated.copy());
    }

    /**
     * Undoes the last gesture.
     *
     * @return {@code true} if there was one
     */
    public boolean undo() {
        BoardDraft before = history.undo(draft);
        return replaceWith(before);
    }

    /**
     * Redoes the last undone gesture.
     *
     * @return {@code true} if there was one
     */
    public boolean redo() {
        BoardDraft after = history.redo(draft);
        return replaceWith(after);
    }

    /**
     * Returns whether a board identifier can be used as a file name: lower-case letters and digits in groups joined by
     * single hyphens, such as {@code proving-grounds}.
     *
     * @param id the identifier
     * @return {@code true} if it is usable
     */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * Finds the side of a square nearest to a point inside it.
     *
     * @param fractionX the point's horizontal position in the square, 0 (west) to 1 (east)
     * @param fractionY the point's vertical position in the square, 0 (south) to 1 (north)
     * @return the nearest side
     */
    public static Direction nearestSide(float fractionX, float fractionY) {
        float west = fractionX;
        float east = 1f - fractionX;
        float south = fractionY;
        float north = 1f - fractionY;
        float nearest = Math.min(Math.min(west, east), Math.min(south, north));
        if (nearest == north) {
            return Direction.NORTH;
        }
        if (nearest == east) {
            return Direction.EAST;
        }
        return nearest == south ? Direction.SOUTH : Direction.WEST;
    }

    /**
     * Applies the tool to one square, placing or removing what it places.
     *
     * @param position the square
     */
    private void apply(Position position) {
        boolean changed = switch (tool) {
            case ERASE -> draft.clearSquare(position);
            case PIT -> feature(position, SquareFeature.PIT);
            case REPAIR -> feature(position, SquareFeature.REPAIR);
            case GEAR_CLOCKWISE -> feature(position, SquareFeature.GEAR_CLOCKWISE);
            case GEAR_COUNTERCLOCKWISE -> feature(position, SquareFeature.GEAR_COUNTERCLOCKWISE);
            case CRUSHER -> removing ? draft.removeFeature(position, SquareFeature.CRUSHER)
                : draft.paintCrusher(position, registers);
            case BELT, EXPRESS_BELT -> draft.removeBelt(position);
            case WALL -> removing ? draft.removeWall(position, side) : draft.addWall(position, side);
            case LASER -> removing ? draft.removeMount(position, side) : draft.mountLaser(position, side, beams);
            case PUSHER -> removing ? draft.removeMount(position, side) : draft.mountPusher(position, side, registers);
            case FLAG -> draft.removeFlag(position);
            case START -> draft.removeStart(position);
        };
        change(changed);
    }

    /**
     * Paints or removes a pit, gear or repair site.
     *
     * @param position the square
     * @param feature  the feature
     * @return {@code true} if the square changed
     */
    private boolean feature(Position position, SquareFeature feature) {
        return removing ? draft.removeFeature(position, feature) : draft.paintFeature(position, feature);
    }

    /**
     * Continues a belt from the last square to another one, one neighbouring square at a time (first along the row, then
     * along the column), so a fast drag that skipped squares still lays a connected belt. Each step points the belt it
     * leaves at the square it enters.
     *
     * @param target the square the pointer is on now
     */
    private void walk(Position target) {
        boolean express = tool == Tool.EXPRESS_BELT;
        Position at = last;
        while (!at.equals(target)) {
            Direction step = at.x() != target.x()
                ? (target.x() > at.x() ? Direction.EAST : Direction.WEST)
                : (target.y() > at.y() ? Direction.NORTH : Direction.SOUTH);
            change(draft.paintBelt(at, step, express));
            at = at.step(step);
            change(draft.paintBelt(at, step, express));
            direction = step;
        }
    }

    /**
     * Notes whether a step of the gesture changed the draft.
     *
     * @param changed {@code true} if it did
     */
    private void change(boolean changed) {
        if (changed) {
            changedInGesture = true;
            dirty = true;
            revision++;
        }
    }

    /**
     * Switches to a draft from the undo history. The id, name and author stay as they are now: they are typed into
     * fields rather than drawn, and undo only steps through what was done on the board.
     *
     * @param replacement the draft, or {@code null} if the history had none
     * @return {@code true} if there was one
     */
    private boolean replaceWith(BoardDraft replacement) {
        if (replacement == null) {
            return false;
        }
        replacement.setMetadata(draft.id(), draft.name(), draft.author());
        draft = replacement;
        dirty = true;
        revision++;
        return true;
    }

    /**
     * Returns whether a tool lays belts.
     *
     * @param candidate the tool
     * @return {@code true} for the two belt tools
     */
    private static boolean isBelt(Tool candidate) {
        return candidate == Tool.BELT || candidate == Tool.EXPRESS_BELT;
    }
}
