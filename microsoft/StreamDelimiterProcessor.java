import java.util.ArrayList;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * 用定长窗口（queue size = stopToken.length()）处理流式 stop token：
 * 窗口满就检查是否等于 token，相等则停止；否则把最左字符输出，再塞入新字符。
 * 不需要 KMP，且天然支持 token 跨 chunk。
 */
public class StreamDelimiterProcessor {
    private final String stopToken;
    private final int L;
    private final Deque<Character> window = new ArrayDeque<>();
    private final StringBuilder output = new StringBuilder();
    private boolean stopped = false;

    public StreamDelimiterProcessor(String stopToken) {
        this.stopToken = stopToken;
        this.L = stopToken.length();
    }

    /** 处理单个 chunk，更新内部状态（window + output） */
    public void process(String chunk) {
        if (stopped || chunk == null) return;
        for (char c : chunk.toCharArray()) {
            if (window.size() == L) {
                if (windowEqualsToken()) {
                    stopped = true;
                    return;
                }
                output.append(window.pollFirst());
            }
            window.addLast(c);
            if (window.size() == L && windowEqualsToken()) {
                stopped = true;
                return;
            }
        }
    }

    /**
     * 输入 List<String> chunks。
     * 若遇到 stopToken：按原始 chunk 边界拆分 token 之前的内容，返回 list。
     * 若没遇到：返回原始 chunks。
     */
    public List<String> process(List<String> chunks) {
        if (chunks == null) return List.of();
        for (String chunk : chunks) {
            process(chunk);
            if (stopped) break;
        }
        if (!stopped) {
            flushRemaining();
            return new ArrayList<>(chunks);
        }
        String fullOutput = output.toString();
        List<String> result = new ArrayList<>();
        int pos = 0;
        for (String chunk : chunks) {
            int take = Math.min(chunk.length(), fullOutput.length() - pos);
            if (take <= 0) break;
            result.add(fullOutput.substring(pos, pos + take));
            pos += take;
        }
        return result;
    }

    /** 流结束且未遇到 token 时，把窗口里剩余字符也算进输出 */
    public void flushRemaining() {
        while (!window.isEmpty()) output.append(window.pollFirst());
    }

    private boolean windowEqualsToken() {
        int i = 0;
        for (char c : window) {
            if (c != stopToken.charAt(i++)) return false;
        }
        return true;
    }

    public String getOutput() { return output.toString(); }
    public boolean isStopped() { return stopped; }

    public static void main(String[] args) {
        // token 在末尾：["aabaabaac"] -> 遇到 aabaac 停止，输出 aab
        StreamDelimiterProcessor p = new StreamDelimiterProcessor("aabaac");
        System.out.println("Single chunk: " + p.process(List.of("aabaabaac")));

        // 没有 token：整段输出
        // StreamDelimiterProcessor p2 = new StreamDelimiterProcessor("aabaac");
        // System.out.println("No token: " + p2.process(List.of("aabaabaaa")));

        // 跨 chunk：chunks = [aab, aab, aac]，拼起来是 aabaabaac，输出按 chunk 对齐的 list
        List<String> chunks = Arrays.asList("aab", "aab", "aaa");
        StreamDelimiterProcessor p3 = new StreamDelimiterProcessor("aabaac");
        System.out.println("Cross-chunk: " + p3.process(chunks));  // 无 token 时 [aab, aab, aaa]

        chunks = Arrays.asList("aab", "aaaab", "aac");
        StreamDelimiterProcessor p2 = new StreamDelimiterProcessor("aabaac");
        System.out.println("Has token: " + p2.process(chunks));  // [aab, "aa"]
    }
}
