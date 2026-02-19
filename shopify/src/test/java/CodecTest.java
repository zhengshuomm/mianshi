import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.HashSet;

public class CodecTest {
    
    @Test
    void testBasicEncodeDecode() {
        Codec codec = new Codec();
        String longUrl = "https://leetcode.com/problems/design-tinyurl";
        
        String shortUrl = codec.encode(longUrl);
        String decodedUrl = codec.decode(shortUrl);
        
        assertEquals(longUrl, decodedUrl);
        assertTrue(shortUrl.startsWith("http://tinyurl.com/"));
    }
    
    @Test
    void testMultipleEncodings() {
        Codec codec = new Codec();
        String url1 = "https://www.example.com/page1";
        String url2 = "https://www.example.com/page2";
        String url3 = "https://www.example.com/page3";
        
        String short1 = codec.encode(url1);
        String short2 = codec.encode(url2);
        String short3 = codec.encode(url3);
        
        // All should be different
        assertNotEquals(short1, short2);
        assertNotEquals(short2, short3);
        assertNotEquals(short1, short3);
        
        // All should decode correctly
        assertEquals(url1, codec.decode(short1));
        assertEquals(url2, codec.decode(short2));
        assertEquals(url3, codec.decode(short3));
    }
    
    @Test
    void testSameUrlEncodedTwice() {
        Codec codec = new Codec();
        String longUrl = "https://leetcode.com/problems/design-tinyurl";
        
        String shortUrl1 = codec.encode(longUrl);
        String shortUrl2 = codec.encode(longUrl);
        
        // Should return the same short URL
        assertEquals(shortUrl1, shortUrl2);
        assertEquals(longUrl, codec.decode(shortUrl1));
    }
    
    @Test
    void testDecodeNonExistentUrl() {
        Codec codec = new Codec();
        String nonExistentShortUrl = "http://tinyurl.com/nonexistent";
        
        String result = codec.decode(nonExistentShortUrl);
        
        // Should return empty string for non-existent URLs
        assertEquals("", result);
    }
    
    @Test
    void testRoundTrip() {
        Codec codec = new Codec();
        String[] urls = {
            "https://leetcode.com/problems/design-tinyurl",
            "https://www.google.com",
            "https://github.com",
            "https://stackoverflow.com/questions/12345",
            "https://example.com/very/long/path/to/resource?param1=value1&param2=value2"
        };
        
        for (String url : urls) {
            String encoded = codec.encode(url);
            String decoded = codec.decode(encoded);
            assertEquals(url, decoded, "Round trip failed for: " + url);
        }
    }
    
    @Test
    void testShortUrlFormat() {
        Codec codec = new Codec();
        String longUrl = "https://leetcode.com/problems/design-tinyurl";
        
        String shortUrl = codec.encode(longUrl);
        
        // Should start with the base URL
        assertTrue(shortUrl.startsWith("http://tinyurl.com/"));
        
        // Should have a code after the base URL
        String code = shortUrl.substring("http://tinyurl.com/".length());
        assertFalse(code.isEmpty());
    }
    
    @Test
    void testManyUrls() {
        Codec codec = new Codec();
        int count = 100;
        String[] shortUrls = new String[count];
        
        // Encode many URLs
        for (int i = 0; i < count; i++) {
            String longUrl = "https://example.com/page" + i;
            shortUrls[i] = codec.encode(longUrl);
        }
        
        // Verify all are unique
        Set<String> uniqueUrls = new HashSet<>();
        for (String shortUrl : shortUrls) {
            assertTrue(uniqueUrls.add(shortUrl), "Duplicate short URL found");
        }
        
        // Verify all decode correctly
        for (int i = 0; i < count; i++) {
            String expected = "https://example.com/page" + i;
            String actual = codec.decode(shortUrls[i]);
            assertEquals(expected, actual);
        }
    }
    
    @Test
    void testVeryLongUrl() {
        Codec codec = new Codec();
        StringBuilder sb = new StringBuilder("https://example.com/");
        for (int i = 0; i < 1000; i++) {
            sb.append("verylongpath/");
        }
        String veryLongUrl = sb.toString();
        
        String shortUrl = codec.encode(veryLongUrl);
        String decodedUrl = codec.decode(shortUrl);
        
        assertEquals(veryLongUrl, decodedUrl);
        assertTrue(shortUrl.length() < veryLongUrl.length());
    }
}
