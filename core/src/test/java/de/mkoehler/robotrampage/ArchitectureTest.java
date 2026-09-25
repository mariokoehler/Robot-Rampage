package de.mkoehler.robotrampage;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the package layering from design.md 3.3: the rules engine and the board
 * model are pure Java and must not depend on libGDX, the network layer or the
 * client, so they stay testable without a window and reusable by bots, replay tools
 * and board generators.
 * <p>
 * Each rule fails if the packages it covers contain no classes, so a rename or move
 * cannot make a rule pass vacuously. The rules inspect compiled bytecode, so one blind
 * spot remains: a compile-time constant from a forbidden class (for example
 * {@code MathUtils.PI}) is inlined by the compiler and leaves no dependency behind.
 * Any real class reference, such as constructing a {@code Vector2}, is caught.
 *
 * @author Mario Koehler
 */
class ArchitectureTest {

    private static JavaClasses productionClasses;

    /**
     * Imports the production classes of this module once for all rules.
     */
    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.mkoehler.robotrampage");
    }

    /**
     * {@code rules} and {@code board} may only depend on the JDK and on each other,
     * never on libGDX, {@code net} or {@code client}.
     */
    @Test
    void rulesAndBoardStayFreeOfLibGdxNetworkAndClient() {
        noClasses().that().resideInAnyPackage("de.mkoehler.robotrampage.rules..", "de.mkoehler.robotrampage.board..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.badlogic..", "de.mkoehler.robotrampage.net..", "de.mkoehler.robotrampage.client..")
            .check(productionClasses);
    }

    /**
     * {@code bot} plays by the rules and nothing else: no libGDX, no network, no client, and no session, which is where the
     * other players' hidden programs live.
     */
    @Test
    void botsOnlyKnowTheRules() {
        noClasses().that().resideInAPackage("de.mkoehler.robotrampage.bot..")
            .should().dependOnClassesThat().resideInAnyPackage("com.badlogic..", "de.mkoehler.robotrampage.net..",
                "de.mkoehler.robotrampage.client..", "de.mkoehler.robotrampage.session..")
            .check(productionClasses);
    }

    /**
     * {@code board} sits below {@code rules} in the layering and must not depend on it.
     */
    @Test
    void boardDoesNotDependOnRules() {
        noClasses().that().resideInAPackage("de.mkoehler.robotrampage.board..")
            .should().dependOnClassesThat().resideInAPackage("de.mkoehler.robotrampage.rules..")
            .check(productionClasses);
    }

    /**
     * The session state machine is protocol- and transport-agnostic: it may use the message classes and the rules, but
     * never libGDX, the client or KryoNet itself, which is what keeps it testable with a fake clock and no sockets.
     */
    @Test
    void sessionStaysFreeOfTransportAndClient() {
        noClasses().that().resideInAPackage("de.mkoehler.robotrampage.session..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.badlogic..", "com.esotericsoftware.kryonet..", "de.mkoehler.robotrampage.client..")
            .check(productionClasses);
    }

    /**
     * The rules and the board must not depend on the session that drives them.
     */
    @Test
    void rulesAndBoardDoNotKnowTheSession() {
        noClasses().that().resideInAnyPackage("de.mkoehler.robotrampage.rules..", "de.mkoehler.robotrampage.board..")
            .should().dependOnClassesThat().resideInAPackage("de.mkoehler.robotrampage.session..")
            .check(productionClasses);
    }

    /**
     * The network layer must not depend on the client, so the server can use it
     * without pulling in screens.
     */
    @Test
    void netDoesNotDependOnClient() {
        noClasses().that().resideInAPackage("de.mkoehler.robotrampage.net..")
            .should().dependOnClassesThat().resideInAPackage("de.mkoehler.robotrampage.client..")
            .check(productionClasses);
    }

    /**
     * The client's connect flow, settings, lobby view, board geometry, game model and turn replay are plain Java: no libGDX and no screens, which is what lets them be tested
     * without a window and driven by any screen. They may use the network layer, which they exist to drive.
     */
    @Test
    void clientLogicStaysFreeOfLibGdxAndScreens() {
        noClasses().that().resideInAnyPackage("de.mkoehler.robotrampage.client.connect..",
                "de.mkoehler.robotrampage.client.settings..", "de.mkoehler.robotrampage.client.lobby..", "de.mkoehler.robotrampage.client.board..", "de.mkoehler.robotrampage.client.game..", "de.mkoehler.robotrampage.client.replay..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.badlogic..", "de.mkoehler.robotrampage.client.screen..", "de.mkoehler.robotrampage.client.ui..",
                "de.mkoehler.robotrampage.client.render..")
            .check(productionClasses);
    }
}
