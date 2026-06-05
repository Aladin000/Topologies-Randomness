package peerselection;

import aaron.AAron;
import aaron.Network;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PeerSelectorTest {

    // --- View size computation ---

    @Test
    void viewSizeWithFullFraction() {
        assertEquals(5, PeerSelector.computeViewSize(5, 1.0));
    }

    @Test
    void viewSizeWithPartialFraction() {
        // round(0.6 * 5) = round(3.0) = 3
        assertEquals(3, PeerSelector.computeViewSize(5, 0.6));
    }

    @Test
    void viewSizeGuaranteesMinimumOne() {
        // round(0.1 * 2) = round(0.2) = 0, max(1, 0) = 1
        assertEquals(1, PeerSelector.computeViewSize(2, 0.1));
    }

    @Test
    void viewSizeRoundsHalfUp() {
        // round(0.5 * 5) = round(2.5) = 3 (Java Math.round rounds half up)
        assertEquals(3, PeerSelector.computeViewSize(5, 0.5));
    }

    @Test
    void viewSizeWithDegreeOne() {
        assertEquals(1, PeerSelector.computeViewSize(1, 0.3));
        assertEquals(1, PeerSelector.computeViewSize(1, 1.0));
    }

    @Test
    void viewSizeNeverExceedsDegree() {
        assertEquals(10, PeerSelector.computeViewSize(10, 1.0));
    }

    @Test
    void viewSizeExamples() {
        // From documentation example: degree 5, viewFraction 0.6 -> 3
        assertEquals(3, PeerSelector.computeViewSize(5, 0.6));
        // round(0.2 * 10) = round(2.0) = 2
        assertEquals(2, PeerSelector.computeViewSize(10, 0.2));
        // round(0.8 * 3) = round(2.4) = 2
        assertEquals(2, PeerSelector.computeViewSize(3, 0.8));
    }

    // --- fanOut clamping ---

    @Test
    void fanOutClampedToViewSize() {
        Set<Integer> neighbors = Set.of(1, 2);
        // degree=2, vf=0.6: viewSize = max(1, round(1.2)) = 1
        // fanOut=5: effectiveFanOut = min(5, 1) = 1
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.6, 5, new Random(42L));
        assertEquals(1, peers.size());
    }

    @Test
    void fanOutNotClampedWhenSmaller() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5);
        // degree=5, vf=1.0: viewSize=5, fanOut=2, effectiveFanOut=2
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 1.0, 2, new Random(42L));
        assertEquals(2, peers.size());
    }

    @Test
    void fanOutEqualsViewSizeSelectsEntireView() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5);
        // vf=0.6, degree=5: viewSize=3, fanOut=3, effectiveFanOut=3
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.6, 3, new Random(42L));
        assertEquals(3, peers.size());
        assertEquals(peers.size(), new HashSet<>(peers).size());
    }

    // --- Selection properties ---

    @Test
    void selectedPeersAreNeighbors() {
        Set<Integer> neighbors = Set.of(2, 7, 8, 12, 15);
        for (int seed = 0; seed < 20; seed++) {
            List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.6, 2, new Random(seed));
            for (int peer : peers) {
                assertTrue(neighbors.contains(peer),
                        "Selected peer " + peer + " must be a neighbor (seed=" + seed + ")");
            }
        }
    }

    @Test
    void selectedPeersHaveNoDuplicates() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        for (int seed = 0; seed < 20; seed++) {
            List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.8, 5, new Random(seed));
            assertEquals(peers.size(), new HashSet<>(peers).size(),
                    "No duplicates allowed (seed=" + seed + ")");
        }
    }

    @Test
    void returnsCorrectNumberOfPeers() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5);
        // viewSize = max(1, round(0.8 * 5)) = 4, effectiveFanOut = min(3, 4) = 3
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.8, 3, new Random(42L));
        assertEquals(3, peers.size());
    }

    // --- Determinism ---

    @Test
    void sameSeedProducesSameResult() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5, 6, 7, 8);

        List<Integer> peers1 = PeerSelector.selectPeers(neighbors, 0.6, 3, new Random(42L));
        List<Integer> peers2 = PeerSelector.selectPeers(neighbors, 0.6, 3, new Random(42L));

        assertEquals(peers1, peers2);
    }

    @Test
    void differentSeedsProduceDifferentResult() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        List<Integer> peers1 = PeerSelector.selectPeers(neighbors, 0.6, 3, new Random(42L));
        List<Integer> peers2 = PeerSelector.selectPeers(neighbors, 0.6, 3, new Random(99L));

        assertNotEquals(peers1, peers2);
    }

    // --- viewFraction = 1.0 ---

    @Test
    void fullViewFractionWithFullFanOutSelectsAllNeighbors() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5);
        for (int seed = 0; seed < 10; seed++) {
            List<Integer> peers = PeerSelector.selectPeers(neighbors, 1.0, 5, new Random(seed));
            assertEquals(5, peers.size());
            assertEquals(neighbors, new HashSet<>(peers));
        }
    }

    @Test
    void fullViewFractionOnlyPeerSelectionIsRandom() {
        Set<Integer> neighbors = Set.of(1, 2, 3, 4, 5);
        // vf=1.0, fanOut=2: view is always all 5 neighbors, only peer selection varies
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 1.0, 2, new Random(42L));
        assertEquals(2, peers.size());
        assertTrue(neighbors.containsAll(peers));
    }

    // --- Edge cases ---

    @Test
    void emptyNeighborsReturnsEmpty() {
        List<Integer> peers = PeerSelector.selectPeers(Set.of(), 0.5, 2, new Random(42L));
        assertTrue(peers.isEmpty());
    }

    @Test
    void singleNeighborAlwaysSelected() {
        Set<Integer> neighbors = Set.of(42);
        for (int seed = 0; seed < 10; seed++) {
            List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.5, 3, new Random(seed));
            assertEquals(List.of(42), peers);
        }
    }

    @Test
    void lowViewFractionOnLowDegreeNode() {
        // Degree 2, viewFraction 0.2: viewSize = max(1, round(0.4)) = max(1, 0) = 1
        Set<Integer> neighbors = Set.of(10, 20);
        List<Integer> peers = PeerSelector.selectPeers(neighbors, 0.2, 5, new Random(42L));
        assertEquals(1, peers.size());
        assertTrue(neighbors.contains(peers.get(0)));
    }

    // --- Network convenience method ---

    @Test
    void networkOverloadMatchesSetOverload() {
        Network network = AAron.ring(20, 3, 42L);
        int node = 5;

        List<Integer> fromNetwork = PeerSelector.selectPeers(network, node, 0.6, 2, new Random(42L));
        List<Integer> fromSet = PeerSelector.selectPeers(network.neighbors(node), 0.6, 2, new Random(42L));

        assertEquals(fromSet, fromNetwork);
    }

    @Test
    void networkOverloadPeersAreNeighbors() {
        Network network = AAron.scaleFree(50, 3, 42L);
        for (int node = 0; node < network.nodeCount(); node++) {
            List<Integer> peers = PeerSelector.selectPeers(network, node, 0.5, 2, new Random(node));
            for (int peer : peers) {
                assertTrue(network.neighbors(node).contains(peer),
                        "Peer " + peer + " must be neighbor of node " + node);
            }
        }
    }

    // --- Parameter validation ---

    @Test
    void viewFractionZeroThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.selectPeers(Set.of(1, 2), 0.0, 1, new Random(42L)));
    }

    @Test
    void viewFractionNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.selectPeers(Set.of(1, 2), -0.1, 1, new Random(42L)));
    }

    @Test
    void viewFractionAboveOneThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.selectPeers(Set.of(1, 2), 1.1, 1, new Random(42L)));
    }

    @Test
    void fanOutZeroThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.selectPeers(Set.of(1, 2), 0.5, 0, new Random(42L)));
    }

    @Test
    void fanOutNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.selectPeers(Set.of(1, 2), 0.5, -1, new Random(42L)));
    }

    @Test
    void computeViewSizeInvalidDegreeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.computeViewSize(0, 0.5));
    }

    @Test
    void computeViewSizeInvalidFractionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.computeViewSize(5, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> PeerSelector.computeViewSize(5, 1.1));
    }
}
