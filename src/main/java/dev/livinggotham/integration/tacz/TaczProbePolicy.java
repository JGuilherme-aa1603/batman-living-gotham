package dev.livinggotham.integration.tacz;

import net.minecraft.world.phys.Vec3;

/** Pure calculations used by runtime probes and later adapter tests. */
public final class TaczProbePolicy {
    public static final float DEFAULT_INACCURACY = 5.0F;

    private TaczProbePolicy() {
    }

    public static float adjustedDamage(float baseDamage, float multiplier) {
        return Math.max(0.0F, baseDamage * multiplier);
    }

    public static double angularDeviationDegrees(Vec3 expected, Vec3 actual) {
        if (expected.lengthSqr() == 0.0D || actual.lengthSqr() == 0.0D) {
            return Double.NaN;
        }
        double dot = expected.normalize().dot(actual.normalize());
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
    }
}
