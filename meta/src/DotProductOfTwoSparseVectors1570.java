import java.util.HashMap;
import java.util.Map;

public class DotProductOfTwoSparseVectors1570 {
    private Map<Integer, Integer> v;
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }

    DotProductOfTwoSparseVectors1570(int[] nums) {
        v = new HashMap<>();
        for (int i = 0 ; i < nums.length ; i ++) {
            if (nums[i] != 0 ) {
                v.put(i, nums[i]);
            }
        }
    }

    public int doProduct(DotProductOfTwoSparseVectors1570 vec) {
        int res = 0;
        if (v.size() > vec.v.size()) {
            Map<Integer, Integer> t = v;
            v = vec.v;
            vec.v = t;
        }

        for (Map.Entry<Integer, Integer> entry : v.entrySet()) {
            int i = entry.getKey(), val = entry.getValue();
            res += val * vec.v.getOrDefault(i, 0);
        }
        return res;
    }
}