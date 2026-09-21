package de.mkoehler.robotrampage.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies {@link AppVersion} reads a real, non-blank version out of the
 * resource-filtered {@code robotrampage-version.properties} &mdash; not an exact
 * value, since that changes with every release.
 *
 * @author Mario Koehler
 */
class AppVersionTest {

    /**
     * The version must be present, non-blank and must not be the unfiltered
     * Maven placeholder, which would mean resource filtering did not run.
     */
    @Test
    void versionIsANonBlankStringReadFromTheFilteredResource() {
        String version = AppVersion.getVersion();
        assertNotNull(version);
        assertFalse(version.isBlank());
        assertFalse(version.contains("${"), "resource filtering did not substitute the version");
    }

    /**
     * Repeated calls must return the same value.
     */
    @Test
    void versionIsStableAcrossRepeatedCalls() {
        assertEquals(AppVersion.getVersion(), AppVersion.getVersion());
    }
}
