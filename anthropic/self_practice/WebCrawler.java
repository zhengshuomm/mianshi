package anthropic.self_practice;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

// Threads vs Processes
// Scaling to Many Machines
// Being "Polite"
// Handling Duplicate Pages
public class WebCrawler {
    public interface HtmlParser {
        List<String> getUrls(String url);
    }
    
    public List<String> crawl(String startUrl, HtmlParser htmlParser) {
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        String host = getHost(startUrl);

        String start = normalize(startUrl);
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String nextUrl : htmlParser.getUrls(cur)) {
                String normalizedNext = normalize(nextUrl);
                if (!host.equals(getHost(normalizedNext)) || visited.contains(normalizedNext)) {
                    continue;
                }
                visited.add(normalizedNext);
                queue.add(normalizedNext);
            }
        }
        return new ArrayList<>(visited);
    }

    // public List<String> crawl(String startUrl, HtmlParser htmlParser) {
    //     String host = getHostName(startUrl);
    //     Set<String> visited = ConcurrentHashMap.newKeySet();
    //     ExecutorService executor = Executors.newFixedThreadPool(16);
        
    //     visited.add(startUrl);
    //     // 使用队列做层序遍历 (BFS)
    //     Queue<String> queue = new LinkedList<>();
    //     queue.add(startUrl);

    //     while (!queue.isEmpty()) {
    //         List<Future<List<String>>> futures = new ArrayList<>();
            
    //         // 1. 派发当前层级的所有任务
    //         for (String url : queue) {
    //             futures.add(executor.submit(() -> htmlParser.getUrls(url)));
    //         }
            
    //         queue.clear();
            
    //         // 2. 主线程负责阻塞等待这一批任务完成，Worker 线程绝不阻塞
    //         for (Future<List<String>> future : futures) {
    //             try {
    //                 List<String> nextUrls = future.get();
    //                 for (String next : nextUrls) {
    //                     if (getHostName(next).equals(host) && visited.add(next)) {
    //                         queue.add(next);
    //                     }
    //                 }
    //             } catch (Exception e) {
    //                 // 异常处理
    //             }
    //         }
    //     }
        
    //     executor.shutdown();
    //     return new ArrayList<>(visited);
    // }

    private List<String> crawlMulti2(String startUrl, HtmlParser htmlParser) {
        ReentrantLock lock = new ReentrantLock();
        ReentrantReadWriteLock lock2 = new ReentrantReadWriteLock();
        ReadLock readlock = lock2.readLock();
        BlockingQueue<String> queue = new LinkedBlockingDeque<>();
        Set<String> visited = ConcurrentHashMap.newKeySet();
        int workers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        String host = getHost(startUrl);
        queue.add(startUrl);

        AtomicInteger activeTask = new AtomicInteger(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0 ; i < workers ; i ++) {
            executor.submit(()-> {
                    while (true) {
                        String url = null;
                        try {
                            url = queue.poll(100, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {

                        }
                        if (url == null) {
                            if (activeTask.get() == 0) {
                                break;
                            }
                            continue;
                        }

                        try {
                            for (String nextUrl : htmlParser.getUrls(url)) {
                                if (getHost(nextUrl).equals(host) && visited.add(nextUrl)) {
                                    queue.add(nextUrl);
                                    activeTask.incrementAndGet();
                                }
                            }
                        } catch (Exception e) {
 
                        } finally {
                            if (activeTask.decrementAndGet() == 0) {
                                break;
                            }
                        }
                    }
                    done.countDown();
            });
        }

        try {
            done.await();
        } catch (Exception e) {

        } 
        executor.shutdown();
        return new ArrayList<>(visited);
    }

    // private List<String> crawlMulti(String startUrl, HtmlParser htmlParser) {
    //     BlockingQueue<String> queue = new LinkedBlockingDeque<>();
    //     Set<String> visited = ConcurrentHashMap.newKeySet();
    //     int workers = 4;
    //     ExecutorService executor = Executors.newFixedThreadPool(workers);
    //     String host = getHost(startUrl);

    //     queue.add(startUrl);
    //     visited.add(startUrl);

    //     AtomicInteger activeTask = new AtomicInteger(1);
    //     CountDownLatch done = new CountDownLatch(workers);


    //     for (int i = 0 ; i < workers ; i ++) {
    //         executor.submit(()-> {
    //             try {
    //                 while (true) {
    //                     String url = queue.poll(100, TimeUnit.MILLISECONDS);
    //                     if (url == null) {
    //                         if (activeTask.get() == 0) {
    //                             break;
    //                         }
    //                         continue;
    //                     }

    //                     for (String nextUrl : htmlParser.getUrls(url)) {
    //                         if (host.equals(getHost(nextUrl)) && visited.add(nextUrl)) {
    //                             queue.add(nextUrl);
    //                             activeTask.incrementAndGet();
    //                         }
    //                     }

    //                     if (activeTask.decrementAndGet() == 0) {
    //                         break;
    //                     }
    //                 }
    //             } catch (InterruptedException e) {
    //                 e.printStackTrace();
    //             } finally {
    //                 done.countDown();
    //             }
    //         });
    //     }

    //     try {
    //         done.await();
    //     } catch (Exception e) {

    //     }
    //     executor.shutdown();
    //     return new ArrayList<>(visited);
    // }

    private String getHost(String url) {
        return URI.create(url).getHost();
    }

    private String normalize(String url) {
        int index = url.indexOf('#');
        if (index == -1) {
            return url;
        }
        return url.substring(0, index);
    }
    
}



/* 

public List<String> crawl(String startUrl, HtmlParser htmlParser) {
    String host = getHostName(startUrl);
    Set<String> visited = ConcurrentHashMap.newKeySet();
    ExecutorService executor = Executors.newFixedThreadPool(16);
    
    visited.add(startUrl);
    // 使用队列做层序遍历 (BFS)
    Queue<String> queue = new LinkedList<>();
    queue.add(startUrl);

    while (!queue.isEmpty()) {
        List<Future<List<String>>> futures = new ArrayList<>();
        
        // 1. 派发当前层级的所有任务
        for (String url : queue) {
            futures.add(executor.submit(() -> htmlParser.getUrls(url)));
        }
        
        queue.clear();
        
        // 2. 主线程负责阻塞等待这一批任务完成，Worker 线程绝不阻塞
        for (Future<List<String>> future : futures) {
            try {
                List<String> nextUrls = future.get();
                for (String next : nextUrls) {
                    if (getHostName(next).equals(host) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            } catch (Exception e) {
                // 异常处理
            }
        }
    }
    
    executor.shutdown();
    return new ArrayList<>(visited);
}

*/