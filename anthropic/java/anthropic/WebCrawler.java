package anthropic;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Parallel BFS crawl same-host (from web_crawler.py). */
public class WebCrawler {

    public static class HtmlParser {
        final Map<String, List<String>> graph = new HashMap<>();

        public HtmlParser(List<String> urls, List<int[]> edges) {
            for (String u : urls) {
                graph.put(u, new ArrayList<>());
            }
            for (int[] e : edges) {
                graph.get(urls.get(e[0])).add(urls.get(e[1]));
            }
        }

        public List<String> getUrls(String url) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            List<String> links = graph.get(url);
            return links != null ? links : Collections.emptyList();
        }
    }

    public static String canonicalUrl(String url) {
        int h = url.indexOf('#');
        return h < 0 ? url : url.substring(0, h);
    }

    public static String getHost(String url) {
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            return host != null ? host : "";
        } catch (Exception e) {
            return "";
        }
    }

    public List<String> crawl(String startUrl, HtmlParser htmlParser) {
        String startHost = getHost(canonicalUrl(startUrl));
        String startCanon = canonicalUrl(startUrl);

        Set<String> visited = new HashSet<>();
        visited.add(startCanon);

        ReentrantLock lock = new ReentrantLock();
        Condition done = lock.newCondition();
        int[] active = {1};

        ExecutorService executor = Executors.newFixedThreadPool(10);
        executor.submit(
                () -> crawlTask(
                        startUrl,
                        startHost,
                        htmlParser,
                        visited,
                        lock,
                        done,
                        active,
                        executor));

        lock.lock();
        try {
            while (active[0] > 0) {
                done.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
        executor.shutdown();
        List<String> out = new ArrayList<>(visited);
        Collections.sort(out);
        return out;
    }

    private void crawlTask(
            String rawUrl,
            String startHost,
            HtmlParser htmlParser,
            Set<String> visited,
            ReentrantLock lock,
            Condition done,
            int[] active,
            ExecutorService executor) {
        try {
            for (String link : htmlParser.getUrls(rawUrl)) {
                String canon = canonicalUrl(link);
                if (!startHost.equals(getHost(canon))) {
                    continue;
                }
                lock.lock();
                try {
                    if (visited.contains(canon)) {
                        continue;
                    }
                    visited.add(canon);
                    active[0]++;
                } finally {
                    lock.unlock();
                }
                executor.submit(
                        () -> crawlTask(
                                link,
                                startHost,
                                htmlParser,
                                visited,
                                lock,
                                done,
                                active,
                                executor));
            }
        } finally {
            lock.lock();
            try {
                active[0]--;
                if (active[0] == 0) {
                    done.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
