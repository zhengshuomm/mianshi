import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

public class SlidingWindowRateLimiterTest {
    private SlidingWindowRateLimiter limiter;
    
    @BeforeEach
    void setUp() {
        limiter = new SlidingWindowRateLimiter(5, 1000);
    }
    
    @Test
    void testBasicLimiting() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest());
        }
        assertFalse(limiter.allowRequest());
    }
    
    @Test
    void testSlidingWindow() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowRequest());
        }
        Thread.sleep(1100);
        assertTrue(limiter.allowRequest());
    }
    
    @Test
    void testGradualExpiry() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            limiter.allowRequest();
            Thread.sleep(100);
        }
        assertFalse(limiter.allowRequest());
        Thread.sleep(1100);
        assertTrue(limiter.allowRequest());
    }
}
