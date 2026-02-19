import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class LRUCacheTest {
    @Test
    void testLRUCache() {
        LRUCache lRUCache = new LRUCache(2);
        lRUCache.put(1, 1); // cache is {1=1}
        lRUCache.put(2, 2); // cache is {1=1, 2=2}
        assertEquals(1, lRUCache.get(1)); // return 1
        lRUCache.put(3, 3); // LRU key was 2, evicts key 2, cache is {1=1, 3=3}
        assertEquals(-1, lRUCache.get(2)); // returns -1 (not found)
        lRUCache.put(4, 4); // LRU key was 1, evicts key 1, cache is {4=4, 3=3}
        assertEquals(-1, lRUCache.get(1)); // return -1 (not found)
        assertEquals(3, lRUCache.get(3)); // return 3
        assertEquals(4, lRUCache.get(4)); // return 4
    }

    @Test
    void testUpdateExistingKey() {
        LRUCache cache = new LRUCache(2);
        cache.put(1, 1);
        cache.put(1, 10);
        assertEquals(10, cache.get(1));
    }

    @Test
    void testCapacityOne() {
        LRUCache cache = new LRUCache(1);
        cache.put(1, 1);
        cache.put(2, 2);
        assertEquals(-1, cache.get(1));
        assertEquals(2, cache.get(2));
    }
}
