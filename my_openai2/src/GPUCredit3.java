import java.util.*;

public class GPUCredit3 {
    private enum EventType { ADD, SUB }

    private static class Event {
        final int timestamp;
        final EventType type;
        final int amount;
        final int endTime; // 仅 ADD 有效

        Event(int timestamp, EventType type, int amount, int endTime) {
            this.timestamp = timestamp;
            this.type = type;
            this.amount = amount;
            this.endTime = endTime;
        }
    }

    private List<Event> events = new ArrayList<>();

    public void grantCredit(String id, int amount, int startTime, int expirationTime) {
        events.add(new Event(startTime, EventType.ADD, amount, expirationTime));
    }

    /** Deduct from credits at timestamp; use soonest-expiring first. */
    public void subtract(int amount, int timestamp) {
        events.add(new Event(timestamp, EventType.SUB, amount, 0));
    }

    public int getBalance(int timestamp) {
        List<Event> validEvents = new ArrayList<>();
        for (Event e : events) {
            if (e.timestamp <= timestamp) {
                validEvents.add(e);
            }
        }

        validEvents.sort((a, b) -> {
            if (a.timestamp == b.timestamp) {
                return a.type.ordinal() - b.type.ordinal();
            }
            return a.timestamp - b.timestamp;
        });

        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> (a[0] - b[0]));
        for (Event e : validEvents) {
            if (e.type == EventType.ADD) {
                queue.add(new int[]{e.endTime, e.amount});
            } else {
                int need = e.amount;
                int t = e.timestamp;
                while (!queue.isEmpty() && need > 0) {
                    int[] cur = queue.poll();
                    int end = cur[0], amt = cur[1];
                    if (end <= t) continue;
                    if (amt > need) {
                        queue.add(new int[]{end, amt - need});
                        need = 0;
                    } else {
                        need -= amt;
                    }
                }
            }
        }

        // Credit active on [startTime, expirationTime - 1]; drop expired at balance time
        while (!queue.isEmpty() && queue.peek()[0] <= timestamp) {
            queue.poll();
        }
        int valid = 0;
        while (!queue.isEmpty()) {
            valid += queue.poll()[1];
        }
        return valid;

    }

    public static void main(String[] args) {
        GPUCredit3 cs = new GPUCredit3();
        cs.grantCredit("a", 3, 10, 60);
        if (cs.getBalance(10) != 3) throw new AssertionError("getBalance(10)");

        cs.grantCredit("b", 2, 20, 40);
        cs.subtract(1, 30);
        cs.subtract(3, 50);

        if (cs.getBalance(10) != 3) throw new AssertionError("getBalance(10)=3");
        if (cs.getBalance(20) != 5) throw new AssertionError("getBalance(20)=5");
        if (cs.getBalance(30) != 4) throw new AssertionError("getBalance(30)=4");
        if (cs.getBalance(35) != 4) throw new AssertionError("getBalance(35)=4");
        if (cs.getBalance(40) != 3) throw new AssertionError("getBalance(40)=3");
        if (cs.getBalance(50) != 0) throw new AssertionError("getBalance(50)=0");

        // Partial subtract: 5 units, subtract 2, remainder 3 must stay
        GPUCredit3 cs2 = new GPUCredit3();
        cs2.grantCredit("x", 5, 0, 100);
        cs2.subtract(2, 10);
        if (cs2.getBalance(10) != 3) throw new AssertionError("partial subtract remainder");

        System.out.println("All checks passed.");
    }
}
