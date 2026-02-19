public class RateLimiterDemo {
    public static void main(String[] args) throws InterruptedException {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 2000);
        
        System.out.println("Rate Limiter: 3 requests per 2 seconds\n");
        
        for (int i = 1; i <= 5; i++) {
            System.out.println("Request " + i + ": " + 
                (limiter.allowRequest() ? "ALLOWED" : "REJECTED"));
            Thread.sleep(300);
        }
        
        System.out.println("\nWaiting 2 seconds...\n");
        Thread.sleep(2000);
        
        for (int i = 1; i <= 3; i++) {
            System.out.println("Request " + i + ": " + 
                (limiter.allowRequest() ? "ALLOWED" : "REJECTED"));
        }
    }
}
