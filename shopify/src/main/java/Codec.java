import java.util.*;

/**
 * LeetCode 535: Encode and Decode TinyURL
 * 
 * This class implements a URL shortening service similar to TinyURL.
 * It provides methods to encode long URLs into short URLs and decode them back.
 */
public class Codec {
    // Base URL for the shortened URLs
    private static final String BASE_URL = "http://tinyurl.com/";
    
    // Maps to store the bidirectional relationship between long and short URLs
    private Map<String, String> longToShort;  // longUrl -> shortUrl
    private Map<String, String> shortToLong;  // shortUrl -> longUrl
    
    // Counter for generating unique short URL codes
    private int counter;
    
    // Characters used for generating short URL codes (base62 encoding)
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    
    public Codec() {
        this.longToShort = new HashMap<>();
        this.shortToLong = new HashMap<>();
        this.counter = 0;
    }
    
    /**
     * Encodes a long URL to a shortened URL.
     * 
     * @param longUrl The original long URL to encode
     * @return A shortened URL in the format "http://tinyurl.com/XXXXX"
     */
    public String encode(String longUrl) {
        // If already encoded, return the existing short URL
        if (longToShort.containsKey(longUrl)) {
            return longToShort.get(longUrl);
        }
        
        // Generate a unique short code
        String shortCode = generateShortCode();
        String shortUrl = BASE_URL + shortCode;
        
        // Store the mapping in both directions
        longToShort.put(longUrl, shortUrl);
        shortToLong.put(shortUrl, longUrl);
        
        return shortUrl;
    }
    
    /**
     * Decodes a shortened URL to its original URL.
     * 
     * @param shortUrl The shortened URL to decode
     * @return The original long URL
     */
    public String decode(String shortUrl) {
        return shortToLong.getOrDefault(shortUrl, "");
    }
    
    /**
     * Generates a unique short code for the URL.
     * Uses a counter-based approach with base62 encoding.
     * 
     * @return A unique short code string
     */
    private String generateShortCode() {
        int num = counter++;
        StringBuilder sb = new StringBuilder();
        
        // Convert counter to base62
        if (num == 0) {
            return "0";
        }
        
        while (num > 0) {
            sb.append(CHARS.charAt(num % 62));
            num /= 62;
        }
        
        return sb.reverse().toString();
    }
}
