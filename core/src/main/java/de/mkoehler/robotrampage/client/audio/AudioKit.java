package de.mkoehler.robotrampage.client.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The client's sound effects: one short {@link Sound}, loaded whole, per {@link Clip}, played from wherever the thing it
 * marks happens. Owned by {@code RobotRampageGame} for the life of the application, the same way {@code UiKit} is.
 *
 * @author Mario Koehler
 */
public final class AudioKit implements Disposable {

    /**
     * One sound effect, named for what it marks, with the file it is loaded from under {@code assets/sfx}.
     */
    public enum Clip {

        /** A connection attempt starts (the "Connecting" dialog opens). */
        CONNECTING("connecting.mp3"),
        /** A card is placed into a register from the hand. */
        PLACE_CARD("place_card.mp3"),
        /** A card is taken out of a register, back into the hand. */
        RETURN_CARD("return_card.mp3"),
        /** A program is confirmed and sent to the server. */
        PROGRAM_LOCKED_IN("program_locked_in.mp3"),
        /** A robot is destroyed, whether or not that also eliminates it. */
        ROBOT_DIES("robot_dies.mp3"),
        /** A laser volley that actually hits somebody fires. */
        LASER("laser.mp3"),
        /** A request was refused, or a connection problem is shown. */
        PROBLEM_OR_ERROR("problem_or_error.mp3"),
        /** Looped while 30 seconds or less, but more than 10, are left on the programming timer. */
        WARNING_30("30_second_warning.mp3"),
        /** Looped while 10 seconds or less are left on the programming timer. */
        WARNING_10("10_second_warning.mp3"),
        /** A player joins a lobby this player is already in. */
        PLAYER_JOINS_LOBBY("player_joins_lobby.mp3"),
        /** A player leaves a lobby this player is in. */
        PLAYER_LEFT_LOBBY("player_left_lobby.mp3"),
        /** A player confirms powering their robot down next turn (not cancelling one already chosen). */
        POWER_DOWN_NEXT_TURN("power_down_next_turn.mp3"),
        /** The default click every button plays unless it already has a more specific sound of its own. */
        BUTTON_CLICK("button_click.mp3"),
        /** The Game Over screen appears, showing the final standings. */
        GAME_WON("game_won.mp3"),
        /** The application finishes loading and the Startup screen appears. */
        WELCOME_JINGLE("welcome_jingle.mp3"),
        /** Not wired to anything yet; kept loaded and ready for whatever earns it. */
        BEEP_1("beep_1.mp3"),
        /** Not wired to anything yet; kept loaded and ready for whatever earns it. */
        BEEP_2("beep_2.mp3"),
        /** Not wired to anything yet; kept loaded and ready for whatever earns it. */
        BEEP_3("beep_3.mp3"),
        /** Not wired to anything yet; kept loaded and ready for whatever earns it. */
        BEEP_4("beep_4.mp3");

        private final String file;

        Clip(String file) {
            this.file = file;
        }
    }

    private final Map<Clip, Sound> sounds = new EnumMap<>(Clip.class);
    private Clip countdownClip;
    private long countdownId = -1;
    private float volume = 1f;

    /**
     * Loads every clip from {@code assets/sfx}.
     */
    public AudioKit() {
        for (Clip clip : Clip.values()) {
            FileHandle file = Gdx.files.internal("sfx/" + clip.file);
            sounds.put(clip, Gdx.audio.newSound(file));
        }
    }

    /**
     * Sets the master volume every clip plays at from now on, including a countdown warning loop already playing (so
     * turning the volume down takes effect at once, not only the next time a clip starts).
     *
     * @param volume the volume, 0 to 1; clamped if out of range
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
        if (countdownClip != null) {
            sounds.get(countdownClip).setVolume(countdownId, this.volume);
        }
    }

    /**
     * Plays a clip once, at the current master volume.
     *
     * @param clip the clip
     */
    public void play(Clip clip) {
        sounds.get(clip).play(volume);
    }

    /**
     * Keeps the programming-timer warning loop matching how much time is left, called once a frame: starts
     * {@link Clip#WARNING_30} at 30 seconds, swaps to {@link Clip#WARNING_10} at 10 (never both at once), and stops once the
     * timer is not counting down for this screen any more or reaches zero. Repeated calls within the same band do nothing,
     * so the loop is never restarted mid-loop. Each band only starts if the turn's total programming time is actually
     * longer than the band's own threshold ({@code totalSeconds > 30} for the 30 s warning, {@code totalSeconds > 10} for
     * the 10 s one) — otherwise a short turn (the host can set as little as
     * {@link de.mkoehler.robotrampage.net.NetworkConstants#MIN_PROGRAMMING_SECONDS}) would start a warning siren on its
     * very first frame and run it for most or all of the turn, which is not a "running low" warning any more.
     *
     * @param active       whether the programming timer is counting down right now
     * @param secondsLeft  the seconds left, rounded up; ignored unless {@code active}
     * @param totalSeconds the turn's total programming time, in seconds
     */
    public void updateCountdownWarning(boolean active, int secondsLeft, int totalSeconds) {
        Clip wanted = null;
        if (active && secondsLeft > 0) {
            if (secondsLeft <= 10 && totalSeconds > 10) {
                wanted = Clip.WARNING_10;
            } else if (secondsLeft <= 30 && totalSeconds > 30) {
                wanted = Clip.WARNING_30;
            }
        }
        if (wanted == countdownClip) {
            return;
        }
        stopCountdownWarning();
        if (wanted != null) {
            countdownClip = wanted;
            countdownId = sounds.get(wanted).loop(volume);
        }
    }

    /**
     * Stops the countdown warning loop if one is playing, so a screen that started it can never leave it running after it
     * is gone.
     */
    public void stopCountdownWarning() {
        if (countdownClip != null) {
            sounds.get(countdownClip).stop(countdownId);
            countdownClip = null;
            countdownId = -1;
        }
    }

    /**
     * Releases every loaded clip.
     */
    @Override
    public void dispose() {
        stopCountdownWarning();
        sounds.values().forEach(Sound::dispose);
    }
}
