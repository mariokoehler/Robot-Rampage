package de.mkoehler.robotrampage.net;

import com.esotericsoftware.kryo.Kryo;
import de.mkoehler.robotrampage.board.Direction;
import de.mkoehler.robotrampage.board.Position;
import de.mkoehler.robotrampage.net.messages.AddBot;
import de.mkoehler.robotrampage.net.messages.BoardChoice;
import de.mkoehler.robotrampage.net.messages.ChooseRespawnFacing;
import de.mkoehler.robotrampage.net.messages.GameOver;
import de.mkoehler.robotrampage.net.messages.GameStarted;
import de.mkoehler.robotrampage.net.messages.HandDealt;
import de.mkoehler.robotrampage.net.messages.HandshakeRequest;
import de.mkoehler.robotrampage.net.messages.HandshakeResponse;
import de.mkoehler.robotrampage.net.messages.LobbyState;
import de.mkoehler.robotrampage.net.messages.PlayerConfirmed;
import de.mkoehler.robotrampage.net.messages.PlayerConnection;
import de.mkoehler.robotrampage.net.messages.PlayerInfo;
import de.mkoehler.robotrampage.net.messages.PlayerLeft;
import de.mkoehler.robotrampage.net.messages.ProgramRevealed;
import de.mkoehler.robotrampage.net.messages.RemoveBot;
import de.mkoehler.robotrampage.net.messages.ReplayFinished;
import de.mkoehler.robotrampage.net.messages.RequestRejected;
import de.mkoehler.robotrampage.net.messages.RespawnFacingChosen;
import de.mkoehler.robotrampage.net.messages.ReturnToLobby;
import de.mkoehler.robotrampage.net.messages.RobotState;
import de.mkoehler.robotrampage.net.messages.SelectBoard;
import de.mkoehler.robotrampage.net.messages.SetProgrammingSeconds;
import de.mkoehler.robotrampage.net.messages.SetReady;
import de.mkoehler.robotrampage.net.messages.SetTimerPaused;
import de.mkoehler.robotrampage.net.messages.StartGameRequest;
import de.mkoehler.robotrampage.net.messages.StateSnapshot;
import de.mkoehler.robotrampage.net.messages.SubmitProgram;
import de.mkoehler.robotrampage.net.messages.TimerPaused;
import de.mkoehler.robotrampage.net.messages.TimerUpdate;
import de.mkoehler.robotrampage.net.messages.TurnResolved;
import de.mkoehler.robotrampage.net.messages.TurnStarted;
import de.mkoehler.robotrampage.rules.Card;
import de.mkoehler.robotrampage.rules.CardType;
import de.mkoehler.robotrampage.rules.DestructionCause;
import de.mkoehler.robotrampage.rules.GameEvent;
import de.mkoehler.robotrampage.rules.LaserSource;
import de.mkoehler.robotrampage.rules.LoggedEvent;
import de.mkoehler.robotrampage.rules.MoveCause;
import de.mkoehler.robotrampage.rules.RobotStatus;
import de.mkoehler.robotrampage.rules.RotationCause;
import de.mkoehler.robotrampage.rules.SubPhase;

import java.util.ArrayList;

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

    private static final String PROTOCOL_PACKAGE_PREFIX = "de.mkoehler.robotrampage.";

    /**
     * Not instantiable; this class only exposes static methods.
     */
    private MessageRegistry() {
    }

    /**
     * Tells this game's own messages apart from KryoNet's housekeeping objects (keep-alive frames and the like), which
     * the network wrappers must never hand on to the game.
     *
     * @param object what KryoNet delivered
     * @return {@code true} if it is an instance of one of this game's classes
     */
    public static boolean isProtocolMessage(Object object) {
        return object != null && object.getClass().getName().startsWith(PROTOCOL_PACKAGE_PREFIX);
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
        // Session protocol (design.md 3.5). Messages hold their lists as ArrayList, which is registered here.
        kryo.register(ArrayList.class);
        kryo.register(RobotStatus.class);
        kryo.register(PlayerInfo.class);
        kryo.register(RobotState.class);
        kryo.register(LobbyState.class);
        kryo.register(SetReady.class);
        kryo.register(StartGameRequest.class);
        kryo.register(GameStarted.class);
        kryo.register(TurnStarted.class);
        kryo.register(HandDealt.class);
        kryo.register(SubmitProgram.class);
        kryo.register(RequestRejected.class);
        kryo.register(PlayerConfirmed.class);
        kryo.register(TimerUpdate.class);
        kryo.register(TurnResolved.class);
        kryo.register(StateSnapshot.class);
        kryo.register(PlayerConnection.class);
        kryo.register(PlayerLeft.class);
        kryo.register(GameOver.class);
        kryo.register(SetTimerPaused.class);
        kryo.register(TimerPaused.class);
        kryo.register(ProgramRevealed.class);
        kryo.register(RespawnFacingChosen.class);
        kryo.register(ChooseRespawnFacing.class);
        kryo.register(ReturnToLobby.class);
        kryo.register(SetProgrammingSeconds.class);
        kryo.register(BoardChoice.class);
        kryo.register(SelectBoard.class);
        kryo.register(AddBot.class);
        kryo.register(RemoveBot.class);
        kryo.register(ReplayFinished.class);
    }
}
