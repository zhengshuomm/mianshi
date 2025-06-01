import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadTest {
    public static void main(String[] args) {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        try {
            // Submit tasks to the executor
            for (int i = 0; i < 10; i++) {
                executorService.submit(() -> {
                    System.out.println("111");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } finally {
            // Shutdown the executor to release resources
//            executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
            executorService.shutdown();
            System.out.println("end");
        }
    }
}
