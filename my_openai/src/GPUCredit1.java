import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * GPU Credit System: 统一事件 + 单堆重放。
 * grant 有效 [startTime, expirationTime-1]；扣减时优先用最先过期的。
 */
public class GPUCredit1 {

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

    private final List<Event> events = new ArrayList<>();

    public GPUCredit1() {
    }

    /** Credit is active in [startTime, expirationTime - 1] inclusive. */
    public void grantCredit(String id, int amount, int startTime, int expirationTime) {
        events.add(new Event(startTime, EventType.ADD, amount, expirationTime));
    }

    /** Deduct from credits at timestamp; use soonest-expiring first. */
    public void subtract(int amount, int timestamp) {
        events.add(new Event(timestamp, EventType.SUB, amount, 0));
    }

    public int getBalance(int timestamp) {
        List<Event> validEvens = new ArrayList<>();
        for (Event e: events) {
            if (e.timestamp <= timestamp) {
                validEvens.add(e);
            }
        }

        validEvens.sort((a, b)-> {
            if (a.timestamp == b.timestamp) {
                return a.type.ordinal() - b.type.ordinal();
            }
            return a.timestamp - b.timestamp;
        });

        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> (a[0] - b[0]));
        for (Event e : validEvens) {
            if (e.type == EventType.ADD) {
                queue.add(new int[] {e.endTime, e.amount});
            } else {
                int need = e.amount;
                int t = e.timestamp;
                while (need > 0 && !queue.isEmpty()) {
                    int[] cur = queue.poll();
                    int end = cur[0], amt = cur[1];
                    if (end <= t) continue;
                    if (amt > need) {
                        queue.add(new int[] {end, amt - need});
                        need = 0;
                    } else {
                        need -= amt;
                    }
                }
            }
        }

        while (!queue.isEmpty() && queue.peek()[0] <= timestamp) {
            queue.poll();
        }

        int valid = 0;
        while (!queue.isEmpty()) valid += queue.poll()[1];
        return valid;
    }

    public static void main(String[] args) {
        GPUCredit1 cs = new GPUCredit1();
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

        System.out.println("All checks passed.");
    }
}
