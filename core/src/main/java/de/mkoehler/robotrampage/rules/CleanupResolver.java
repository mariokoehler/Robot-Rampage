package de.mkoehler.robotrampage.rules;

import de.mkoehler.robotrampage.board.SquareFeature;

/**
 * Resolves the cleanup phase at the end of a turn (design.md 2.3, 2.5, 2.8, 2.9), in
 * this order for every active robot:
 * <ol>
 *   <li><b>Repair sites.</b> A robot ending the turn on a repair site loses one damage
 *       and its archive marker moves there.</li>
 *   <li><b>Power-down.</b> A robot that announced a power-down shuts down, fully
 *       repaired. A robot that was already shut down comes back on, unless it announced
 *       another power-down.</li>
 *   <li><b>Discarding.</b> Every programmed card goes to the discard pile except the ones
 *       in registers that are still locked by damage, which stay put.</li>
 * </ol>
 * Discarding comes last so that registers unlocked by repairs release their cards.
 *
 * @author Mario Koehler
 */
final class CleanupResolver {

    private final GameState state;
    private final EventLog log;

    /**
     * Creates a resolver operating on a game state.
     *
     * @param state the game state to mutate
     * @param log   receives the events describing every change
     */
    CleanupResolver(GameState state, EventLog log) {
        this.state = state;
        this.log = log;
    }

    /**
     * Runs the whole cleanup phase.
     */
    void run() {
        log.enter(0, SubPhase.CLEANUP);
        for (Robot robot : state.robots()) {
            if (robot.isActive()) {
                repairOnRepairSite(robot);
            }
        }
        for (Robot robot : state.robots()) {
            if (robot.isActive()) {
                updatePowerState(robot);
            }
        }
        for (Robot robot : state.robots()) {
            if (robot.isActive()) {
                discardUnlockedCards(robot);
            }
        }
    }

    /**
     * Heals one damage and moves the archive marker if the robot stands on a repair site.
     *
     * @param robot the robot to check
     */
    private void repairOnRepairSite(Robot robot) {
        if (state.board().featureAt(robot.position()) != SquareFeature.REPAIR) {
            return;
        }
        if (robot.damage() > 0) {
            robot.setDamage(robot.damage() - 1);
            log.add(new GameEvent.RobotRepaired(robot.id(), 1, robot.damage()));
        }
        if (!robot.position().equals(robot.archiveMarker())) {
            robot.setArchiveMarker(robot.position());
            log.add(new GameEvent.ArchiveMarkerMoved(robot.id(), robot.position()));
        }
    }

    /**
     * Applies a power-down announcement or ends a finished one.
     *
     * @param robot the robot to update
     */
    private void updatePowerState(Robot robot) {
        if (robot.isPoweredDown()) {
            if (robot.isPowerDownAnnounced()) {
                robot.setPowerDownAnnounced(false);
            } else {
                robot.setPoweredDown(false);
                log.add(new GameEvent.RobotPoweredUp(robot.id()));
            }
        } else if (robot.isPowerDownAnnounced()) {
            robot.setPowerDownAnnounced(false);
            robot.setPoweredDown(true);
            log.add(new GameEvent.RobotPoweredDown(robot.id()));
            if (robot.damage() > 0) {
                log.add(new GameEvent.RobotRepaired(robot.id(), robot.damage(), 0));
                robot.setDamage(0);
            }
        }
    }

    /**
     * Sends every card in an unlocked register to the discard pile.
     *
     * @param robot the robot whose registers to clear
     */
    private void discardUnlockedCards(Robot robot) {
        for (int index = 0; index < Robot.REGISTER_COUNT; index++) {
            Card card = robot.register(index);
            if (card != null && !robot.isRegisterLocked(index)) {
                state.deck().discard(card);
                robot.setRegister(index, null);
            }
        }
    }
}
