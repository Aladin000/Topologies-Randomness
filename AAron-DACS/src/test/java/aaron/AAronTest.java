package aaron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AAronTest {

    @Test
    void randomCreatesRandomNetwork() {
        Network network = AAron.random(100, 0.1, 42L);
        
        assertTrue(network instanceof RandomNetwork);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void ringCreatesRingNetwork() {
        Network network = AAron.ring(100, 3, 42L);
        
        assertTrue(network instanceof RingNetwork);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void scaleFreeCreatesScaleFreeNetwork() {
        Network network = AAron.scaleFree(100, 2, 42L);
        
        assertTrue(network instanceof ScaleFreeNetwork);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void randomNetworkIsReproducible() {
        Network n1 = AAron.random(50, 0.2, 12345L);
        Network n2 = AAron.random(50, 0.2, 12345L);
        
        for (int i = 0; i < n1.nodeCount(); i++) {
            assertEquals(n1.neighbors(i), n2.neighbors(i));
        }
    }

    @Test
    void scaleFreeNetworkIsReproducible() {
        Network n1 = AAron.scaleFree(50, 2, 12345L);
        Network n2 = AAron.scaleFree(50, 2, 12345L);
        
        for (int i = 0; i < n1.nodeCount(); i++) {
            assertEquals(n1.neighbors(i), n2.neighbors(i));
        }
    }

    @Test
    void ringNetworkIsReproducible() {
        Network n1 = AAron.ring(50, 3, 42L);
        Network n2 = AAron.ring(50, 3, 42L);

        assertEquals(n1.edgeCount(), n2.edgeCount());
        for (int i = 0; i < n1.nodeCount(); i++) {
            assertEquals(n1.neighbors(i), n2.neighbors(i));
        }
    }

    @Test
    void smallWorldCreatesSmallWorldNetwork() {
        Network network = AAron.smallWorld(100, 3, 0.1, 42L);

        assertTrue(network instanceof SmallWorldNetwork);
        assertEquals(100, network.nodeCount());
    }

    @Test
    void allFormsPassValidation() {
        Network random = AAron.random(50, 0.1, 42L);
        Network ring = AAron.ring(50, 2, 42L);
        Network scaleFree = AAron.scaleFree(50, 2, 42L);
        Network smallWorld = AAron.smallWorld(50, 2, 0.1, 42L);

        assertTrue(NetworkValidator.validate(random).valid());
        assertTrue(NetworkValidator.validate(ring).valid());
        assertTrue(NetworkValidator.validate(scaleFree).valid());
        assertTrue(NetworkValidator.validate(smallWorld).valid());
    }

    @Test
    void ringSeedIsIgnored() {
        Network n1 = AAron.ring(50, 3, 1L);
        Network n2 = AAron.ring(50, 3, 999L);

        assertEquals(n1.edgeCount(), n2.edgeCount());
        assertEquals(n1.edges(), n2.edges());
    }

    // Immutability tests

    @Test
    void randomNeighborsAreImmutable() {
        Network network = AAron.random(20, 0.3, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.neighbors(0).add(99));
    }

    @Test
    void ringNeighborsAreImmutable() {
        Network network = AAron.ring(20, 2, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.neighbors(0).add(99));
    }

    @Test
    void scaleFreeNeighborsAreImmutable() {
        Network network = AAron.scaleFree(20, 2, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.neighbors(0).add(99));
    }

    @Test
    void randomEdgesAreImmutable() {
        Network network = AAron.random(20, 0.3, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.edges().add(new Edge(0, 99)));
    }

    @Test
    void ringEdgesAreImmutable() {
        Network network = AAron.ring(20, 2, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.edges().add(new Edge(0, 99)));
    }

    @Test
    void scaleFreeEdgesAreImmutable() {
        Network network = AAron.scaleFree(20, 2, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.edges().add(new Edge(0, 99)));
    }

    @Test
    void smallWorldNeighborsAreImmutable() {
        Network network = AAron.smallWorld(20, 2, 0.1, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.neighbors(0).add(99));
    }

    @Test
    void smallWorldEdgesAreImmutable() {
        Network network = AAron.smallWorld(20, 2, 0.1, 42L);
        assertThrows(UnsupportedOperationException.class, () -> network.edges().add(new Edge(0, 99)));
    }

    @Test
    void smallWorldNetworkIsReproducible() {
        Network n1 = AAron.smallWorld(50, 3, 0.1, 12345L);
        Network n2 = AAron.smallWorld(50, 3, 0.1, 12345L);

        assertEquals(n1.edges(), n2.edges());
    }

    // Edge-level reproducibility tests

    @Test
    void randomNetworkEdgesAreReproducible() {
        Network n1 = AAron.random(50, 0.2, 12345L);
        Network n2 = AAron.random(50, 0.2, 12345L);

        assertEquals(n1.edges(), n2.edges());
    }

    @Test
    void scaleFreeNetworkEdgesAreReproducible() {
        Network n1 = AAron.scaleFree(50, 2, 12345L);
        Network n2 = AAron.scaleFree(50, 2, 12345L);

        assertEquals(n1.edges(), n2.edges());
    }

    @Test
    void ringNetworkEdgesAreReproducible() {
        Network n1 = AAron.ring(50, 3, 42L);
        Network n2 = AAron.ring(50, 3, 42L);

        assertEquals(n1.edges(), n2.edges());
    }
}

