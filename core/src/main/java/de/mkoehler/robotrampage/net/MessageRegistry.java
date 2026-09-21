package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LaserSource;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.SubPhase;

/**
 * Registers every class sent over the wire with a {@link Kryo} instance, in a
 * fixed order.
 * <p>
 * Kryo assigns each registered class a numeric id based on registration order
 * and relies on that id, rather than the class name, to identify types on the
 * wire. Both ends of a connection must therefore register the exact same
 * classes in the exact same order, or messages will be misread on the
 * receiving end. This class is the single shared place that order is defined,
 * to be used by both the server and the client.
 * <p>
 * The order is append-only: new classes are added at the end of
 * {@link #register(Kryo)}, existing lines are never reordered or removed.
 *
 * @author Mario Koehler
 */
public final class MessageRegistry {

    /**
     * Not instantiable; this class only exposes a static registration method.
     */
    private MessageRegistry() {
    }

    /**
     * Registers all wire message classes with the given {@link Kryo} instance.
     *
     * @param kryo the Kryo instance to register classes with, typically obtained
     *             from an {@code EndPoint}'s {@code getKryo()} method
     */
    public static void register(Kryo kryo) {
        kryo.register(HandshakeRequest.class);
        kryo.register(HandshakeResponse.class);
        // Building blocks of the per-turn event log (design.md 3.4/3.5). Every GameEvent
        // record must be registered here as it is added, since events travel inside
        // LoggedEvent through a field typed as the GameEvent interface.
        kryo.register(Position.class);
        kryo.register(Direction.class);
        kryo.register(CardType.class);
        kryo.register(Card.class);
        kryo.register(MoveCause.class);
        kryo.register(RotationCause.class);
        kryo.register(DestructionCause.class);
        kryo.register(SubPhase.class);
        kryo.register(GameEvent.RobotMoved.class);
        kryo.register(GameEvent.RobotRotated.class);
        kryo.register(GameEvent.RobotDestroyed.class);
        kryo.register(LoggedEvent.class);
        kryo.register(LaserSource.class);
        kryo.register(GameEvent.LaserFired.class);
        kryo.register(GameEvent.RobotDamaged.class);
        kryo.register(GameEvent.RegisterRevealed.class);
        kryo.register(GameEvent.FlagTouched.class);
        kryo.register(GameEvent.ArchiveMarkerMoved.class);
        kryo.register(GameEvent.RobotRepaired.class);
        kryo.register(GameEvent.RobotPoweredDown.class);
        kryo.register(GameEvent.RobotPoweredUp.class);
        kryo.register(GameEvent.RobotRespawned.class);
        kryo.register(GameEvent.GameEnded.class);
    }
}
