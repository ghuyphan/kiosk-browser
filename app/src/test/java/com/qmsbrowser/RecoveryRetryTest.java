package com.qmsbrowser;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class RecoveryRetryTest {
    @Test
    public void testExponentialBackoffMath() {
        int initialDelay = 2000;
        int maxDelay = 60000;
        
        int delay = initialDelay;
        assertEquals(2000, delay);
        
        // 1st backoff step
        delay = Math.min(delay * 2, maxDelay);
        assertEquals(4000, delay);
        
        // 2nd backoff step
        delay = Math.min(delay * 2, maxDelay);
        assertEquals(8000, delay);
        
        // Exponential growth up to the cap
        for (int i = 0; i < 10; i++) {
            delay = Math.min(delay * 2, maxDelay);
        }
        assertEquals(60000, delay);
    }
}
