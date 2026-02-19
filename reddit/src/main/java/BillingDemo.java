import java.util.*;

public class BillingDemo {
    public static void main(String[] args) {
        // Part 1
        System.out.println("=== Part 1 ===");
        Map<String, Map<String, Object>> transactions1 = new LinkedHashMap<>();
        transactions1.put("ff8bc1c2-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000001L
        ));
        transactions1.put("ff8bc2e4-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000002L
        ));
        transactions1.put("ff8bc4ec-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "payment_pennies", 500, "transaction_timestamp", 1500000003L
        ));
        transactions1.put("fv24z4ec-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "payment_pennies", 500, "transaction_timestamp", 1500000004L
        ));
        
        Map<Integer, BillingStatus> result1 = BillingStatus.processTransactions(transactions1);
        System.out.println(result1);
        
        // Part 2
        System.out.println("\n=== Part 2 ===");
        Map<String, Map<String, Object>> transactions2 = new LinkedHashMap<>();
        transactions2.put("ff8ba98a-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000001L, "overwrite", false
        ));
        transactions2.put("ff8bad4a-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000004L
        ));
        transactions2.put("ff8baea8-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "payment_pennies", 600, "transaction_timestamp", 1500000007L, "overwrite", false
        ));
        transactions2.put("ff8bb4ac-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000002L, "overwrite", false
        ));
        transactions2.put("ff8bb600-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "ad_delivery_pennies", 1000, "payment_pennies", 500, "transaction_timestamp", 1500000003L, "overwrite", false
        ));
        transactions2.put("ff8bb89e-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "payment_pennies", 2000, "transaction_timestamp", 1500000005L, "overwrite", true
        ));
        transactions2.put("ff8bb9c0-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "payment_pennies", 500, "transaction_timestamp", 1500000003L, "overwrite", false
        ));
        transactions2.put("ff8bbf74-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "payment_pennies", 500, "transaction_timestamp", 1500000004L, "overwrite", true
        ));
        transactions2.put("ff8bc0a0-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000001L
        ));
        transactions2.put("ff8bc1c2-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 2, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000002L
        ));
        transactions2.put("ff923488-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "payment_pennies", 100, "transaction_timestamp", 1500000013L
        ));
        
        Map<Integer, BillingStatus> result2 = BillingStatus.processTransactions(transactions2);
        System.out.println(result2);
        
        // Part 3
        System.out.println("\n=== Part 3 ===");
        Map<String, Map<String, Object>> transactions3 = new LinkedHashMap<>();
        transactions3.put("ff8bc1c2-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "transaction_timestamp", 1500000001L
        ));
        transactions3.put("ff8bc2e4-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "undo_last", true, "transaction_timestamp", 1500000002L
        ));
        transactions3.put("ff8bc4ec-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "payment_pennies", 500, "transaction_timestamp", 1500000003L
        ));
        transactions3.put("fv24z4ec-8d45-11e9-bc42-526af7764f64", Map.of(
            "user_id", 1, "ad_delivery_pennies", 1000, "payment_pennies", 500, "transaction_timestamp", 1500000004L
        ));
        
        Map<Integer, BillingStatus> result3 = BillingStatus.processTransactions(transactions3);
        System.out.println(result3);
    }
}
