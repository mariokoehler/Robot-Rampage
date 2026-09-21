package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.lobby.RobotLook;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.AppVersion;

/**
 * The first screen: the game's name and tagline, the way in (Play), to the settings and out (Quit), and the eight robots
 * standing on a conveyor belt. The settings screen does not exist yet, so its button is shown disabled.
 *
 * @author Mario Koehler
 */
public final class StartupScreen extends StageScreen {

    private static final float BAND_HEIGHT = 98f;
    private static final float BELT_TILE = 96f;
    private static final float ROBOT_SIZE = 128f;
    private static final float ROBOT_LEFT = 86f;
    private static final float ROBOT_SPACING = 230f;
    private static final float ROBOT_TOP = 760f;
    private static final float ROBOT_STAGGER = 10f;
    private static final String CREDIT = "A fan project. RoboRally is a board game by Richard Garfield, published by "
        + "Wizards of the Coast / Avalon Hill and Renegade Game Studios.";

    /**
     * Builds the screen.
     *
     * @param game the game showing the screen
     */
    public StartupScreen(RobotRampageGame game) {
        super(game);
        addBelt();
        stage.addActor(floorBand());
        addRobots();
        stage.addActor(titleBlock());
    }

    /**
     * Builds the title, the tagline and the three buttons.
     *
     * @return the table, filling the stage
     */
    private Table titleBlock() {
        TextButton play = ui.button("Play", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON_LARGE);
        TextButton settings = ui.button("Settings", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON_MEDIUM);
        TextButton quit = ui.button("Quit", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON_MEDIUM);
        settings.setDisabled(true);
        play.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new ConnectScreen(game));
            }
        });
        quit.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Gdx.app.exit();
            }
        });

        Table buttons = new Table();
        buttons.add(play).size(260f, 68f);
        buttons.add(settings).size(220f, 68f).padLeft(Theme.SPACE_6);
        buttons.add(quit).size(160f, 68f).padLeft(Theme.SPACE_6);

        Table table = new Table();
        table.setFillParent(true);
        table.top().padTop(230f);
        table.add(ui.label("Robot Rampage", Theme.TextStyle.HERO, Theme.INK)).row();
        table.add(ui.label("Plan carefully. Crash spectacularly.", Theme.TextStyle.LEAD,
            Theme.INK_MUTED)).padTop(40f).row();
        table.add(buttons).padTop(40f);
        return table;
    }

    /**
     * Lays the conveyor belt along the bottom of the screen, above the strip with the version.
     */
    private void addBelt() {
        for (int i = 0; i < Theme.VIEW_WIDTH / BELT_TILE; i++) {
            Image tile = new Image(ui.image("tiles/belt.png"));
            tile.setBounds(i * BELT_TILE, BAND_HEIGHT, BELT_TILE, BELT_TILE);
            stage.addActor(tile);
        }
    }

    /**
     * Stands the eight robots in a row on the belt, every other one a little higher.
     */
    private void addRobots() {
        for (int seat = 0; seat < RobotLook.COUNT; seat++) {
            Image robot = new Image(ui.image(RobotLook.picture(seat)));
            float top = seat % 2 == 0 ? ROBOT_TOP : ROBOT_TOP + ROBOT_STAGGER;
            robot.setBounds(ROBOT_LEFT + seat * ROBOT_SPACING, Theme.VIEW_HEIGHT - top - ROBOT_SIZE, ROBOT_SIZE, ROBOT_SIZE);
            stage.addActor(robot);
        }
    }

    /**
     * Builds the strip along the bottom edge with the version and the credit.
     *
     * @return the table, filling the stage
     */
    private Table floorBand() {
        Label credit = ui.label(CREDIT, Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        credit.setWrap(true);
        credit.setAlignment(Align.right);

        Table band = new Table();
        band.setFillParent(true);
        band.bottom();
        Table strip = new Table();
        strip.setBackground(ui.solid(Theme.FLOOR));
        strip.padLeft(48f).padRight(48f);
        strip.add(ui.label("v" + AppVersion.getVersion(), Theme.TextStyle.BODY, Theme.INK_MUTED)).left().expandX();
        strip.add(credit).width(900f).right();
        band.add(new Image(ui.solid(Theme.LINE))).growX().height(Theme.BORDER_HAIRLINE).row();
        band.add(strip).growX().height(BAND_HEIGHT - Theme.BORDER_HAIRLINE);
        return band;
    }
}
