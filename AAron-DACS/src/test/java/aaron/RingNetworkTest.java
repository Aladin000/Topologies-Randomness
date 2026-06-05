package aaron;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RingNetworkTest {

    @Test
    void createReturnsCorrectNodeCount() {
        RingNetwork network = RingNetwork.create(10, 2);
        assertEquals(10, network.nodeCount());
    }

    @Test
    void createReturnsCorrectK() {
        RingNetwork network = RingNetwork.create(10, 2);
        assertEquals(2, network.k());
    }

    @Test
    void eachNodeHasCorrectDegree() {
        RingNetwork network = RingNetwork.create(10, 2);
        for (int i = 0; i < network.nodeCount(); i++) {
            assertEquals(4, network.neighbors(i).size(), "Node " + i + " should have degree 4");
        }
    }

    @Test
    void neighborsAreSymmetric() {
        RingNetwork network = RingNetwork.create(10, 2);
        for (int i = 0; i < network.nodeCount(); i++) {
            for (int neighbor : network.neighbors(i)) {
                assertTrue(network.neighbors(neighbor).contains(i),
                    "If " + neighbor + " is neighbor of " + i + ", then " + i + " should be neighbor of " + neighbor);
            }
        }
    }

    @Test
    void noSelfLoops() {
        RingNetwork network = RingNetwork.create(10, 2);
        for (int i = 0; i < network.nodeCount(); i++) {
            assertFalse(network.neighbors(i).contains(i), "Node " + i + " should not be its own neighbor");
        }
    }

    @Test
    void neighborsAreWithinKDistance() {
        int n = 10;
        int k = 2;
        RingNetwork network = RingNetwork.create(n, k);
        
        for (int i = 0; i < n; i++) {
            for (int neighbor : network.neighbors(i)) {
                int distance = Math.min(Math.abs(neighbor - i), n - Math.abs(neighbor - i));
                assertTrue(distance <= k, 
                    "Node " + i + " should only be connected to nodes within distance " + k);
            }
        }
    }

    @Test
    void smallNetworkHasExpectedStructure() {
        // 6 nodes, k=1: each node connected to immediate neighbors
        RingNetwork network = RingNetwork.create(6, 1);
        
        assertEquals(Set.of(5, 1), network.neighbors(0));
        assertEquals(Set.of(0, 2), network.neighbors(1));
        assertEquals(Set.of(1, 3), network.neighbors(2));
        assertEquals(Set.of(2, 4), network.neighbors(3));
        assertEquals(Set.of(3, 5), network.neighbors(4));
        assertEquals(Set.of(4, 0), network.neighbors(5));
    }

    @Test
    void invalidNodeThrowsException() {
        RingNetwork network = RingNetwork.create(10, 2);
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(-1));
        assertThrows(IllegalArgumentException.class, () -> network.neighbors(10));
    }

    @Test
    void tooFewNodesThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RingNetwork.create(2, 1));
    }

    @Test
    void kTooLargeThrowsException() {
        // For 10 nodes, k=5 would mean 2k=10 which is >= nodeCount
        assertThrows(IllegalArgumentException.class, () -> RingNetwork.create(10, 5));
    }

    @Test
    void kZeroThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RingNetwork.create(10, 0));
    }

    @Test
    void createIsDeterministic() {
        // Ring is deterministic: same parameters always produce same network
        RingNetwork network1 = RingNetwork.create(50, 3);
        RingNetwork network2 = RingNetwork.create(50, 3);

        assertEquals(network1.edgeCount(), network2.edgeCount());
        for (int i = 0; i < network1.nodeCount(); i++) {
            assertEquals(network1.neighbors(i), network2.neighbors(i));
        }
    }

    @Test
    void edgeCountIsCorrect() {
        // Ring with n nodes and k neighbors per side has n * k edges
        int n = 20;
        int k = 3;
        RingNetwork network = RingNetwork.create(n, k);
        assertEquals(n * k, network.edgeCount());
    }
}

