import java.util.*;

class Grant {
    String id;
    int amount;
    int startTime;
    int expirationTime;
    
    Grant(String id, int amount, int startTime, int expirationTime) {
        this.id = id;
        this.amount = amount;
        this.startTime = startTime;
        this.expirationTime = expirationTime;
    }
    
    boolean isActiveAt(int time) {
        return time >= startTime && time < expirationTime;
    }
}

class Subtraction {
    int timestamp;
    int amount;
    
    Subtraction(int timestamp, int amount) {
        this.timestamp = timestamp;
        this.amount = amount;
    }
}

public class CreditSystem {
    private Map<String, Grant> grants = new HashMap<>();
    private List<Subtraction> subtractions = new ArrayList<>();
    
    public void grantCredit(String id, int amount, int startTime, int expirationTime) {
        grants.put(id, new Grant(id, amount, startTime, expirationTime));
    }
    
    public void subtract(int amount, int timestamp) {
        subtractions.add(new Subtraction(timestamp, amount));
    }
    
    public int getBalance(int timestamp) {
        // 为每个 grant 维护剩余量
        Map<String, Integer> remaining = new HashMap<>();
        for (Grant g : grants.values()) {
            remaining.put(g.id, g.amount);
        }
        
        // 找到所有 <= timestamp 的 subtract 事件，按时间排序
        List<Subtraction> relevantSubs = new ArrayList<>();
        for (Subtraction sub : subtractions) {
            if (sub.timestamp <= timestamp) {
                relevantSubs.add(sub);
            }
        }
        relevantSubs.sort(Comparator.comparingInt(s -> s.timestamp));
        
        // 按顺序模拟每个 subtract
        for (Subtraction sub : relevantSubs) {
            int toSubtract = sub.amount;
            
            // 找到在该时间点活跃的 grants，按过期时间排序（最早过期的优先）
            List<Grant> activeGrants = new ArrayList<>();
            for (Grant g : grants.values()) {
                if (g.isActiveAt(sub.timestamp)) {
                    activeGrants.add(g);
                }
            }
            activeGrants.sort(Comparator.comparingInt(g -> g.expirationTime));
            
            // 从最早过期的开始扣除
            for (Grant g : activeGrants) {
                if (toSubtract <= 0) break;
                int available = remaining.get(g.id);
                int deduct = Math.min(available, toSubtract);
                remaining.put(g.id, available - deduct);
                toSubtract -= deduct;
            }
        }
        
        // 计算在 timestamp 活跃的所有 grants 的剩余总量
        int total = 0;
        for (Grant g : grants.values()) {
            if (g.isActiveAt(timestamp)) {
                total += remaining.get(g.id);
            }
        }
        return total;
    }
    
    public static void main(String[] args) {
        CreditSystem cs = new CreditSystem();
        
        cs.grantCredit("a", 3, 10, 60);
        System.out.println("getBalance(10) = " + cs.getBalance(10)); // 3
        
        cs.grantCredit("b", 2, 20, 40);
        cs.subtract(1, 30);
        cs.subtract(3, 50);
        
        System.out.println("getBalance(10) = " + cs.getBalance(10)); // 3
        System.out.println("getBalance(20) = " + cs.getBalance(20)); // 5
        System.out.println("getBalance(30) = " + cs.getBalance(30)); // 4
        System.out.println("getBalance(35) = " + cs.getBalance(35)); // 4
        System.out.println("getBalance(40) = " + cs.getBalance(40)); // 3
        System.out.println("getBalance(50) = " + cs.getBalance(50)); // 0
    }
}
