package aaron;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the edges() method across all network types.
 */
class NetworkEdgesTest {

    @Test
    void ringNetworkEdgesMatchEdgeCount() {
        RingNetwork network = RingNetwork.create(10, 2);
        Set<Edge> edges = network.edges();
        
        assertEquals(network.edgeCount(), edges.size());
    }

    @Test
    void randomNetworkEdgesMatchEdgeCount() {
        RandomNetwork network = RandomNetwork.create(50, 0.1, 42L);
        Set<Edge> edges = network.edges();
        
        assertEquals(network.edgeCount(), edges.size());
    }

    @Test
    void scaleFreeNetworkEdgesMatchEdgeCount() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        Set<Edge> edges = network.edges();
        
        assertEquals(network.edgeCount(), edges.size());
    }

    @Test
    void smallWorldNetworkEdgesMatchEdgeCount() {
        SmallWorldNetwork network = SmallWorldNetwork.create(50, 3, 0.1, 42L);
        Set<Edge> edges = network.edges();

        assertEquals(network.edgeCount(), edges.size());

        for (Edge edge : edges) {
            assertTrue(edge.from() <= edge.to(),
                "Edge should be normalized: from <= to");
            assertTrue(network.neighbors(edge.from()).contains(edge.to()),
                "Edge should be reflected in neighbors");
            assertTrue(network.neighbors(edge.to()).contains(edge.from()),
                "Edge should be symmetric in neighbors");
        }
    }

    @Test
    void edgesAreNormalized() {
        RandomNetwork network = RandomNetwork.create(20, 0.3, 42L);
        
        for (Edge edge : network.edges()) {
            assertTrue(edge.from() <= edge.to(), 
                "Edge should be normalized: from <= to");
        }
    }

    @Test
    void edgesMatchNeighborRelationships() {
        RandomNetwork network = RandomNetwork.create(20, 0.2, 42L);
        Set<Edge> edges = network.edges();
        
        // Every edge should correspond to a neighbor relationship
        for (Edge edge : edges) {
            assertTrue(network.neighbors(edge.from()).contains(edge.to()),
                "Edge " + edge + " should be reflected in neighbors");
            assertTrue(network.neighbors(edge.to()).contains(edge.from()),
                "Edge " + edge + " should be symmetric in neighbors");
        }
        
        // Every neighbor relationship should be in edges
        for (int i = 0; i < network.nodeCount(); i++) {
            for (int neighbor : network.neighbors(i)) {
                Edge expected = new Edge(i, neighbor);
                assertTrue(edges.contains(expected),
                    "Neighbor relationship " + i + "-" + neighbor + " should be in edges");
            }
        }
    }

    @Test
    void completeGraphHasExpectedEdges() {
        RandomNetwork network = RandomNetwork.create(5, 1.0, 42L);
        Set<Edge> edges = network.edges();
        
        // Complete graph with 5 nodes has 5*4/2 = 10 edges
        assertEquals(10, edges.size());
    }

    @Test
    void emptyGraphHasNoEdges() {
        RandomNetwork network = RandomNetwork.create(10, 0.0, 42L);
        Set<Edge> edges = network.edges();
        
        assertTrue(edges.isEmpty());
    }
}

