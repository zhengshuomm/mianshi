package anthropic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Office payroll with double-pay windows (worker_management.py lines 1–108). */
public class OfficeManager {

    static class Worker {
        String position;
        int compensation;
        int enterTime = -1;
        boolean isInOffice;
        /** Each session: start, end, rate */
        final List<int[]> sessions = new ArrayList<>();
        int currentTimeInPosition;
        PendingPromo pendingPromo;
    }

    static class PendingPromo {
        String newPosition;
        int newCompensation;
        int startTimestamp;

        PendingPromo(String np, int nc, int st) {
            this.newPosition = np;
            this.newCompensation = nc;
            this.startTimestamp = st;
        }
    }

    final Map<String, Worker> workers = new HashMap<>();
    final List<int[]> doublePaidIntervals = new ArrayList<>();

    public boolean addWorker(String workerId, String position, int compensation) {
        if (workers.containsKey(workerId)) return false;
        Worker w = new Worker();
        w.position = position;
        w.compensation = compensation;
        workers.put(workerId, w);
        return true;
    }

    public String registerWorker(String workerId, int timestamp) {
        Worker worker = workers.get(workerId);
        if (worker == null) return "invalid_request";
        if (!worker.isInOffice) {
            if (worker.pendingPromo != null && timestamp >= worker.pendingPromo.startTimestamp) {
                worker.position = worker.pendingPromo.newPosition;
                worker.compensation = worker.pendingPromo.newCompensation;
                worker.currentTimeInPosition = 0;
                worker.pendingPromo = null;
            }
            worker.enterTime = timestamp;
            worker.isInOffice = true;
        } else {
            int duration = timestamp - worker.enterTime;
            worker.currentTimeInPosition += duration;
            worker.sessions.add(new int[] {worker.enterTime, timestamp, worker.compensation});
            worker.isInOffice = false;
        }
        return "registered";
    }

    public String promote(String workerId, String newPosition, String newCompensation, int startTimestamp) {
        Worker worker = workers.get(workerId);
        if (worker == null || worker.pendingPromo != null) return "invalid_request";
        worker.pendingPromo = new PendingPromo(newPosition, Integer.parseInt(newCompensation), startTimestamp);
        return "success";
    }

    public int calcSalary(String workerId, int startTimestamp, int endTimestamp) {
        Worker worker = workers.get(workerId);
        if (worker == null) return -1;
        long totalSalary = 0;
        for (int[] s : worker.sessions) {
            int sStart = s[0];
            int sEnd = s[1];
            int rate = s[2];
            int effectiveStart = Math.max(sStart, startTimestamp);
            int effectiveEnd = Math.min(sEnd, endTimestamp);
            if (effectiveStart < effectiveEnd) {
                int duration = effectiveEnd - effectiveStart;
                totalSalary += (long) duration * rate;
                for (int[] d : doublePaidIntervals) {
                    int overlapS = Math.max(effectiveStart, d[0]);
                    int overlapE = Math.min(effectiveEnd, d[1]);
                    if (overlapS < overlapE) {
                        totalSalary += (long) (overlapE - overlapS) * rate;
                    }
                }
            }
        }
        return (int) totalSalary;
    }

    public int get(String workerId) {
        Worker w = workers.get(workerId);
        if (w == null) return -1;
        int t = 0;
        for (int[] s : w.sessions) {
            t += s[1] - s[0];
        }
        return t;
    }

    public String topNWorkers(int n, String position) {
        List<Map.Entry<String, Worker>> filtered =
                workers.entrySet().stream()
                        .filter(e -> position.equals(e.getValue().position))
                        .sorted(
                                Comparator.<Map.Entry<String, Worker>>comparingInt(
                                                e -> -e.getValue().currentTimeInPosition)
                                        .thenComparing(Map.Entry::getKey))
                        .collect(Collectors.toList());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(n, filtered.size()); i++) {
            Map.Entry<String, Worker> e = filtered.get(i);
            parts.add(e.getKey() + "(" + e.getValue().currentTimeInPosition + ")");
        }
        return String.join(", ", parts);
    }

    public void setDoublePaid(int startTimestamp, int endTimestamp) {
        doublePaidIntervals.add(new int[] {startTimestamp, endTimestamp});
        doublePaidIntervals.sort(Comparator.comparingInt(a -> a[0]));
        if (doublePaidIntervals.isEmpty()) return;
        List<int[]> merged = new ArrayList<>();
        int cs = doublePaidIntervals.get(0)[0];
        int ce = doublePaidIntervals.get(0)[1];
        for (int i = 1; i < doublePaidIntervals.size(); i++) {
            int ns = doublePaidIntervals.get(i)[0];
            int ne = doublePaidIntervals.get(i)[1];
            if (ns <= ce) {
                ce = Math.max(ce, ne);
            } else {
                merged.add(new int[] {cs, ce});
                cs = ns;
                ce = ne;
            }
        }
        merged.add(new int[] {cs, ce});
        doublePaidIntervals.clear();
        doublePaidIntervals.addAll(merged);
    }
}
