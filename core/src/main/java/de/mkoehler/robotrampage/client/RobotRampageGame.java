package de.mkoehler.robotrampage.client;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;

/**
 * The client's {@link com.badlogic.gdx.ApplicationListener}. Delegates
 * rendering to whichever {@link com.badlogic.gdx.Screen} is current and starts
 * on the {@link StartupScreen} placeholder until real screens exist.
 *
 * @author Mario Koehler
 */
public class RobotRampageGame extends Game {

    /**
     * Shows the initial screen once the libGDX backend is ready.
     */
    @Override
    public void create() {
        setScreen(new StartupScreen());
    }

    /**
     * Disposes the current screen along with the application. {@link Game#dispose()}
     * only calls {@code hide()} on the current screen, so a screen owning GPU
     * resources would otherwise leak them on exit.
     */
    @Override
    public void dispose() {
        Screen current = getScreen();
        super.dispose();
        if (current != null) {
            current.dispose();
        }
    }
}
