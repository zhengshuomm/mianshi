package anthropic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Simpler office manager without double-pay (worker_management.py part 3). */
public class OfficeManagerLite {

    static class Worker {
        String position;
        int compensation;
        int enterTime = -1;
        boolean isInOffice;
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
        int totalSalary = 0;
        for (int[] s : worker.sessions) {
            int overlapStart = Math.max(s[0], startTimestamp);
            int overlapEnd = Math.min(s[1], endTimestamp);
            if (overlapStart < overlapEnd) {
                totalSalary += (overlapEnd - overlapStart) * s[2];
            }
        }
        return totalSalary;
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
}
