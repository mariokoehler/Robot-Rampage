package de.mkoehler.robotrampage.client.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the names and pictures of the eight robots.
 *
 * @author Mario Koehler
 */
class RobotLookTest {

    /**
     * Every seat has a robot with the name the design gives it, and a seat outside the table has none.
     */
    @Test
    void everySeatHasANamedRobot() {
        assertEquals(8, RobotLook.COUNT);
        assertEquals("Bolt", RobotLook.name(0));
        assertEquals("Stack", RobotLook.name(7));
        assertThrows(IllegalArgumentException.class, () -> RobotLook.name(-1));
        assertThrows(IllegalArgumentException.class, () -> RobotLook.name(8));
        assertThrows(IllegalArgumentException.class, () -> RobotLook.picture(8));
    }

    /**
     * The picture paths follow the numbering of the asset files, which count seats from one.
     */
    @Test
    void picturePathsCountSeatsFromOne() {
        assertEquals("robots/robot-1-bolt.png", RobotLook.picture(0));
        assertEquals("robots/robot-8-stack.png", RobotLook.picture(7));
    }

    /**
     * Every picture the lookup names exists in the asset folder, so a renamed file cannot go unnoticed until a lobby is
     * opened.
     */
    @Test
    void everyPictureExistsInTheAssets() {
        for (int seat = 0; seat < RobotLook.COUNT; seat++) {
            assertNotNull(getClass().getClassLoader().getResource(RobotLook.picture(seat)),
                "missing " + RobotLook.picture(seat));
        }
    }
}
