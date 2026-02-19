import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GPUCredit2 {

    class CreditEntry {
        int amount;
        int timestamp;
        int expiration;

        public CreditEntry(int amount, int timestamp, int expiration) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.expiration = expiration;
        }

        public boolean isValid(int time) {
            return timestamp <= time && time <= timestamp + expiration;
        }
    }

    Map<String, List<CreditEntry>> map = new HashMap<>();

    public static void main(String[] args) {
        GPUCredit2 gpu = new GPUCredit2();

        // Test 1
        gpu.addCredit("microsoft", 10, 10, 30);
        System.out.println(gpu.getBalance("microsoft", 0)); // null
        System.out.println(gpu.getBalance("microsoft", 10)); // 10
        System.out.println(gpu.getBalance("microsoft", 40)); // 10
        System.out.println(gpu.getBalance("microsoft", 41)); // null

        // Test 2
        gpu = new GPUCredit2();
        gpu.addCredit("amazon", 40, 10, 50);
        gpu.useCredit("amazon", 30, 30);
        System.out.println(gpu.getBalance("amazon", 12)); // 40 (修复后)
        gpu.addCredit("amazon", 20, 60, 10);
        System.out.println(gpu.getBalance("amazon", 60)); // 30
        System.out.println(gpu.getBalance("amazon", 61)); // 20
        System.out.println(gpu.getBalance("amazon", 70)); // 20
        System.out.println(gpu.getBalance("amazon", 71)); // null

        // Edge case
        gpu = new GPUCredit2();
        gpu.addCredit("openai", 10, 10, 30);
        gpu.useCredit("openai", 10, 100000000); // overuse
        System.out.println(gpu.getBalance("openai", 10)); // null
        gpu.addCredit("openai", 10, 20, 10);
        System.out.println(gpu.getBalance("openai", 20)); // 10
    }

    public void addCredit(String creditId, int amount, int timestamp, int expiration) {
        map.computeIfAbsent(creditId, k -> new ArrayList<>());
        map.get(creditId).add(new CreditEntry(amount, timestamp, expiration));
    }

    public boolean useCredit(String creditId, int timestamp, int amount) {
        if (!map.containsKey(creditId))
            return false;
        List<CreditEntry> entries = map.get(creditId);
        List<CreditEntry> newEntries = new ArrayList<>();
        entries.sort((a, b) -> (a.timestamp + a.expiration - b.timestamp - b.expiration));

        Iterator<CreditEntry> it = entries.iterator();
        while (it.hasNext() && amount > 0) {
            CreditEntry cur = it.next();
            if (!cur.isValid(timestamp))
                continue;
            if (cur.amount <= amount) {
                it.remove();
                if (cur.timestamp < timestamp) {
                    newEntries.add(new CreditEntry(cur.amount, cur.timestamp, timestamp - cur.timestamp - 1));  // FIX: cur.amount
                }
                amount -= cur.amount;
            } else {
                it.remove();
                if (cur.timestamp < timestamp) {
                    newEntries.add(new CreditEntry(cur.amount, cur.timestamp, timestamp - cur.timestamp - 1));  // FIX: cur.amount
                }
                newEntries.add(
                        new CreditEntry(cur.amount - amount, timestamp, cur.timestamp + cur.expiration - timestamp));
                amount = 0;
            }
        }
        if (amount > 0)
            return false;
        entries.addAll(newEntries);
        return true;
    }

    public int getBalance(String creditId, int timestamp) {
        List<CreditEntry> entries = map.get(creditId);
        int total = 0;
        for (CreditEntry entry : entries) {
            if (entry.isValid(timestamp)) {
                total += entry.amount;
            }
        }
        return total;

    }
}