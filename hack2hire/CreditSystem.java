import java.util.*;

class Grant {
    int amount;
    int startTime;
    int expirationTime;

    Grant(int amount, int startTime, int expirationTime) {
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
    private List<Grant> grants = new ArrayList<>();
    private List<Subtraction> subtractions = new ArrayList<>();

    public void grantCredit(int amount, int startTime, int expirationTime) {
        grants.add(new Grant(amount, startTime, expirationTime));
    }

    public void subtract(int amount, int timestamp) {
        subtractions.add(new Subtraction(timestamp, amount));
    }

    public int getBalance(int timestamp) {
        List<Integer> remaining = new ArrayList<>();
        for (Grant g : grants) {
            remaining.add(g.amount);
        }

        List<Subtraction> relevantSubs = new ArrayList<>();
        for (Subtraction sub : subtractions) {
            if (sub.timestamp <= timestamp) {
                relevantSubs.add(sub);
            }
        }
        relevantSubs.sort(Comparator.comparingInt(s -> s.timestamp));

        for (Subtraction sub : relevantSubs) {
            int toSubtract = sub.amount;

            List<Integer> activeIndices = new ArrayList<>();
            for (int i = 0; i < grants.size(); i++) {
                if (grants.get(i).isActiveAt(sub.timestamp)) {
                    activeIndices.add(i);
                }
            }
            activeIndices.sort(Comparator.comparingInt(i -> grants.get(i).expirationTime));

            for (int i : activeIndices) {
                if (toSubtract <= 0) break;
                int available = remaining.get(i);
                int deduct = Math.min(available, toSubtract);
                remaining.set(i, available - deduct);
                toSubtract -= deduct;
            }
        }

        int total = 0;
        for (int i = 0; i < grants.size(); i++) {
            if (grants.get(i).isActiveAt(timestamp)) {
                total += remaining.get(i);
            }
        }
        return total;
    }

    public static void main(String[] args) {
        CreditSystem cs = new CreditSystem();

        cs.grantCredit(3, 10, 60);
        System.out.println("getBalance(10) = " + cs.getBalance(10)); // 3

        cs.grantCredit(2, 20, 40);
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
