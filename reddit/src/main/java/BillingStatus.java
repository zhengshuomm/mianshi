import java.util.*;

public class BillingStatus {
    private Map<String, Long> values;
    private List<Map<String, Long>> history;
    private Stack<Map<String, Long>> undone;
    
    public BillingStatus() {
        values = new HashMap<>();
        history = new ArrayList<>();
        undone = new Stack<>();
    }
    
    public void processTransaction(Map<String, Object> tx) {
        if (tx.containsKey("undo_last") && (Boolean) tx.get("undo_last")) {
            undo();
            return;
        }
        if (tx.containsKey("redo_last") && (Boolean) tx.get("redo_last")) {
            redo();
            return;
        }
        
        Map<String, Long> changes = new HashMap<>();
        boolean overwrite = tx.containsKey("overwrite") && (Boolean) tx.get("overwrite");
        
        for (Map.Entry<String, Object> e : tx.entrySet()) {
            String k = e.getKey();
            if (k.equals("user_id") || k.equals("transaction_timestamp") || 
                k.equals("overwrite") || k.equals("undo_last") || k.equals("redo_last")) continue;
            if (e.getValue() instanceof Number) {
                long v = ((Number) e.getValue()).longValue();
                changes.put(k, v);
                if (overwrite) values.put(k, v);
                else values.put(k, values.getOrDefault(k, 0L) + v);
            }
        }
        
        if (!changes.isEmpty()) {
            history.add(changes);
            undone.clear();
        }
    }
    
    private void undo() {
        if (history.isEmpty()) return;
        Map<String, Long> last = history.remove(history.size() - 1);
        undone.push(last);
        for (String k : last.keySet()) {
            values.put(k, Math.max(0, values.getOrDefault(k, 0L) - last.get(k)));
        }
    }
    
    private void redo() {
        if (undone.isEmpty()) return;
        Map<String, Long> tx = undone.pop();
        history.add(tx);
        for (String k : tx.keySet()) {
            values.put(k, values.getOrDefault(k, 0L) + tx.get(k));
        }
    }
    
    public static Map<Integer, BillingStatus> processTransactions(Map<String, Map<String, Object>> transactions) {
        List<Map.Entry<String, Map<String, Object>>> sorted = new ArrayList<>(transactions.entrySet());
        sorted.sort(Comparator.comparingLong(e -> 
            ((Number) e.getValue().getOrDefault("transaction_timestamp", 0L)).longValue()));
        
        Map<Integer, BillingStatus> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : sorted) {
            int userId = ((Number) e.getValue().get("user_id")).intValue();
            result.putIfAbsent(userId, new BillingStatus());
            result.get(userId).processTransaction(e.getValue());
        }
        return result;
    }
    
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        for (String k : values.keySet()) {
            parts.add("'" + k + "'=" + values.get(k));
        }
        return "BillingStatus(" + String.join(", ", parts) + ")";
    }
}
