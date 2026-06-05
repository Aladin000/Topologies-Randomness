package aaron;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkValidatorTest {

    @Test
    void validRingNetworkPassesValidation() {
        RingNetwork network = RingNetwork.create(20, 2);
        var result = NetworkValidator.validate(network);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validRandomNetworkPassesValidation() {
        RandomNetwork network = RandomNetwork.create(50, 0.1, 42L);
        var result = NetworkValidator.validate(network);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validScaleFreeNetworkPassesValidation() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        var result = NetworkValidator.validate(network);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void statsAreCorrectForRingNetwork() {
        RingNetwork network = RingNetwork.create(10, 2);
        var result = NetworkValidator.validate(network);
        var stats = result.stats();
        
        assertEquals(10, stats.nodeCount());
        assertEquals(20, stats.edgeCount()); // 10 nodes * degree 4 / 2
        assertEquals(4, stats.minDegree());
        assertEquals(4, stats.maxDegree());
        assertEquals(4.0, stats.averageDegree(), 0.001);
        assertEquals(1, stats.componentCount()); // Ring is connected
    }

    @Test
    void statsAreCorrectForRandomNetwork() {
        RandomNetwork network = RandomNetwork.create(50, 0.1, 42L);
        var result = NetworkValidator.validate(network);
        var stats = result.stats();
        
        assertEquals(50, stats.nodeCount());
        assertEquals(network.edgeCount(), stats.edgeCount());
        assertEquals(network.averageDegree(), stats.averageDegree(), 0.001);
    }

    @Test
    void validatorEdgeCountMatchesNetworkForRing() {
        RingNetwork network = RingNetwork.create(30, 3);
        var stats = NetworkValidator.validate(network).stats();

        assertEquals(network.edgeCount(), stats.edgeCount());
    }

    @Test
    void validatorEdgeCountMatchesNetworkForScaleFree() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(50, 2, 42L);
        var stats = NetworkValidator.validate(network).stats();

        assertEquals(network.edgeCount(), stats.edgeCount());
    }

    @Test
    void completeGraphHasOneComponent() {
        RandomNetwork network = RandomNetwork.create(10, 1.0, 42L);
        var result = NetworkValidator.validate(network);
        
        assertEquals(1, result.stats().componentCount());
    }

    @Test
    void emptyEdgesGraphHasNComponents() {
        RandomNetwork network = RandomNetwork.create(10, 0.0, 42L);
        var result = NetworkValidator.validate(network);
        
        assertEquals(10, result.stats().componentCount());
    }

    @Test
    void ringValidationChecksUniformDegree() {
        RingNetwork network = RingNetwork.create(20, 3);
        var result = NetworkValidator.validateRing(network, 3);
        
        assertTrue(result.valid());
        assertEquals(6, result.stats().minDegree());
        assertEquals(6, result.stats().maxDegree());
    }

    @Test
    void ringValidationFailsWithWrongK() {
        RingNetwork network = RingNetwork.create(20, 3);
        var result = NetworkValidator.validateRing(network, 2);
        
        // Should fail because degrees don't match expected k=2
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void scaleFreeNetworkHasHubs() {
        ScaleFreeNetwork network = ScaleFreeNetwork.create(200, 2, 42L);
        var result = NetworkValidator.validate(network);
        var stats = result.stats();
        
        // Max degree should be significantly higher than min degree
        assertTrue(stats.maxDegree() > stats.minDegree() * 2,
            "Scale-free network should have hubs with much higher degree");
    }

    // Connectivity enforcement tests

    @Test
    void isConnectedReturnsTrueForConnectedNetwork() {
        RingNetwork network = RingNetwork.create(20, 2);
        assertTrue(NetworkValidator.isConnected(network));
    }

    @Test
    void isConnectedReturnsFalseForDisconnectedNetwork() {
        // Zero edge probability creates isolated nodes
        RandomNetwork network = RandomNetwork.create(10, 0.0, 42L);
        assertFalse(NetworkValidator.isConnected(network));
    }

    @Test
    void validateForSimulationPassesForConnectedNetwork() {
        RingNetwork network = RingNetwork.create(20, 2);
        var result = NetworkValidator.validateForSimulation(network);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validateForSimulationFailsForDisconnectedNetwork() {
        // Zero edge probability creates isolated nodes
        RandomNetwork network = RandomNetwork.create(10, 0.0, 42L);
        var result = NetworkValidator.validateForSimulation(network);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("not connected")));
    }

    @Test
    void requireValidForSimulationSucceedsForConnectedNetwork() {
        RingNetwork network = RingNetwork.create(20, 2);
        // Should not throw
        assertDoesNotThrow(() -> NetworkValidator.requireValidForSimulation(network));
    }

    @Test
    void requireValidForSimulationThrowsForDisconnectedNetwork() {
        // Zero edge probability creates isolated nodes
        RandomNetwork network = RandomNetwork.create(10, 0.0, 42L);
        
        var exception = assertThrows(IllegalArgumentException.class,
            () -> NetworkValidator.requireValidForSimulation(network));
        
        assertTrue(exception.getMessage().contains("not connected") ||
                   exception.getMessage().contains("not valid"));
    }

    @Test
    void scaleFreeNetworkIsAlwaysConnected() {
        // Barabasi-Albert generates connected networks by construction
        ScaleFreeNetwork network = ScaleFreeNetwork.create(100, 2, 42L);
        assertTrue(NetworkValidator.isConnected(network));
    }

    @Test
    void ringNetworkIsAlwaysConnected() {
        // Ring networks are connected by construction
        RingNetwork network = RingNetwork.create(50, 1);
        assertTrue(NetworkValidator.isConnected(network));
    }

    @Test
    void denseRandomNetworkIsLikelyConnected() {
        // High edge probability typically produces connected networks
        RandomNetwork network = RandomNetwork.create(50, 0.3, 42L);
        assertTrue(NetworkValidator.isConnected(network));
    }
}

