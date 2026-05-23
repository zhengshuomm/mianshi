package anthropic.refined_java;

import java.util.ArrayList;
import java.util.List;

class Sample {
    double ts;
    List<String> stack;

    public Sample(double ts, List<String> stack) {
        this.ts = ts;
        this.stack = stack;
    }
}

class Event {
    String kind;
    double ts;
    String name;

    public Event(String kind, double ts, String name) {
        this.kind = kind;
        this.ts = ts;
        this.name = name;
    }

    @Override
    public String toString() {
        return kind + " " + ts + " " + name;
    }
}

public class StackTrace {
    // Part 1: 把每次调用栈变化转换成 start/end 事件。
    public List<Event> convertSamplesToEvents(List<Sample> samples) {
        List<Event> events = new ArrayList<>();

        List<String> previous = new ArrayList<>();

        for (Sample sample : samples) {
            List<String> current = sample.stack;

            int common = commonPrefixLength(previous, current);

            // 例如 main -> a -> b 变成 main 时，必须先 end b，再 end a。
            for (int i = previous.size() - 1; i >= common; i--) {
                events.add(new Event("end", sample.ts, previous.get(i)));
            }

            // 例如 main 变成 main -> a -> b 时，必须先 start a，再 start b。
            for (int i = common; i < current.size(); i++) {
                events.add(new Event("start", sample.ts, current.get(i)));
            }

            previous = current;
        }

        return events;
    }

    private int commonPrefixLength(List<String> a, List<String> b) {
        int i = 0;
        while (i < a.size() && i < b.size() && a.get(i).equals(b.get(i))) {
            i++;
        }
        return i;
    }

    // Part 2: 对短暂出现的 frame 做 debounce，只保留连续出现 n 次的 frame。
    private static class FrameState {
        // 当前深度上的函数名。
        String name;

        // 这个精确 frame 已经连续出现了多少次。
        int streak;

        // 是否已经为这个 frame 输出过 start 事件。
        boolean started;

        FrameState(String name, int streak, boolean started) {
            this.name = name;
            this.streak = streak;
            this.started = started;
        }
    }

    public List<Event> convertSamplesToDebouncedEvents(List<Sample> samples, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }

        List<Event> events = new ArrayList<>();

        // active 表示当前还在观察中的调用栈，并且每个 frame 都带着 debounce 状态。
        // active 的下标就是调用栈深度，所以 parent path 可以通过前缀体现出来。
        List<FrameState> active = new ArrayList<>();

        for (Sample sample : samples) {
            List<String> current = sample.stack;

            // 只有没有变化的前缀可以延续 streak。如果调用栈在深度 k 发生变化，
            // 那么深度 k 以及更深的 frame 都被打断，streak 要重置。
            int common = commonActivePrefixLength(active, current);

            // 移除被打断的 frame，顺序同样是从内层到外层。
            // 如果某个 frame 从没达到 n 次，它就没有输出过 start，因此也不能输出 end。
            for (int i = active.size() - 1; i >= common; i--) {
                FrameState frame = active.get(i);
                if (frame.started) {
                    events.add(new Event("end", sample.ts, frame.name));
                }
                active.remove(i);
            }

            // common prefix 里的 frame 又连续出现了一次，所以 streak++。
            // 当 streak 第一次达到 n 时，说明这个 frame 被确认，输出 start。
            for (int i = 0; i < common; i++) {
                FrameState frame = active.get(i);
                frame.streak++;
                if (!frame.started && frame.streak >= n) {
                    frame.started = true;
                    events.add(new Event("start", sample.ts, frame.name));
                }
            }

            // common prefix 之后的 frame 是新出现的 frame。
            // 它们的 streak 从 1 开始，因为这是第一次连续出现。
            for (int i = common; i < current.size(); i++) {
                boolean started = n == 1;
                FrameState frame = new FrameState(current.get(i), 1, started);
                active.add(frame);

                // 当 n == 1 时，debounce 等价于普通模式，出现一次就立刻 start。
                if (started) {
                    events.add(new Event("start", sample.ts, frame.name));
                }
            }
        }

        return events;
    }

    private int commonActivePrefixLength(List<FrameState> active, List<String> current) {
        // 只有同名函数出现在同一深度，并且 parent prefix 也相同时，
        // 才算同一个 frame 的连续出现。前缀比较正好能表达这个规则。
        int i = 0;
        while (i < active.size() && i < current.size() && active.get(i).name.equals(current.get(i))) {
            i++;
        }
        return i;
    }

    public static void main(String[] args) {
        StackTrace converter = new StackTrace();

        assertEvents(
            "part1 empty input",
            converter.convertSamplesToEvents(List.of()),
            List.of()
        );

        assertEvents(
            "part1 basic example",
            converter.convertSamplesToEvents(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.5, List.of("main", "func1")),
                new Sample(3.1, List.of("main"))
            )),
            List.of(
                new Event("start", 1.0, "main"),
                new Event("start", 2.5, "func1"),
                new Event("end", 3.1, "func1")
            )
        );

        assertEvents(
            "part1 repeated identical sample creates no extra event",
            converter.convertSamplesToEvents(List.of(
                new Sample(1.0, List.of("main", "foo")),
                new Sample(2.0, List.of("main", "foo")),
                new Sample(3.0, List.of("main"))
            )),
            List.of(
                new Event("start", 1.0, "main"),
                new Event("start", 1.0, "foo"),
                new Event("end", 3.0, "foo")
            )
        );

        assertEvents(
            "part1 full stack replacement closes before opens",
            converter.convertSamplesToEvents(List.of(
                new Sample(1.0, List.of("a", "b")),
                new Sample(2.0, List.of("c", "d"))
            )),
            List.of(
                new Event("start", 1.0, "a"),
                new Event("start", 1.0, "b"),
                new Event("end", 2.0, "b"),
                new Event("end", 2.0, "a"),
                new Event("start", 2.0, "c"),
                new Event("start", 2.0, "d")
            )
        );

        assertEvents(
            "part1 recursion treats same name at different depth separately",
            converter.convertSamplesToEvents(List.of(
                new Sample(1.0, List.of("main", "foo")),
                new Sample(2.0, List.of("main", "foo", "foo")),
                new Sample(3.0, List.of("main", "bar"))
            )),
            List.of(
                new Event("start", 1.0, "main"),
                new Event("start", 1.0, "foo"),
                new Event("start", 2.0, "foo"),
                new Event("end", 3.0, "foo"),
                new Event("end", 3.0, "foo"),
                new Event("start", 3.0, "bar")
            )
        );

        assertEvents(
            "part2 empty input",
            converter.convertSamplesToDebouncedEvents(List.of(), 2),
            List.of()
        );

        assertEvents(
            "part2 debounce example n=2",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.0, List.of("main")),
                new Sample(3.0, List.of("main", "foo")),
                new Sample(4.0, List.of("main", "foo"))
            ), 2),
            List.of(
                new Event("start", 2.0, "main"),
                new Event("start", 4.0, "foo")
            )
        );

        assertEvents(
            "part2 short lived frame is ignored",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.0, List.of("main")),
                new Sample(3.0, List.of("main", "fast")),
                new Sample(4.0, List.of("main"))
            ), 2),
            List.of(
                new Event("start", 2.0, "main")
            )
        );

        assertEvents(
            "part2 confirmed frame emits end when removed",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.0, List.of("main", "foo")),
                new Sample(3.0, List.of("main", "foo")),
                new Sample(4.0, List.of("main"))
            ), 2),
            List.of(
                new Event("start", 2.0, "main"),
                new Event("start", 3.0, "foo"),
                new Event("end", 4.0, "foo")
            )
        );

        assertEvents(
            "part2 path change resets streak even with same names",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("a", "b")),
                new Sample(2.0, List.of("c", "b", "a")),
                new Sample(3.0, List.of("c", "b", "a"))
            ), 2),
            List.of(
                new Event("start", 3.0, "c"),
                new Event("start", 3.0, "b"),
                new Event("start", 3.0, "a")
            )
        );

        assertEvents(
            "part2 separate appearances do not combine streak",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("main", "foo")),
                new Sample(2.0, List.of("main")),
                new Sample(3.0, List.of("main", "foo")),
                new Sample(4.0, List.of("main"))
            ), 2),
            List.of(
                new Event("start", 2.0, "main")
            )
        );

        assertEvents(
            "part2 n=1 matches normal start/end behavior",
            converter.convertSamplesToDebouncedEvents(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.0, List.of("main", "foo")),
                new Sample(3.0, List.of("main"))
            ), 1),
            List.of(
                new Event("start", 1.0, "main"),
                new Event("start", 2.0, "foo"),
                new Event("end", 3.0, "foo")
            )
        );

        assertThrows(
            "part2 invalid n",
            () -> converter.convertSamplesToDebouncedEvents(List.of(), 0)
        );

        System.out.println("All StackTrace tests passed.");
    }

    private static void assertEvents(String testName, List<Event> actual, List<Event> expected) {
        if (actual.size() != expected.size()) {
            throw new AssertionError(testName + " failed. expected=" + expected + ", actual=" + actual);
        }

        for (int i = 0; i < actual.size(); i++) {
            Event a = actual.get(i);
            Event e = expected.get(i);
            if (!a.kind.equals(e.kind) || a.ts != e.ts || !a.name.equals(e.name)) {
                throw new AssertionError(testName + " failed at index " + i
                    + ". expected=" + expected + ", actual=" + actual);
            }
        }

        System.out.println(testName + " passed");
    }

    private static void assertThrows(String testName, Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException e) {
            System.out.println(testName + " passed");
            return;
        }

        throw new AssertionError(testName + " failed. expected IllegalArgumentException");
    }
}
