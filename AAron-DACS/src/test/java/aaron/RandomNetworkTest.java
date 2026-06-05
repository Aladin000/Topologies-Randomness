package aaron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RandomNetworkTest {

    @Test
    void createReturnsCorrectNodeCount() {
        RandomNetwork network = RandomNetwork.create(100, 0.1, 42L);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void createReturnsCorrectEdgeProbability() {
        RandomNetwork network = RandomNetwork.create(100, 0.1, 42L);
        assertEquals(0.1, network.edgeProbability(), 0.0001);
    }

    @Test
    void neighborsAreSymmetric() {
        RandomNetwork network = RandomNetwork.create(50, 0.2, 42L);
        for (int i = 0; i < network.nodeCount(); i++) {
            for (int neighbor : network.neighbors(i)) {
                assertTrue(network.neighbors(neighbor).contains(i),
                    "If " + neighbor + " is neighbor of " + i + ", then " + i + " should be neighbor of " + neighbor);
            }
        }
    }

    @Test
    void noSelfLoops() {
        RandomNetwork network = RandomNetwork.create(50, 0.3, 42L);
        for (int i = 0; i < network.nodeCount(); i++) {
            assertFalse(network.neighbors(i).contains(i), "Node " + i + " should not be its own neighbor");
        }
    }

    @Test
    void edgeCountMatchesAdjacencyLists() {
        RandomNetwork network = RandomNetwork.create(50, 0.2, 42L);
        int sumDegrees = 0;
        for (int i = 0; i < network.nodeCount(); i++) {
            sumDegrees += network.neighbors(i).size();
        }
        // Sum of degrees = 2 * edge count (each edge counted twice)
        assertEquals(2 * network.edgeCount(), sumDegrees);
    }

    @Test
    void averageDegreeIsCorrect() {
        RandomNetwork network = RandomNetwork.create(50, 0.2, 42L);
        double expected = (2.0 * network.edgeCount()) / network.nodeCount();
        assertEquals(expected, network.averageDegree(), 0.0001);
    }

    @Test
    void probabilityZeroGivesNoEdges() {
        RandomNetwork network = RandomNetwork.create(50, 0.0, 42L);
        assertEquals(0, network.edgeCount());
        for (int i = 0; i < network.nodeCount(); i++) {
            assertTrue(network.neighbors(i).isEmpty());
        }
    }

    @Test
    void probabilityOneGivesCompleteGraph() {
        RandomNetwork network = RandomNetwork.create(10, 1.0, 42L);
        // Complete graph has n*(n-1)/2 edges
        int expectedEdges = 10 * 9 / 2;
        assertEquals(expectedEdges, network.edgeCount());
        // Each node should have degree n-1
        for (int i = 0; i < network.nodeCount(); i++) {
            assertEquals(9, network.neighbors(i).size());
        }
    }

    @Test
    void sameSeedProducesSameNetwork() {
        RandomNetwork network1 = RandomNetwork.create(50, 0.2, 12345L);
        RandomNetwork network2 = RandomNetwork.create(50, 0.2, 12345L);
        
        assertEquals(network1.edgeCount(), network2.edgeCount());
        for (int i = 0; i < network1.nodeCount(); i++) {
            assertEquals(network1.neighbors(i), network2.neighbors(i));
        }
    }

    @Test
    void differentSeedsProduceDifferentNetworks() {
        RandomNetwork network1 = RandomNetwork.create(50, 0.2, 12345L);
        RandomNetwork network2 = RandomNetwork.create(50, 0.2, 54321L);
        
        // Very likely to have different edge counts
        // This is a statistical test, but with these parameters it should always pass
        boolean different = false;
        for (int i = 0; i < network1.nodeCount(); i++) {
            if (!network1.neighbors(i).equals(network2.neighbors(i))) {
                different = true;
                break;
            }
        }
        assertTrue(different, "Networks with different seeds should differ");
    }

    @Test
    void invalidNodeThrowsException() {
        RandomNetwork network = RandomNetwork.create(50, 0.2, 42L);
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(-1));
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(50));
    }

    @Test
    void tooFewNodesThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RandomNetwork.create(1, 0.5, 42L));
    }

    @Test
    void invalidProbabilityThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RandomNetwork.create(10, -0.1, 42L));
        assertThrows(IllegalArgumentException.class, () -> RandomNetwork.create(10, 1.1, 42L));
    }
}

