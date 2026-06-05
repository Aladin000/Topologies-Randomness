package aaron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EdgeTest {

    @Test
    void edgeNormalizesOrder() {
        // When from > to, the edge should swap them
        Edge edge = new Edge(5, 2);
        assertEquals(2, edge.from());
        assertEquals(5, edge.to());
    }

    @Test
    void edgePreservesOrderWhenCorrect() {
        Edge edge = new Edge(2, 5);
        assertEquals(2, edge.from());
        assertEquals(5, edge.to());
    }

    @Test
    void edgeHandlesEqualNodes() {
        // Edge record can represent equal endpoints; self-loops are prevented by generators and validators
        Edge edge = new Edge(3, 3);
        assertEquals(3, edge.from());
        assertEquals(3, edge.to());
    }

    @Test
    void edgesWithSameNodesAreEqual() {
        Edge edge1 = new Edge(2, 5);
        Edge edge2 = new Edge(5, 2);
        assertEquals(edge1, edge2);
    }
}

