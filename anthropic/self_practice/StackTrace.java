package anthropic.self_practice;

import java.util.ArrayList;
import java.util.Collections;
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

class State {
    String name;
    int streak;
    boolean started;

    public State(String name, int streak, boolean started) {
        this.name = name;
        this.streak = streak;
        this.started = started;
    }
}

public class StackTrace {

    private int getCommonPrefix(List<String> previous, List<String> current) {
        int i = 0;
        while (i < previous.size() && i < current.size() && previous.get(i).equals(current.get(i))) {
            i ++;
        }
        return i;
    }

    private int getCommonPrefix2(List<State> active, List<String> current) {
        int i = 0;
        while (i < active.size() && i < current.size() && active.get(i).name.equals(current.get(i))) {
            i ++;
        }
        return i;
    }

    public List<Event> convertSamplesToEvents(List<Sample> samples) {
        List<Event> result = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        for (Sample sample: samples) {
            List<String> current = sample.stack;
            int common = getCommonPrefix(previous, current);

            for (int i = previous.size() - 1; i >= common ; i --) {
                result.add(new Event("end", sample.ts, previous.get(i)));
            }

            for (int i = common ; i < current.size() ; i ++) {
                result.add(new Event("start", sample.ts, current.get(i)));
            }
            previous = current;
        }
        return result;
    }

    public List<Event> convertSamplesToDebouncedEvents(List<Sample> samples, int n) {
        List<Event> result = new ArrayList<>();
        List<State> active = new ArrayList<>();

        for (Sample sample : samples) {
            List<String> current = sample.stack;
            int common = getCommonPrefix2(active, current);

            for (int i = active.size() - 1 ; i >= common ; i --) {
                State state = active.get(i);
                if (state.started) {
                    result.add(new Event("end", sample.ts, state.name));
                }
                active.remove(i);
            }

            for (int i = 0 ; i < common ; i ++) {
                State state = active.get(i);
                state.streak ++;
                if (!state.started && state.streak >= n) {
                    state.started = true;
                    result.add(new Event("start", sample.ts, state.name));
                }
            }

            for (int i = common ; i < current.size() ; i ++) {
                boolean started = n == 1;
                State state = new State(current.get(i), 1, started);
                active.add(state);

                if (started) {
                    result.add(new Event("start", sample.ts, state.name));
                }
            }
        }
        return result;
    }

    public List<Event> convertSamplesToEventsWithSuffix(List<Sample> samples) {
        List<Event> result = new ArrayList<>();
        List<String> previous = new ArrayList<>();
        for (Sample sample: samples) {
            List<String> current = new ArrayList<>(sample.stack);
            Collections.reverse(current);
            int common = getCommonPrefix(previous, current);

            for (int i = previous.size() - 1; i >= common ; i --) {
                result.add(new Event("end", sample.ts, previous.get(i)));
            }

            for (int i = common ; i < current.size() ; i ++) {
                result.add(new Event("start", sample.ts, current.get(i)));
            }
            previous = current;
        }

        // if (!samples.isEmpty() && !previous.isEmpty()) {
        //     // 这里使用最后一个 sample 的 timestamp 作为结束时间
        //     long lastTs = samples.get(samples.size() - 1).ts; 
        //     for (int i = previous.size() - 1; i >= 0; i--) {
        //         result.add(new Event("end", lastTs, previous.get(i)));
        //     }
        // }

        return result;
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

        assertEvents(
            "part3 empty input",
            converter.convertSamplesToEventsWithSuffix(List.of()),
            List.of()
        );

        assertEvents(
            "part3 depth change restarts because leaf changes",
            converter.convertSamplesToEventsWithSuffix(List.of(
                new Sample(1.0, List.of("main")),
                new Sample(2.0, List.of("main", "foo")),
                new Sample(3.0, List.of("main"))
            )),
            List.of(
                new Event("start", 1.0, "main"),
                new Event("end", 2.0, "main"),
                new Event("start", 2.0, "main"),
                new Event("start", 2.0, "foo"),
                new Event("end", 3.0, "foo"),
                new Event("end", 3.0, "main"),
                new Event("start", 3.0, "main")
            )
        );

        assertEvents(
            "part3 leaf change restarts shared prefix",
            converter.convertSamplesToEventsWithSuffix(List.of(
                new Sample(5.0, List.of("main", "foo")),
                new Sample(10.0, List.of("main", "bar")),
                new Sample(15.0, List.of("main", "foo"))
            )),
            List.of(
                new Event("start", 5.0, "main"),
                new Event("start", 5.0, "foo"),
                new Event("end", 10.0, "foo"),
                new Event("end", 10.0, "main"),
                new Event("start", 10.0, "main"),
                new Event("start", 10.0, "bar"),
                new Event("end", 15.0, "bar"),
                new Event("end", 15.0, "main"),
                new Event("start", 15.0, "main"),
                new Event("start", 15.0, "foo")
            )
        );

        assertEvents(
            "part3 keeps common leaf suffix",
            converter.convertSamplesToEventsWithSuffix(List.of(
                new Sample(10.0, List.of("taskA", "process", "save")),
                new Sample(20.0, List.of("taskB", "process", "save")),
                new Sample(30.0, List.of("taskB", "flush"))
            )),
            List.of(
                new Event("start", 10.0, "taskA"),
                new Event("start", 10.0, "process"),
                new Event("start", 10.0, "save"),
                new Event("end", 20.0, "taskA"),
                new Event("start", 20.0, "taskB"),
                new Event("end", 30.0, "save"),
                new Event("end", 30.0, "process"),
                new Event("end", 30.0, "taskB"),
                new Event("start", 30.0, "taskB"),
                new Event("start", 30.0, "flush")
            )
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
}
