package de.mkoehler.robotrampage.devtools.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo for a {@link BoardDraft}: remembers whole copies of the draft, one per editing gesture (a click, or a
 * whole drag), up to a fixed number of steps.
 *
 * @author Mario Koehler
 */
public final class EditHistory {

    private static final int LIMIT = 200;

    private final Deque<BoardDraft> undo = new ArrayDeque<>();
    private final Deque<BoardDraft> redo = new ArrayDeque<>();

    /**
     * Remembers the draft as it is before a change, and forgets everything that could be redone.
     *
     * @param before the draft before the change; a copy is kept
     */
    public void record(BoardDraft before) {
        undo.push(before.copy());
        if (undo.size() > LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    /**
     * Forgets the most recent {@link #record}, for a gesture that turned out to change nothing.
     */
    public void dropLast() {
        undo.poll();
    }

    /**
     * Steps back one change.
     *
     * @param current the draft as it is now, which becomes redoable
     * @return the draft before the last change, or {@code null} if there is nothing to undo
     */
    public BoardDraft undo(BoardDraft current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current.copy());
        return undo.pop();
    }

    /**
     * Steps forward again one undone change.
     *
     * @param current the draft as it is now, which becomes undoable
     * @return the draft after the undone change, or {@code null} if there is nothing to redo
     */
    public BoardDraft redo(BoardDraft current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current.copy());
        return redo.pop();
    }

    /**
     * Forgets everything, for example when another board is opened.
     */
    public void clear() {
        undo.clear();
        redo.clear();
    }
}
