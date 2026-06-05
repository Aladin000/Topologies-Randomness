package aaron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScaleFreeNetworkTest {

    @Test
    void createReturnsCorrectNodeCount() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(100, 3, 42L);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void createReturnsCorrectM() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(100, 3, 42L);
        assertEquals(3, network.m());
    }

    @Test
    void neighborsAreSymmetric() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        for (int i = 0; i < network.nodeCount(); i++) {
            for (int neighbor : network.neighbors(i)) {
                assertTrue(network.neighbors(neighbor).contains(i),
                    "If " + neighbor + " is neighbor of " + i + ", then " + i + " should be neighbor of " + neighbor);
            }
        }
    }

    @Test
    void noSelfLoops() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        for (int i = 0; i < network.nodeCount(); i++) {
            assertFalse(network.neighbors(i).contains(i), "Node " + i + " should not be its own neighbor");
        }
    }

    @Test
    void edgeCountMatchesAdjacencyLists() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        int sumDegrees = 0;
        for (int i = 0; i < network.nodeCount(); i++) {
            sumDegrees += network.neighbors(i).size();
        }
        assertEquals(2 * network.edgeCount(), sumDegrees);
    }

    @Test
    void edgeCountIsCorrect() {
        // Initial network: m+1 nodes fully connected = m*(m+1)/2 edges
        // Then (n - m - 1) nodes added, each adding m edges
        // Total: m*(m+1)/2 + (n-m-1)*m
        int n = 50;
        int m = 2;
        ScaleFreeNetwork network = ScaleFreeNetwork.create(n, m, 42L);
        
        int initialEdges = m * (m + 1) / 2;
        int addedEdges = (n - m - 1) * m;
        int expectedEdges = initialEdges + addedEdges;
        
        assertEquals(expectedEdges, network.edgeCount());
    }

    @Test
    void initialNodesAreFullyConnected() {
        // First m+1 nodes should form a complete graph
        int m = 3;
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, m, 42L);
        
        for (int i = 0; i <= m; i++) {
            for (int j = 0; j <= m; j++) {
                if (i != j) {
                    assertTrue(network.neighbors(i).contains(j),
                        "Initial nodes " + i + " and " + j + " should be connected");
                }
            }
        }
    }

    @Test
    void newNodesHaveAtLeastMNeighbors() {
        int m = 3;
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, m, 42L);
        
        // All nodes added after the initial network should have at least m neighbors
        for (int i = m + 1; i < network.nodeCount(); i++) {
            assertTrue(network.neighbors(i).size() >= m,
                "Node " + i + " should have at least " + m + " neighbors");
        }
    }

    @Test
    void sameSeedProducesSameNetwork() {
        ScaleFreeNetwork network1 = ScaleFreeNetwork.create(50, 2, 12345L);
        ScaleFreeNetwork network2 = ScaleFreeNetwork.create(50, 2, 12345L);
        
        assertEquals(network1.edgeCount(), network2.edgeCount());
        for (int i = 0; i < network1.nodeCount(); i++) {
            assertEquals(network1.neighbors(i), network2.neighbors(i));
        }
    }

    @Test
    void differentSeedsProduceDifferentNetworks() {
        ScaleFreeNetwork network1 = ScaleFreeNetwork.create(50, 2, 12345L);
        ScaleFreeNetwork network2 = ScaleFreeNetwork.create(50, 2, 54321L);
        
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
    void degreeDistributionIsHeavyTailed() {
        // In a scale-free network, we expect some nodes with much higher degree than average
        ScaleFreeNetwork network = ScaleFreeNetwork.create(500, 2, 42L);
        
        int maxDegree = 0;
        for (int i = 0; i < network.nodeCount(); i++) {
            maxDegree = Math.max(maxDegree, network.neighbors(i).size());
        }
        
        // Max degree should be significantly higher than average degree
        double avgDegree = network.averageDegree();
        assertTrue(maxDegree > 3 * avgDegree, 
            "Max degree (" + maxDegree + ") should be much higher than average (" + avgDegree + ")");
    }

    @Test
    void invalidNodeThrowsException() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(-1));
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(50));
    }

    @Test
    void mTooLargeThrowsException() {
        // nodeCount must be > m
        assertThrows(IllegalArgumentException.class, () -> ScaleFreeNetwork.create(5, 5, 42L));
        assertThrows(IllegalArgumentException.class, () -> ScaleFreeNetwork.create(5, 10, 42L));
    }

    @Test
    void mZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> ScaleFreeNetwork.create(10, 0, 42L));
    }
}

