import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadTest {
    public static void main(String[] args){
        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int i = 0 ; i < 10000 ; i ++) {
//            System.out.println(i);

            executor.submit(new MyRunnable(i+""));
        }
    }


    static class MyRunnable implements Runnable {
        private final String name;

        public MyRunnable(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String threadName = Thread.currentThread().getName();
                long threadId = Thread.currentThread().getId();
                System.out.println("Running in thread name: " + threadName);
                System.out.println("Running in thread ID: " + threadId);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
                return "Hello";
            });
            CompletableFuture<String> greeting = future
                    .thenApply(result -> result + " World")

                    // Step 3: 任务完成时执行（成功或失败都会执行）
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            System.out.println("⚠️ Task failed: " + exception.getMessage());
                        } else {
                            System.out.println("✅ Task completed successfully with result: " + name);
                        }
                    });
            try {
//                Thread.sleep(1000);
                System.out.println(name + "finish");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
    }

//    public class ThreadParamExample2 {
//        public static void main(String[] args) {
//            Thread thread = new Thread(new MyRunnable("Bob"));
//            thread.start();
//        }
//    }
}
