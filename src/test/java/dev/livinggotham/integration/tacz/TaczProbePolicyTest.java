package dev.livinggotham.integration.tacz;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TaczProbePolicyTest {
    @Test
    void appliesPerShooterDamageMultiplierWithoutNegativeDamage() {
        assertEquals(8.0F, TaczProbePolicy.adjustedDamage(8.0F, 1.0F));
        assertEquals(4.0F, TaczProbePolicy.adjustedDamage(8.0F, 0.5F));
        assertEquals(0.0F, TaczProbePolicy.adjustedDamage(8.0F, -1.0F));
    }

    @Test
    void measuresAngularDeviationIndependentOfVelocityMagnitude() {
        assertEquals(0.0D, TaczProbePolicy.angularDeviationDegrees(
                new Vec3(0, 0, 1), new Vec3(0, 0, 5)), 0.000001D);
        assertEquals(90.0D, TaczProbePolicy.angularDeviationDegrees(
                new Vec3(0, 0, 1), new Vec3(1, 0, 0)), 0.000001D);
        assertTrue(Double.isNaN(TaczProbePolicy.angularDeviationDegrees(Vec3.ZERO, new Vec3(1, 0, 0))));
    }
}
