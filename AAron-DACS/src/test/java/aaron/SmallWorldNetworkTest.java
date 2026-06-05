package aaron;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SmallWorldNetworkTest {

    // --- Basic construction ---

    @Test
    void createsNetworkWithCorrectNodeCount() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        assertEquals(50, net.nodeCount());
    }

    @Test
    void createsNetworkWithCorrectEdgeCount_noBeta() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.0, 42L);
        assertEquals(50 * 3, net.edgeCount(), "Pure ring with k=3: N*k edges");
    }

    @Test
    void rewiredNetworkPreservesEdgeCount() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.5, 42L);
        assertEquals(50 * 3, net.edgeCount(), "Rewiring preserves total edges");
    }

    @Test
    void averageDegreeIsApproximatelyTwoK() {
        SmallWorldNetwork net = SmallWorldNetwork.create(100, 3, 0.1, 42L);
        assertEquals(6.0, net.averageDegree(), 0.01, "Average degree = 2k = 6");
    }

    // --- Ring lattice (beta=0) ---

    @Test
    void betaZero_producesExactRing() {
        int n = 10;
        int k = 2;
        SmallWorldNetwork net = SmallWorldNetwork.create(n, k, 0.0, 42L);

        for (int i = 0; i < n; i++) {
            Set<Integer> nbrs = net.neighbors(i);
            assertEquals(2 * k, nbrs.size(), "Each node should have 2k neighbors");
            for (int offset = 1; offset <= k; offset++) {
                assertTrue(nbrs.contains(Math.floorMod(i + offset, n)));
                assertTrue(nbrs.contains(Math.floorMod(i - offset, n)));
            }
        }
    }

    // --- Connectivity ---

    @Test
    void networkIsConnected_betaZero() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.0, 42L);
        assertTrue(NetworkValidator.isConnected(net));
    }

    @Test
    void networkIsConnected_betaLow() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        assertTrue(NetworkValidator.isConnected(net));
    }

    @Test
    void networkIsConnected_betaHigh() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.5, 42L);
        assertTrue(NetworkValidator.isConnected(net));
    }

    // --- Determinism ---

    @Test
    void sameSeedProducesSameNetwork() {
        SmallWorldNetwork net1 = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        SmallWorldNetwork net2 = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        assertEquals(net1.edges(), net2.edges());
    }

    @Test
    void differentSeedProducesDifferentNetwork() {
        SmallWorldNetwork net1 = SmallWorldNetwork.create(50, 3, 0.3, 42L);
        SmallWorldNetwork net2 = SmallWorldNetwork.create(50, 3, 0.3, 99L);
        assertNotEquals(net1.edges(), net2.edges());
    }

    // --- Rewiring effect ---

    @Test
    void betaOneRewiresMostEdges() {
        SmallWorldNetwork ring = SmallWorldNetwork.create(100, 3, 0.0, 42L);
        SmallWorldNetwork rewired = SmallWorldNetwork.create(100, 3, 1.0, 42L);

        Set<Edge> ringEdges = ring.edges();
        Set<Edge> rewiredEdges = rewired.edges();

        int shared = 0;
        for (Edge e : rewiredEdges) {
            if (ringEdges.contains(e)) shared++;
        }
        assertTrue(shared < ringEdges.size() * 0.5,
            "beta=1.0 should rewire most edges, but " + shared + "/" + ringEdges.size() + " remained");

        assertEquals(100 * 3, rewired.edgeCount(),
            "beta=1.0 must preserve total edge count (rewiring replaces edges 1:1)");
    }

    // --- Accessors ---

    @Test
    void kAccessorReturnsConstructionValue() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 4, 0.1, 42L);
        assertEquals(4, net.k());
    }

    @Test
    void betaAccessorReturnsConstructionValue() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.15, 42L);
        assertEquals(0.15, net.beta(), 1e-10);
    }

    // --- Validation ---

    @Test
    void invalidNodeCount_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SmallWorldNetwork.create(2, 1, 0.1, 42L));
    }

    @Test
    void invalidK_zero_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SmallWorldNetwork.create(50, 0, 0.1, 42L));
    }

    @Test
    void kTooLargeForNodeCount_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SmallWorldNetwork.create(10, 5, 0.1, 42L));
    }

    @Test
    void invalidBeta_negative_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SmallWorldNetwork.create(50, 3, -0.1, 42L));
    }

    @Test
    void invalidBeta_greaterThanOne_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> SmallWorldNetwork.create(50, 3, 1.1, 42L));
    }

    @Test
    void neighborOutOfRange_throws() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        assertThrows(IllegalArgumentException.class, () -> net.neighbors(-1));
        assertThrows(IllegalArgumentException.class, () -> net.neighbors(50));
    }

    // --- Edge symmetry ---

    @Test
    void edgesAreSymmetric() {
        SmallWorldNetwork net = SmallWorldNetwork.create(50, 3, 0.3, 42L);
        for (int i = 0; i < net.nodeCount(); i++) {
            for (int neighbor : net.neighbors(i)) {
                assertTrue(net.neighbors(neighbor).contains(i),
                    "Edge symmetry violated: " + i + " -> " + neighbor);
            }
        }
    }

    // --- Varying k ---

    @Test
    void differentKProducesDifferentDegree() {
        SmallWorldNetwork k3 = SmallWorldNetwork.create(50, 3, 0.0, 42L);
        SmallWorldNetwork k6 = SmallWorldNetwork.create(50, 6, 0.0, 42L);

        assertEquals(6.0, k3.averageDegree(), 0.01);
        assertEquals(12.0, k6.averageDegree(), 0.01);
    }
}
