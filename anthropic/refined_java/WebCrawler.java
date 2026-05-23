package anthropic.refined_java;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class WebCrawler {
    public interface HtmlParser {
        List<String> getUrls(String url);
    }

    public List<String> crawl(String startUrl, HtmlParser htmlParser) {
        String startHost = getHost(normalize(startUrl));

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        String normalizedStart = normalize(startUrl);
        visited.add(normalizedStart);
        queue.offer(startUrl);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String nextUrl : htmlParser.getUrls(current)) {
                String normalizedNext = normalize(nextUrl);
                if (!startHost.equals(getHost(normalizedNext)) || visited.contains(normalizedNext)) {
                    continue;
                }

                visited.add(normalizedNext);
                queue.offer(nextUrl);
            }
        }

        return new ArrayList<>(visited);
    }

    private String getHost(String url) {
        // input: "http://news.yahoo.com/news/topics/"
        // output: "news.yahoo.com"
        return URI.create(url).getHost();
    }

    private String normalize(String url) {
        int fragmentIndex = url.indexOf('#');
        return fragmentIndex == -1 ? url : url.substring(0, fragmentIndex);
    }

    // part 2: multi thread BFS
    public List<String> crawlBFS(String startUrl, HtmlParser htmlParser) {
        String host = getHost(normalize(startUrl));

        Set<String> visited = ConcurrentHashMap.newKeySet();
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        AtomicInteger activeTasks = new AtomicInteger(1);

        visited.add(normalize(startUrl));
        queue.offer(startUrl);

        int workers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            executor.submit(() -> {
                try {
                    while (true) {
                        String url = queue.poll(100, TimeUnit.MILLISECONDS);

                        if (url == null) {
                            if (activeTasks.get() == 0) {
                                break;
                            }
                            continue;
                        }

                        for (String next : htmlParser.getUrls(url)) {
                            String normalizedNext = normalize(next);
                            if (!getHost(normalizedNext).equals(host)) {
                                continue;
                            }

                            if (visited.add(normalizedNext)) {
                                activeTasks.incrementAndGet();
                                queue.offer(next);
                            }
                        }

                        if (activeTasks.decrementAndGet() == 0) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();
        return new ArrayList<>(visited);
    }

    // part 2: multi thread DFS
    // public List<String> crawlMulti(String startUrl, HtmlParser htmlParser) {
    //     String host = getHost(startUrl);

    //     Set<String> visited = ConcurrentHashMap.newKeySet();
    //     ExecutorService executor = Executors.newFixedThreadPool(16);

    //     visited.add(startUrl);
    //     crawlUrl(startUrl, host, htmlParser, visited, executor);
    //     executor.shutdown();
    //     return new ArrayList<>(visited);
    // }

    // private void crawlUrl(
    //     String url,
    //     String host,
    //     HtmlParser htmlParser,
    //     Set<String> visited,
    //     ExecutorService executor
    // ) {
    //     List<Future<?>> futures = new ArrayList<>();

    //     for (String next : htmlParser.getUrls(url)) {
    //         if (!getHost(next).equals(host)) {
    //             continue;
    //         }

    //         if (visited.add(next)) {
    //             futures.add(executor.submit(() -> {
    //                 crawlUrl(next, host, htmlParser, visited, executor);
    //             }));
    //         }
    //     }

    //     for (Future<?> future : futures) {
    //         try {
    //             future.get();
    //         } catch (Exception e) {
    //             // LeetCode 环境下一般不会发生
    //         }
    //     }
    // }

    private static class TestHtmlParser implements HtmlParser {
        private final Map<String, List<String>> graph = new HashMap<>();

        TestHtmlParser(List<String> urls, int[][] edges) {
            for (String url : urls) {
                graph.put(url, new ArrayList<>());
            }
            for (int[] edge : edges) {
                graph.get(urls.get(edge[0])).add(urls.get(edge[1]));
            }
        }

        @Override
        public List<String> getUrls(String url) {
            return graph.getOrDefault(url, Collections.emptyList());
        }
    }

    private static void assertEquals(List<String> actual, List<String> expected, String testName) {
        Collections.sort(actual);
        Collections.sort(expected);
        if (!actual.equals(expected)) {
            throw new AssertionError(testName + " failed. expected=" + expected + ", actual=" + actual);
        }
        System.out.println(testName + " passed: " + actual);
    }

    public static void main(String[] args) {
        WebCrawler crawler = new WebCrawler();

        List<String> urls1 = List.of(
            "http://example.com/page1",
            "http://example.com/page2",
            "http://example.com/page3#sectionA",
            "http://example.net/page4#"
        );
        int[][] edges1 = {
            {0, 1},
            {0, 2},
            {1, 3},
            {2, 0}
        };
        assertEquals(
            crawler.crawlBFS("http://example.com/page1", new TestHtmlParser(urls1, edges1)),
            new ArrayList<>(List.of(
                "http://example.com/page1",
                "http://example.com/page2",
                "http://example.com/page3"
            )),
            "same host and fragment test"
        );

        List<String> urls2 = List.of(
            "http://news.yahoo.com/home",
            "http://news.google.com/top",
            "http://news.yahoo.com/news"
        );
        int[][] edges2 = {
            {1, 0},
            {0, 2}
        };
        assertEquals(
            crawler.crawlBFS("http://news.google.com/top", new TestHtmlParser(urls2, edges2)),
            new ArrayList<>(List.of("http://news.google.com/top")),
            "different host test"
        );

        List<String> urls3 = List.of(
            "http://site.com/a",
            "http://site.com/b#frag1",
            "http://site.com/b#frag2",
            "http://site.com/c",
            "http://other.com/x",
            "http://site.com/d",
            "http://site.com/e#",
            "http://site.com/f"
        );
        int[][] edges3 = {
            {0, 1},
            {0, 2},
            {1, 3},
            {2, 3},
            {3, 4},
            {3, 5},
            {5, 0},
            {5, 6},
            {6, 7},
            {7, 0}
        };
        assertEquals(
            crawler.crawlBFS("http://site.com/a", new TestHtmlParser(urls3, edges3)),
            new ArrayList<>(List.of(
                "http://site.com/a",
                "http://site.com/b",
                "http://site.com/c",
                "http://site.com/d",
                "http://site.com/e",
                "http://site.com/f"
            )),
            "cycle and duplicate fragment test"
        );
    }
}
