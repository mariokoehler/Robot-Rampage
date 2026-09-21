package de.mkoehler.robotrampage.net;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This build's version, baked into every jar at build time via a
 * resource-filtered properties file &mdash; never a hand-maintained constant.
 * <p>
 * The client sends this version in its {@code HandshakeRequest} and the server
 * compares it against its own, so a client built from a different version than
 * the server it connects to is rejected up front rather than risking a
 * {@link MessageRegistry} mismatch further into the session.
 *
 * @author Mario Koehler
 */
public final class AppVersion {

    /**
     * Name of the classpath resource holding the Maven-filtered version.
     */
    private static final String RESOURCE_NAME = "robotrampage-version.properties";

    /**
     * The version, read once when this class is first loaded.
     */
    private static final String VERSION = loadVersion();

    /**
     * Not instantiable; this class only exposes static accessors.
     */
    private AppVersion() {
    }

    /**
     * Returns this build's version string, e.g. {@code "0.1.0-SNAPSHOT"}.
     *
     * @return the build version, never {@code null} or blank
     */
    public static String getVersion() {
        return VERSION;
    }

    /**
     * Reads the {@code version} property from {@value #RESOURCE_NAME}.
     *
     * @return the non-blank version string
     * @throws IllegalStateException if the resource is missing (for example
     *                               because the code was compiled without
     *                               Maven's resource filtering), unreadable, or
     *                               its {@code version} property is missing or
     *                               blank
     */
    private static String loadVersion() {
        try (InputStream stream = AppVersion.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (stream == null) {
                throw new IllegalStateException(
                    "Missing " + RESOURCE_NAME + " on the classpath - was this built with Maven (not just compiled), "
                        + "so resource filtering ran?");
            }
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank()) {
                throw new IllegalStateException("'version' property missing or blank in " + RESOURCE_NAME);
            }
            return version;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_NAME, e);
        }
    }
}
