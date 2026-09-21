package de.mkoehler.robotrampage.client.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import de.mkoehler.robotrampage.client.RobotRampageGame;
import de.mkoehler.robotrampage.client.connect.ConnectFlow;
import de.mkoehler.robotrampage.client.connect.ConnectionAttempt;
import de.mkoehler.robotrampage.client.connect.DisplayNames;
import de.mkoehler.robotrampage.client.connect.ServerAddress;
import de.mkoehler.robotrampage.client.ui.ModalDialog;
import de.mkoehler.robotrampage.client.ui.Theme;
import de.mkoehler.robotrampage.net.AppVersion;
import de.mkoehler.robotrampage.net.NetworkClient;
import de.mkoehler.robotrampage.net.NetworkConstants;

/**
 * Where the player says which server to join and under which name, and where the attempt to join is shown. Trying to join
 * opens a dialog that stays until the server has answered; the answer either leads on to the next screen or is explained
 * in another dialog.
 * <p>
 * The attempt itself runs in a {@link ConnectionAttempt}, which opens the connection on a background thread. This screen
 * only reads its state each frame.
 *
 * @author Mario Koehler
 */
public final class ConnectScreen extends StageScreen {

    private static final String ADDRESS_HINT = "The host and port of the server you want to join.";
    private static final String NAME_HINT = "Up to " + NetworkConstants.MAX_DISPLAY_NAME_LENGTH
        + " characters. Letters from any language are fine.";
    private static final float FIELD_HEIGHT = 56f;
    private static final float DIALOG_WIDTH = 700f;

    private final TextField addressField;
    private final TextField nameField;
    private final Label addressHint;
    private final Label nameHint;
    private ConnectionAttempt attempt;
    private ConnectFlow.Phase reported = ConnectFlow.Phase.IDLE;
    private ModalDialog dialog;

    /**
     * Builds the screen with the address and name from the last run.
     *
     * @param game the game showing the screen
     */
    public ConnectScreen(RobotRampageGame game) {
        super(game);
        addressField = ui.textField(game.settings().serverAddress(), 255);
        nameField = ui.textField(game.settings().displayName(), NetworkConstants.MAX_DISPLAY_NAME_LENGTH);
        addressHint = ui.label(ADDRESS_HINT, Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        nameHint = ui.label(NAME_HINT, Theme.TextStyle.CAPTION, Theme.INK_MUTED);
        watchForEditing(addressField, this::clearAddressError);
        watchForEditing(nameField, this::clearNameError);

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().padTop(200f);
        root.add(introduction()).width(640f).padLeft(160f).padRight(140f).top();
        root.add(serverPanel()).width(820f).top();
        stage.addActor(root);
        stage.setKeyboardFocus(addressField);
    }

    /**
     * Builds the title and the two lines of explanation on the left.
     *
     * @return the table
     */
    private Table introduction() {
        Label lead = ui.label("Join a Robot Rampage server to race other players across the factory floor.",
            Theme.TextStyle.LEAD, Theme.INK_MUTED);
        lead.setWrap(true);
        Label note = ui.label("Your display name is shown to everyone in the game. Both sides must run the same version "
            + "of the game.", Theme.TextStyle.BODY_LARGE, Theme.INK_MUTED);
        note.setWrap(true);

        Table table = new Table();
        table.top().left();
        table.add(ui.label("Connect", Theme.TextStyle.TITLE, Theme.INK)).left().row();
        table.add(lead).growX().padTop(Theme.SPACE_6).row();
        table.add(note).growX().padTop(Theme.SPACE_6);
        return table;
    }

    /**
     * Builds the panel with the two fields and the buttons.
     *
     * @return the panel
     */
    private Table serverPanel() {
        TextButton back = ui.button("Back", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton connect = ui.button("Connect", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new StartupScreen(game));
            }
        });
        connect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tryToConnect();
            }
        });
        Table buttons = new Table();
        buttons.right();
        buttons.add(back).size(180f, 52f);
        buttons.add(connect).size(260f, 52f).padLeft(Theme.SPACE_4);

        Table panel = ui.panel();
        panel.pad(40f).top().left();
        panel.add(ui.label("Server", Theme.TextStyle.SUBTITLE, Theme.INK)).left().row();
        panel.add(fieldGroup("Server address", addressField, addressHint)).growX().padTop(28f).row();
        panel.add(fieldGroup("Display name", nameField, nameHint)).growX().padTop(28f).row();
        panel.add(buttons).growX().padTop(28f).row();
        panel.add(ui.label("Client v" + AppVersion.getVersion(), Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left()
            .padTop(28f);
        return panel;
    }

    /**
     * Stacks a caption, a field and a hint the way the mockup does.
     *
     * @param caption the small heading above the field
     * @param field   the field
     * @param hint    the line below the field
     * @return the table
     */
    private Table fieldGroup(String caption, TextField field, Label hint) {
        hint.setWrap(true);
        Table group = new Table();
        group.left();
        group.add(ui.label(caption, Theme.TextStyle.LABEL, Theme.INK)).left().row();
        group.add(field).growX().height(FIELD_HEIGHT).padTop(Theme.SPACE_2).row();
        group.add(hint).growX().padTop(Theme.SPACE_2);
        return group;
    }

    /**
     * Sets what happens when a field is edited or the player presses Enter in it.
     *
     * @param field         the field
     * @param onEdit        what to do when the text changed
     */
    private void watchForEditing(TextField field, Runnable onEdit) {
        field.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onEdit.run();
            }
        });
        field.setTextFieldListener((typed, character) -> {
            if (character == '\r' || character == '\n') {
                tryToConnect();
            }
        });
    }

    /**
     * Takes the address field back to its normal look and hint.
     */
    private void clearAddressError() {
        ui.setFieldError(addressField, false);
        ui.setText(addressHint, Theme.TextStyle.CAPTION, ADDRESS_HINT);
        addressHint.getStyle().fontColor = Theme.INK_MUTED;
    }

    /**
     * Takes the name field back to its normal look and hint.
     */
    private void clearNameError() {
        ui.setFieldError(nameField, false);
        ui.setText(nameHint, Theme.TextStyle.CAPTION, NAME_HINT);
        nameHint.getStyle().fontColor = Theme.INK_MUTED;
    }

    /**
     * Checks the fields. If both are fine, remembers them and starts joining; otherwise marks what is wrong.
     */
    private void tryToConnect() {
        if (attempt != null) {
            return;
        }
        ServerAddress address = null;
        String addressProblem = null;
        try {
            address = ServerAddress.parse(addressField.getText());
        } catch (IllegalArgumentException e) {
            addressProblem = e.getMessage();
        }
        String nameProblem = DisplayNames.problem(nameField.getText());
        if (addressProblem != null) {
            showError(addressField, addressHint, addressProblem);
        }
        if (nameProblem != null) {
            showError(nameField, nameHint, nameProblem);
        }
        if (addressProblem != null || nameProblem != null) {
            return;
        }
        String name = DisplayNames.clean(nameField.getText());
        game.saveSettings(game.settings().withServerAddress(address.toString()).withDisplayName(name));
        join(address, name);
    }

    /**
     * Marks a field as wrong and puts the reason in its hint line.
     *
     * @param field   the field
     * @param hint    its hint label
     * @param message the reason
     */
    private void showError(TextField field, Label hint, String message) {
        ui.setFieldError(field, true);
        hint.setText(message);
        hint.getStyle().fontColor = Theme.DANGER;
    }

    /**
     * Starts joining a server and shows the dialog that waits for the answer.
     *
     * @param address the server
     * @param name    the display name, already checked
     */
    private void join(ServerAddress address, String name) {
        attempt = new ConnectionAttempt(new NetworkClient(), address, name, null, AppVersion.getVersion());
        reported = ConnectFlow.Phase.IDLE;
        attempt.start();
        showConnectingDialog(address);
    }

    /**
     * Reads the attempt and reacts when its state changed.
     *
     * @param delta seconds since the previous frame, unused
     */
    @Override
    protected void update(float delta) {
        if (attempt == null) {
            return;
        }
        attempt.update();
        ConnectFlow flow = attempt.flow();
        if (flow.phase() == reported) {
            return;
        }
        reported = flow.phase();
        switch (reported) {
            case ACCEPTED -> {
                ConnectionAttempt finished = attempt;
                attempt = null;
                game.setScreen(new ConnectedScreen(game, finished.connected(), finished.address()));
            }
            case UNREACHABLE -> showUnreachableDialog(attempt.address(), flow.detail());
            case VERSION_MISMATCH -> showVersionDialog(flow.serverVersion());
            case REFUSED -> showRefusedDialog(flow.detail());
            default -> {
            }
        }
    }

    /**
     * Shows the dialog that waits while the attempt is under way.
     *
     * @param address the server being contacted
     */
    private void showConnectingDialog(ServerAddress address) {
        Group bar = new Group();
        Image track = new Image(ui.solid(Theme.LINE));
        track.setBounds(0f, 10f, 512f, 4f);
        Image mover = new Image(ui.solid(Theme.PRIMARY));
        mover.setBounds(0f, 0f, 64f, 24f);
        mover.addAction(Actions.forever(Actions.sequence(
            Actions.moveTo(448f, 0f, 0.9f, Interpolation.sine),
            Actions.moveTo(0f, 0f, 0.9f, Interpolation.sine))));
        bar.addActor(track);
        bar.addActor(mover);
        bar.setSize(512f, 24f);

        TextButton cancel = ui.button("Cancel", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        cancel.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                cancelAttempt();
            }
        });
        ModalDialog connecting = new ModalDialog(ui, 640f, false)
            .title("Connecting")
            .row(bar)
            .text("Contacting " + address + " …", Theme.TextStyle.BODY_LARGE, Theme.INK)
            .text("This usually takes a second or two.", Theme.TextStyle.BODY, Theme.INK_MUTED)
            .buttons(200f, cancel)
            .onEscape(this::cancelAttempt);
        open(connecting);
    }

    /**
     * Shows why the server could not be reached, with the choice to change the address or to try again.
     *
     * @param address the server that was tried
     * @param reason  what went wrong
     */
    private void showUnreachableDialog(ServerAddress address, String reason) {
        ModalDialog unreachable = new ModalDialog(ui, DIALOG_WIDTH, true);
        Table well = ui.well();
        well.pad(14f);
        Label why = ui.label(reason, Theme.TextStyle.BODY, Theme.INK);
        why.setWrap(true);
        well.add(why).width(unreachable.contentWidth() - 28f);

        TextButton edit = ui.button("Edit address", Theme.ButtonKind.GHOST, Theme.TextStyle.BUTTON);
        TextButton retry = ui.button("Try again", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        edit.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dismiss();
            }
        });
        retry.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeDialog();
                join(address, DisplayNames.clean(nameField.getText()));
            }
        });
        unreachable.title("Can't reach the server")
            .text(address + " did not answer. Check the address, and make sure the server is running.",
                Theme.TextStyle.BODY_LARGE, Theme.INK)
            .row(well)
            .buttons(240f, edit, retry)
            .onEscape(this::dismiss);
        open(unreachable);
    }

    /**
     * Shows the versions of both sides when the server refused this game for its version.
     *
     * @param serverVersion the version the server runs
     */
    private void showVersionDialog(String serverVersion) {
        ModalDialog mismatch = new ModalDialog(ui, DIALOG_WIDTH, true);
        Table versions = new Table();
        versions.add(versionWell("Your game", AppVersion.getVersion(), Theme.INK)).growX().uniformX();
        versions.add(versionWell("This server", serverVersion, Theme.DANGER)).growX().uniformX()
            .padLeft(Theme.SPACE_4);

        TextButton back = ui.button("Back", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dismiss();
            }
        });
        mismatch.title("Different versions")
            .text("Your game and this server have to run the same version, or the rules could disagree.",
                Theme.TextStyle.BODY_LARGE, Theme.INK)
            .row(versions)
            .text("Update the game, or pick a server that runs v" + AppVersion.getVersion() + ".",
                Theme.TextStyle.BODY_LARGE, Theme.INK_MUTED)
            .buttons(200f, back)
            .onEscape(this::dismiss);
        open(mismatch);
    }

    /**
     * Shows the server's own reason when it refused the player for something other than the version, for example because
     * the game is full or already under way.
     *
     * @param reason the server's message
     */
    private void showRefusedDialog(String reason) {
        TextButton back = ui.button("Back", Theme.ButtonKind.PRIMARY, Theme.TextStyle.BUTTON);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                dismiss();
            }
        });
        String text = reason == null || reason.isBlank() ? "The server did not let you in." : reason;
        ModalDialog refused = new ModalDialog(ui, DIALOG_WIDTH, true)
            .title("Couldn't join")
            .text(text, Theme.TextStyle.BODY_LARGE, Theme.INK)
            .buttons(200f, back)
            .onEscape(this::dismiss);
        open(refused);
    }

    /**
     * Builds one of the two boxes that show a version.
     *
     * @param caption whose version it is
     * @param version the version
     * @param color   the color of the version
     * @return the box
     */
    private Table versionWell(String caption, String version, Color color) {
        Table well = ui.well();
        well.pad(Theme.SPACE_4).left();
        well.add(ui.label(caption, Theme.TextStyle.CAPTION, Theme.INK_MUTED)).left().row();
        well.add(ui.label("v" + version, Theme.TextStyle.HEADING, color)).left().padTop(Theme.SPACE_1);
        return well;
    }

    /**
     * Gives up the attempt that is under way and closes the dialog.
     */
    private void cancelAttempt() {
        if (attempt != null) {
            attempt.cancel();
            attempt = null;
        }
        dismiss();
    }

    /**
     * Closes the open dialog and puts the cursor back in the address field, ready to edit.
     */
    private void dismiss() {
        closeDialog();
        stage.setKeyboardFocus(addressField);
    }

    /**
     * Shows a dialog in place of the one that is open.
     *
     * @param next the dialog to show
     */
    private void open(ModalDialog next) {
        closeDialog();
        dialog = next;
        dialog.show(stage);
    }

    /**
     * Closes the open dialog and lets the player edit again after a failed attempt.
     */
    private void closeDialog() {
        if (dialog != null) {
            dialog.hide();
            dialog = null;
        }
        if (attempt != null && attempt.flow().isFinished()) {
            attempt = null;
        }
    }

    /**
     * Gives up an attempt that is still under way, then releases the stage.
     */
    @Override
    public void dispose() {
        if (attempt != null) {
            attempt.cancel();
            attempt = null;
        }
        super.dispose();
    }
}
