package com.nonid.affinity.non.mixin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerifiedVersionTest {
    @Test void acceptsVerifiedReleaseAndMinecraftBuildMetadata() {
        assertTrue(NonIntegrationMixinPlugin.isVerifiedVersion("1.5.1"));
        assertTrue(NonIntegrationMixinPlugin.isVerifiedVersion("1.5.1+1.21.11"));
        assertTrue(NonIntegrationMixinPlugin.isVerifiedVersion("1.5.2"));
        assertTrue(NonIntegrationMixinPlugin.isVerifiedVersion("1.5.2+1.21.11"));
    }

    @Test void doesNotConfuseOtherPatchReleasesWithVerifiedRelease() {
        for (String version : new String[]{"1.5.10", "1.5.11+1.21.11", "1.5.100", "1.5.3", "1.5.20", "1.6.0"}) {
            assertFalse(NonIntegrationMixinPlugin.isVerifiedVersion(version), version);
        }
    }

    @Test void unverifiedPrereleasesAndMalformedVersionsAreNotApproved() {
        assertFalse(NonIntegrationMixinPlugin.isVerifiedVersion(null));
        for (String version : new String[]{"", "unknown", "1.5.1-beta", "1.5.1foo", "1.5.1.", "1.5.1+"}) {
            assertFalse(NonIntegrationMixinPlugin.isVerifiedVersion(version), version);
        }
    }
}
