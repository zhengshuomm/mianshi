package anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stack / debounced / suffix-debounced profilers (from function_profile.py). */
public class FunctionProfile {

    public static class Solution {
        public List<String> stackEvents(List<String> samples) {
            List<String> results = new ArrayList<>();
            List<String> prevStack = new ArrayList<>();
            for (String sample : samples) {
                String[] parts = sample.split(":", 2);
                String timestamp = parts[0];
                List<String> currentStack = new ArrayList<>();
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    for (String s : parts[1].split("->")) {
                        currentStack.add(s);
                    }
                }
                int sharedLen = 0;
                int minLen = Math.min(prevStack.size(), currentStack.size());
                while (sharedLen < minLen
                        && prevStack.get(sharedLen).equals(currentStack.get(sharedLen))) {
                    sharedLen++;
                }
                for (int i = prevStack.size() - 1; i >= sharedLen; i--) {
                    results.add("end:" + timestamp + ":" + prevStack.get(i));
                }
                for (int i = sharedLen; i < currentStack.size(); i++) {
                    results.add("start:" + timestamp + ":" + currentStack.get(i));
                }
                prevStack = new ArrayList<>(currentStack);
            }
            return results;
        }
    }

    static class PathInfo {
        int count;
        String startTime;
        boolean isActive;
    }

    public static class Solution2 {
        public List<String> debouncedEvents(List<String> samples, int n) {
            Map<String, PathInfo> pathInfo = new HashMap<>();
            Set<String> prevPaths = new HashSet<>();
            List<String> results = new ArrayList<>();

            for (String sample : samples) {
                String[] parts = sample.split(":", 2);
                String timestamp = parts[0];
                List<String> stack = new ArrayList<>();
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    for (String s : parts[1].split("->")) {
                        stack.add(s);
                    }
                }
                Set<String> currentPaths = new HashSet<>();
                for (int i = 1; i <= stack.size(); i++) {
                    List<String> prefix = stack.subList(0, i);
                    String key = pathKey(prefix);
                    currentPaths.add(key);
                    PathInfo info =
                            pathInfo.computeIfAbsent(
                                    key,
                                    k -> {
                                        PathInfo p = new PathInfo();
                                        p.count = 0;
                                        p.startTime = timestamp;
                                        p.isActive = false;
                                        return p;
                                    });
                    info.count++;
                    if (!info.isActive && info.count >= n) {
                        info.isActive = true;
                        results.add("start:" + info.startTime + ":" + stack.get(i - 1));
                    }
                }
                List<String> toEnd = new ArrayList<>(prevPaths);
                toEnd.sort(
                        Comparator.comparingInt((String k) -> k.split("\u0000", -1).length)
                                .reversed());
                for (String pathKey : toEnd) {
                    if (!currentPaths.contains(pathKey)) {
                        PathInfo info = pathInfo.get(pathKey);
                        if (info != null && info.isActive) {
                            String lastSeg =
                                    pathKey.contains("\u0000")
                                            ? pathKey.substring(pathKey.lastIndexOf('\u0000') + 1)
                                            : pathKey;
                            results.add("end:" + timestamp + ":" + lastSeg);
                        }
                        pathInfo.remove(pathKey);
                    }
                }
                prevPaths = new HashSet<>(currentPaths);
            }
            return results;
        }

        static String pathKey(List<String> segments) {
            return String.join("\u0000", segments);
        }
    }

    public static class Solution3 {
        public List<String> suffixDebouncedEvents(List<String> samples, int n) {
            Map<String, PathInfo> pathInfo = new HashMap<>();
            Set<String> prevSuffixes = new HashSet<>();
            List<String> results = new ArrayList<>();

            for (String sample : samples) {
                String[] parts = sample.split(":", 2);
                String timestamp = parts[0];
                List<String> raw = new ArrayList<>();
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    for (String s : parts[1].split("->")) {
                        raw.add(s);
                    }
                }
                List<String> rev = new ArrayList<>();
                for (int i = raw.size() - 1; i >= 0; i--) {
                    rev.add(raw.get(i));
                }

                Set<String> currentSuffixes = new HashSet<>();
                for (int i = 1; i <= rev.size(); i++) {
                    List<String> pref = rev.subList(0, i);
                    String key = Solution2.pathKey(pref);
                    currentSuffixes.add(key);
                    PathInfo info =
                            pathInfo.computeIfAbsent(
                                    key,
                                    k -> {
                                        PathInfo p = new PathInfo();
                                        p.count = 0;
                                        p.startTime = timestamp;
                                        p.isActive = false;
                                        return p;
                                    });
                    info.count++;
                }

                for (int i = rev.size(); i >= 1; i--) {
                    List<String> pref = rev.subList(0, i);
                    String key = Solution2.pathKey(pref);
                    PathInfo info = pathInfo.get(key);
                    if (info != null && info.count >= n && !info.isActive) {
                        String name =
                                key.contains("\u0000")
                                        ? key.substring(key.lastIndexOf('\u0000') + 1)
                                        : key;
                        results.add("start:" + info.startTime + ":" + name);
                        info.isActive = true;
                    }
                }

                List<String> toEnd = new ArrayList<>(prevSuffixes);
                // For suffix chains, emit shorter suffix first: save -> process -> taskB
                toEnd.sort(Comparator.comparingInt(k -> k.split("\u0000", -1).length));
                for (String key : toEnd) {
                    if (!currentSuffixes.contains(key)) {
                        PathInfo info = pathInfo.get(key);
                        if (info != null && info.isActive) {
                            String name =
                                    key.contains("\u0000")
                                            ? key.substring(key.lastIndexOf('\u0000') + 1)
                                            : key;
                            results.add("end:" + timestamp + ":" + name);
                        }
                        pathInfo.remove(key);
                    }
                }
                prevSuffixes = new HashSet<>(currentSuffixes);
            }
            return results;
        }
    }

    static void assertEq(List<String> a, List<String> b) {
        if (!a.equals(b)) {
            throw new AssertionError(a + " != " + b);
        }
    }

    public static void main(String[] args) {
        Solution s = new Solution();
        assertEq(
                s.stackEvents(
                        Arrays.asList(
                                "10:main->calc",
                                "20:main->calc->helper",
                                "30:main->calc->helper->helper",
                                "40:main->calc->helper",
                                "50:main")),
                Arrays.asList(
                        "start:10:main",
                        "start:10:calc",
                        "start:20:helper",
                        "start:30:helper",
                        "end:40:helper",
                        "end:50:helper",
                        "end:50:calc"));

        Solution2 s2 = new Solution2();
        assertEq(
                s2.debouncedEvents(
                        Arrays.asList(
                                "1:main",
                                "2:main->A",
                                "3:main->A",
                                "4:main->B",
                                "5:main->B->C",
                                "6:main->B"),
                        2),
                Arrays.asList("start:1:main", "start:2:A", "end:4:A", "start:4:B"));

        Solution3 s3 = new Solution3();
        assertEq(
                s3.suffixDebouncedEvents(
                        Arrays.asList(
                                "10:taskA->process->save",
                                "20:taskB->process->save",
                                "30:taskB->process->save",
                                "40:taskB->flush"),
                        2),
                Arrays.asList(
                        "start:10:process",
                        "start:10:save",
                        "start:20:taskB",
                        "end:40:save",
                        "end:40:process",
                        "end:40:taskB"));

        System.out.println("FunctionProfile: ok");
    }
}
