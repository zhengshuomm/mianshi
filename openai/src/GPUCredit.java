import java.util.*;
public class GPUCredit {

    class CreditEntry {
        int amount;
        int timestamp;
        int expiration;

        CreditEntry(int amount, int timestamp, int expiration) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.expiration = expiration;
        }

        boolean isValidAt(int time) {
            return time >=timestamp && time <= timestamp + expiration;
        }
    }

    public final Map<String, List<CreditEntry>> map = new HashMap<>();

    // 添加积分：指定用户、数量、生效时间和有效期（单位：时间戳）
    public void addCredit(String creditId, int amount, int timestamp, int expiration) {
        map.computeIfAbsent(creditId, k -> new ArrayList<>());
        map.get(creditId).add(new CreditEntry(amount, timestamp, expiration));
    }

    // 在指定时间消费指定数量的积分，若成功返回 true，余额不足返回 false
    public boolean useCredit(String creditId, int timestamp, int amount) {
        if (!map.containsKey(creditId)) return false;

        List<CreditEntry> credits = map.get(creditId);
        credits.sort(Comparator.comparingInt(e -> e.timestamp + e.expiration));

        int available = 0;
        for (CreditEntry entry : credits) {
            if (entry.isValidAt(timestamp)) {
                available += entry.amount;
            }
        }

        // need to delete all credit as well
//        if (available < amount) return false;

        int remainingToDeduct = amount;
        for (CreditEntry entry : credits) {
            if (!entry.isValidAt(timestamp)) continue;
            int used = Math.min(entry.amount, remainingToDeduct);
            entry.amount -= used;
            remainingToDeduct -=used;
            if (remainingToDeduct == 0) break;
        }
        return true;

    }

    // 查询指定用户在指定时间点的有效余额；如果没有任何有效积分，则返回 null
    public Integer getBalance(String creditId, int timestamp) {
        if (!map.containsKey(creditId)) return null;
        int total = 0;
//        boolean valid = false;
        for (CreditEntry entry : map.get(creditId)) {
            if (entry.isValidAt(timestamp)) {
                total += entry.amount;
//                valid = true;
            }
        }
        return total == 0? null : total;
    }

    public static void main(String[] args) {
        GPUCredit gpu = new GPUCredit();

        // Test 1
//        gpu.addCredit("microsoft", 10, 10, 30);
//        System.out.println(gpu.getBalance("microsoft", 0));    // null
//        System.out.println(gpu.getBalance("microsoft", 10));   // 10
//        System.out.println(gpu.getBalance("microsoft", 40));   // 10
//        System.out.println(gpu.getBalance("microsoft", 41));   // null

        // Test 2
        gpu = new GPUCredit();
        gpu.addCredit("amazon", 40, 10, 50);
        gpu.useCredit("amazon", 30, 30);
        System.out.println(gpu.getBalance("amazon", 12));      // 10
        gpu.addCredit("amazon", 20, 60, 10);
        System.out.println(gpu.getBalance("amazon", 60));      // 30
        System.out.println(gpu.getBalance("amazon", 61));      // 20
        System.out.println(gpu.getBalance("amazon", 70));      // 20
        System.out.println(gpu.getBalance("amazon", 71));      // null

        // Edge case
        gpu = new GPUCredit();
        gpu.addCredit("openai", 10, 10, 30);
        gpu.useCredit("openai", 10, 100000000);  // overuse
        System.out.println(gpu.getBalance("openai", 10));      // null
        gpu.addCredit("openai", 10, 20, 10);
        System.out.println(gpu.getBalance("openai", 20));      // 10
    }
}


//public class GPUCredit {
//
//
//    // <start time, amount>, <end time + 1, -amount>
//    public final Map<String, TreeMap<Integer, Integer>> creditMap = new HashMap<>();
//
//    // 添加积分：指定用户、数量、生效时间和有效期（单位：时间戳）
//    public void addCredit(String creditId, int amount, int timestamp, int expiration) {
//        creditMap.computeIfAbsent(creditId, k -> new TreeMap<>());
////        creditMap.get(creditId).put(new CreditEntry(amount, timestamp, expiration));
//        Map<Integer, Integer> map = creditMap.get(creditId);
//        map.put(timestamp, map.getOrDefault(timestamp, 0) + amount);
//        map.put(timestamp + expiration + 1, map.getOrDefault(timestamp + expiration + 1, -amount));
//    }
//
//    // 在指定时间消费指定数量的积分，若成功返回 true，余额不足返回 false
//    public boolean useCredit(String creditId, int timestamp, int amount) {
//        if (!creditMap.containsKey(creditId)) return false;
//
//        TreeMap<Integer, Integer> map = creditMap.get(creditId);
//        map.put(timestamp, map.getOrDefault(timestamp, 0) - amount);
//        return true;
//
//    }
//
//    // 查询指定用户在指定时间点的有效余额；如果没有任何有效积分，则返回 null
//    public Integer getBalance(String creditId, int timestamp) {
//        if (!creditMap.containsKey(creditId)) return null;
//        int total = 0;
//
//        TreeMap<Integer, Integer> map = creditMap.get(creditId);
//        for (Integer time : map.keySet()) {
//            int val = map.get(time);
//            if (time <= timestamp) {
//                total += val;
//                if (total < 0) total = 0;
//            } else break;
//        }
//        return total == 0? null : total;
//    }
//
//    public static void main(String[] args) {
//        GPUCredit gpu = new GPUCredit();
//
//        // Test 1
////        gpu.addCredit("microsoft", 10, 10, 30);
////        System.out.println(gpu.getBalance("microsoft", 0));    // null
////        System.out.println(gpu.getBalance("microsoft", 10));   // 10
////        System.out.println(gpu.getBalance("microsoft", 40));   // 10
////        System.out.println(gpu.getBalance("microsoft", 41));   // null
//
//        // Test 2
//        gpu = new GPUCredit();
//        gpu.addCredit("amazon", 40, 10, 50);
//        gpu.useCredit("amazon", 30, 30);
//        System.out.println(gpu.getBalance("amazon", 12));      // 10
//        gpu.addCredit("amazon", 20, 60, 10);
//        System.out.println(gpu.getBalance("amazon", 60));      // 30
//        System.out.println(gpu.getBalance("amazon", 61));      // 20
//        System.out.println(gpu.getBalance("amazon", 70));      // 20
//        System.out.println(gpu.getBalance("amazon", 71));      // null
//
//        // Edge case
//        gpu = new GPUCredit();
//        gpu.addCredit("openai", 10, 10, 30);
//        gpu.useCredit("openai", 10, 100000000);  // overuse
//        System.out.println(gpu.getBalance("openai", 10));      // null
//        gpu.addCredit("openai", 10, 20, 10);
//        System.out.println(gpu.getBalance("openai", 20));      // 10
//    }
//}
