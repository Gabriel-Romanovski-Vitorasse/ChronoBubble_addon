package com.gabriel.chronobubble;

import net.minecraft.util.math.vector.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link Precognition#predict}: posição futura de um projétil com o arrasto (0.99) e a gravidade (0.04) do jogo. */
class PrecognitionTest {
    private static final double EPS = 1.0E-9;

    @Test
    void zeroTicksKeepsPosition() {
        Vector3d p = Precognition.predict(new Vector3d(1, 2, 3), new Vector3d(4, 5, 6), false, 0);
        assertEquals(new Vector3d(1, 2, 3), p);
    }

    @Test
    void stillProjectileWithoutGravityDoesNotMove() {
        Vector3d p = Precognition.predict(new Vector3d(1, 2, 3), Vector3d.ZERO, true, 50);
        assertEquals(new Vector3d(1, 2, 3), p);
    }

    @Test
    void dragSlowsTheProjectile() {
        // 2 ticks sem gravidade: x = 0 + 1 + 0.99
        Vector3d p = Precognition.predict(Vector3d.ZERO, new Vector3d(1, 0, 0), true, 2);
        assertEquals(1.99, p.x, EPS);
        assertEquals(0.0, p.y, EPS);
    }

    @Test
    void gravityPullsDown() {
        // 3 ticks parado e com gravidade: y = 0 + 0 + (-0.04) + (-0.04 * 0.99 - 0.04)
        Vector3d p = Precognition.predict(Vector3d.ZERO, Vector3d.ZERO, false, 3);
        assertEquals(-0.04 - (0.04 * 0.99 + 0.04), p.y, EPS);
        assertEquals(0.0, p.x, EPS);
    }
}
