package com.gabriel.chronobubble;

import net.minecraft.util.math.vector.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link StasisField#crossing}: em que fração do próximo passo um projétil toca a esfera da zona. */
class StasisFieldTest {
    private static final double EPS = 1.0E-9;

    @Test
    void alreadyInsideReturnsZero() {
        assertEquals(0.0, StasisField.crossing(new Vector3d(1, 0, 0), new Vector3d(5, 0, 0), 0, 0, 0, 2), EPS);
    }

    @Test
    void crossesSurfaceDuringTheStep() {
        // sai de x = 10 e anda 10 blocos rumo ao centro: toca a esfera de raio 2 em x = 2, ou seja, em 80% do passo
        double t = StasisField.crossing(new Vector3d(10, 0, 0), new Vector3d(-10, 0, 0), 0, 0, 0, 2);
        assertEquals(0.8, t, EPS);
    }

    @Test
    void stopsShortOfTheSphere() {
        // anda só 5 blocos: ainda não chega na superfície neste passo
        assertEquals(-1.0, StasisField.crossing(new Vector3d(10, 0, 0), new Vector3d(-5, 0, 0), 0, 0, 0, 2), EPS);
    }

    @Test
    void movingAwayNeverCrosses() {
        assertEquals(-1.0, StasisField.crossing(new Vector3d(10, 0, 0), new Vector3d(10, 0, 0), 0, 0, 0, 2), EPS);
    }

    @Test
    void passingBesideTheSphereNeverCrosses() {
        assertEquals(-1.0, StasisField.crossing(new Vector3d(10, 5, 0), new Vector3d(-20, 0, 0), 0, 0, 0, 2), EPS);
    }

    @Test
    void stoppedProjectileOutsideNeverCrosses() {
        assertEquals(-1.0, StasisField.crossing(new Vector3d(10, 0, 0), Vector3d.ZERO, 0, 0, 0, 2), EPS);
    }

    @Test
    void sphereCenterIsRespected() {
        // mesma conta do teste de 80%, com a esfera deslocada para (100, 64, -30)
        double t = StasisField.crossing(new Vector3d(110, 64, -30), new Vector3d(-10, 0, 0), 100, 64, -30, 2);
        assertEquals(0.8, t, EPS);
    }
}
