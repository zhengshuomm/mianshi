import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class KVStore {

    Map<String, String> map;
    public KVStore() {
        this.map = new HashMap<>();
        this.stack = new Stack<>();
    }

    public String get(String key){
        if (!stack.isEmpty()) {
            for (int i = stack.size()- 1 ; i >=0 ; i --) {
                if (stack.get(i).map.containsKey(key)) {
                    return "";
                }
            }
        }
        return map.get(key);
    }

    public void set(String key, String value) {
        if (!stack.isEmpty()) {
            stack.peek().map.put(key, value);
        }
        map.put(key, value);
    }

    public void delete(String key) {
        if (!stack.isEmpty()) {
            KVStore kv = stack.peek();
            kv.map.remove(key);
        }
        map.remove(key);
    }

    public void begin(){
        stack.push(new KVStore());
    }

    // commit to parent or to db??
    public void commit() {
        KVStore kv = stack.pop();
        for (String k : kv.map.keySet()) {
            this.map.put(k, kv.map.get(k));
        }
    }

    public void rollback() {
        stack.pop();
    }

    Stack<KVStore> stack;

    public static void main(String[] args) {
        Stack<String> s = new Stack<>();
        s.push("aa");
        s.push("bb");
        System.out.println(s.get(0));
//        Reentrantlock lock =  new Reentrantlock();
    }
}
