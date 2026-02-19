import java.util.*;

/**
 * DoorDash 面试题：Pattern Matching with Consecutive Ones
 * 
 * 问题描述：
 * 给定一个整数数组和一个模式字符串（包含 '0', '1', '?'），确定可以通过用 '0' 或 '1' 
 * 替换每个 '?' 形成的有效字符串组合的数量。
 * 
 * 数组 arr 指定必须出现的连续 '1' 段的确切长度。
 * 
 * 示例：
 * Input: pattern = "?0??10", arr = [1,2]
 * Output: 1
 * Explanation: "100110" 是唯一有效的字符串
 */
public class PatternMatch {
    
    /**
     * 主方法：计算有效的模式匹配数量
     * 
     * @param pattern 模式字符串，包含 '0', '1', '?'
     * @param arr 连续 '1' 段的长度数组
     * @return 有效组合的数量
     */
    public int countValidPatterns(String pattern, int[] arr) {
        // 记忆化搜索的缓存
        Map<String, Integer> memo = new HashMap<>();
        return dfs(pattern, 0, arr, 0, memo);
    }
    
    /**
     * DFS + 记忆化搜索
     * 
     * @param pattern 模式字符串
     * @param pos 当前在 pattern 中的位置
     * @param arr 连续 '1' 段的长度数组
     * @param arrIdx 当前需要匹配的数组索引
     * @param memo 记忆化缓存
     * @return 从当前状态开始的有效组合数
     */
    private int dfs(String pattern, int pos, int[] arr, int arrIdx, Map<String, Integer> memo) {
        // Base case 1: 已经匹配完所有的 arr 元素
        if (arrIdx == arr.length) {
            // 检查剩余的 pattern 是否都可以是 '0'
            for (int i = pos; i < pattern.length(); i++) {
                if (pattern.charAt(i) == '1') {
                    return 0;  // 剩余有固定的 '1'，不匹配
                }
            }
            return 1;  // 剩余都可以是 '0'，匹配成功
        }
        
        // Base case 2: pattern 遍历完但 arr 还有元素
        if (pos >= pattern.length()) {
            return 0;
        }
        
        // 记忆化键
        String key = pos + "," + arrIdx;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        
        int result = 0;
        char ch = pattern.charAt(pos);
        
        // 选项 1: 当前位置是 '0'（如果可能）
        if (ch == '0' || ch == '?') {
            result += dfs(pattern, pos + 1, arr, arrIdx, memo);
        }
        
        // 选项 2: 当前位置是 '1'，尝试匹配当前 arr[arrIdx] 长度的连续 '1'
        if (ch == '1' || ch == '?') {
            int requiredLen = arr[arrIdx];
            
            // 检查是否可以放置 requiredLen 个连续的 '1'
            if (canPlaceOnes(pattern, pos, requiredLen)) {
                int nextPos = pos + requiredLen;
                
                // 检查连续 '1' 后面的约束
                if (nextPos == pattern.length()) {
                    // 如果是最后一段，且是最后一个 arr 元素，则有效
                    if (arrIdx == arr.length - 1) {
                        result += 1;
                    }
                } else if (pattern.charAt(nextPos) == '0' || pattern.charAt(nextPos) == '?') {
                    // 连续 '1' 后面必须是 '0'，然后继续匹配
                    result += dfs(pattern, nextPos + 1, arr, arrIdx + 1, memo);
                }
                // 如果后面是固定的 '1'，则不能放置（会导致连续段更长）
            }
        }
        
        memo.put(key, result);
        return result;
    }
    
    /**
     * 检查从 pos 开始是否可以放置 len 个连续的 '1'
     * 
     * @param pattern 模式字符串
     * @param pos 起始位置
     * @param len 需要的长度
     * @return 是否可以放置
     */
    private boolean canPlaceOnes(String pattern, int pos, int len) {
        if (pos + len > pattern.length()) {
            return false;
        }
        
        for (int i = pos; i < pos + len; i++) {
            if (pattern.charAt(i) == '0') {
                return false;  // 有固定的 '0'，无法放置
            }
        }
        
        return true;
    }
    
    // 测试方法
    public static void main(String[] args) {
        PatternMatch solution = new PatternMatch();
        
        // 测试用例 1
        System.out.println("=== Test Case 1 ===");
        String pattern1 = "?0??10";
        int[] arr1 = {1, 2};
        int result1 = solution.countValidPatterns(pattern1, arr1);
        System.out.println("Input: pattern = \"" + pattern1 + "\", arr = " + Arrays.toString(arr1));
        System.out.println("Output: " + result1);
        System.out.println("Expected: 1");
        System.out.println("Pass: " + (result1 == 1 ? "✅" : "❌"));
        
        // 测试用例 2
        System.out.println("\n=== Test Case 2 ===");
        String pattern2 = "?00??";
        int[] arr2 = {1, 1};
        int result2 = solution.countValidPatterns(pattern2, arr2);
        System.out.println("Input: pattern = \"" + pattern2 + "\", arr = " + Arrays.toString(arr2));
        System.out.println("Output: " + result2);
        System.out.println("Expected: 2");
        System.out.println("Pass: " + (result2 == 2 ? "✅" : "❌"));
        
        // 测试用例 3
        System.out.println("\n=== Test Case 3 ===");
        String pattern3 = "10?11??0";
        int[] arr3 = {1, 3, 2};
        int result3 = solution.countValidPatterns(pattern3, arr3);
        System.out.println("Input: pattern = \"" + pattern3 + "\", arr = " + Arrays.toString(arr3));
        System.out.println("Output: " + result3);
        System.out.println("Expected: 0");
        System.out.println("Pass: " + (result3 == 0 ? "✅" : "❌"));
        
        // 额外测试用例
        System.out.println("\n=== Additional Test Cases ===");
        
        // Test 4: 简单情况
        String pattern4 = "?";
        int[] arr4 = {1};
        int result4 = solution.countValidPatterns(pattern4, arr4);
        System.out.println("Input: pattern = \"" + pattern4 + "\", arr = " + Arrays.toString(arr4));
        System.out.println("Output: " + result4 + " (Expected: 1) " + (result4 == 1 ? "✅" : "❌"));
        
        // Test 5: 多个 ?
        String pattern5 = "???";
        int[] arr5 = {1};
        int result5 = solution.countValidPatterns(pattern5, arr5);
        System.out.println("Input: pattern = \"" + pattern5 + "\", arr = " + Arrays.toString(arr5));
        System.out.println("Output: " + result5 + " (Expected: 4) " + (result5 == 4 ? "✅" : "❌"));
        // Explanation: "100", "010", "001", "001" -> actually "100", "010", "001" (3 positions)
        
        // Test 6: 固定模式
        String pattern6 = "1010";
        int[] arr6 = {1, 1};
        int result6 = solution.countValidPatterns(pattern6, arr6);
        System.out.println("Input: pattern = \"" + pattern6 + "\", arr = " + Arrays.toString(arr6));
        System.out.println("Output: " + result6 + " (Expected: 1) " + (result6 == 1 ? "✅" : "❌"));
        
        System.out.println("\n=== 所有测试完成 ===");
    }
}
