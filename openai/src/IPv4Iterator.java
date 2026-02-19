import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IPv4 Address Iterator
 *
 * Part 1: Forward — 从起始 IP 递增到 255.255.255.255
 * Part 2: Backward — 从起始 IP 递减到 0.0.0.0，reverse=true
 * Part 3: CIDR — 支持 "a.b.c.d/prefix"，只在 CIDR 范围内迭代
 * Part 4: Faster — step 步长、nextBatch 批量、CIDR 解析缓存
 */
public class IPv4Iterator implements Iterator<String>, Iterable<String> {

    private long current;
    private final boolean reverse;
    private final long rangeMin;
    private final long rangeMax;
    private final int step;
    private static final long MAX_IP = 0xFFFFFFFFL;

    /** 同一网段多次创建迭代器时复用 rangeMin/rangeMax，减少重复计算 */
    private static final Map<String, long[]> CIDR_CACHE = new ConcurrentHashMap<>();

    public IPv4Iterator(String startIp) {
        this(startIp, false, 1);
    }

    public IPv4Iterator(String startIp, boolean reverse) {
        this(startIp, reverse, 1);
    }

    public IPv4Iterator(String startIp, boolean reverse, int step) {
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1");
        }
        long start;
        long rMin;
        long rMax;
        if (startIp.contains("/")) {
            int slash = startIp.indexOf("/");
            String ipPart = startIp.substring(0, slash).trim();
            int prefixLen = Integer.parseInt(startIp.substring(slash + 1).trim());
            if (prefixLen < 0 || prefixLen > 32) {
                throw new IllegalArgumentException("Invalid prefix length: " + prefixLen);
            }
            start = parse(ipPart);
            int suffixBits = 32 - prefixLen;
            long mask = (1L << suffixBits) - 1;
            long base = start & (~mask);
            String cacheKey = toStr(base) + "/" + prefixLen;
            long[] cached = CIDR_CACHE.get(cacheKey);
            if (cached != null) {
                rMin = cached[0];
                rMax = cached[1];
            } else {
                rMin = base;
                rMax = base + mask;
                CIDR_CACHE.put(cacheKey, new long[]{rMin, rMax});
            }
        } else {
            start = parse(startIp);
            rMin = 0;
            rMax = MAX_IP;
        }
        this.current = start;
        this.reverse = reverse;
        this.rangeMin = rMin;
        this.rangeMax = rMax;
        this.step = step;
    }

    private static long parse(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4: " + ip);
        }
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int octet = Integer.parseInt(parts[i].trim());
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid octet: " + octet);
            }
            v = (v << 8) | (octet & 0xFF);
        }
        return v;
    }

    private static String toStr(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    @Override
    public boolean hasNext() {
        return reverse ? current >= rangeMin : current <= rangeMax;
    }

    @Override
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException(reverse ? "Past range min" : "Past range max");
        }
        String s = toStr(current);
        if (reverse) current -= step;
        else current += step;
        return s;
    }

    /**
     * 批量返回最多 size 个 IP，减少调用次数，适合大批量处理。
     */
    public List<String> nextBatch(int size) {
        if (size <= 0) return new ArrayList<>();
        List<String> batch = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size && hasNext(); i++) {
            batch.add(next());
        }
        return batch;
    }

    @Override
    public Iterator<String> iterator() {
        return this;
    }

    private static List<String> toList(IPv4Iterator ip) {
        List<String> out = new ArrayList<>();
        for (String s : ip) out.add(s);
        return out;
    }

    public static void main(String[] args) {
        // Test Case 1: /32 (single IP)
        List<String> t1 = toList(new IPv4Iterator("192.168.1.100/32"));
        if (!t1.equals(List.of("192.168.1.100"))) throw new AssertionError("Test 1: " + t1);

        // Test Case 2: /31 (2 IPs)
        List<String> t2 = toList(new IPv4Iterator("10.0.0.0/31"));
        if (!t2.equals(List.of("10.0.0.0", "10.0.0.1"))) throw new AssertionError("Test 2: " + t2);

        // Test Case 3: /29 forward (8 IPs)
        List<String> t3 = toList(new IPv4Iterator("172.16.0.0/29"));
        if (t3.size() != 8 || !"172.16.0.7".equals(t3.get(7))) throw new AssertionError("Test 3: size=" + t3.size() + " last=" + (t3.isEmpty() ? "" : t3.get(t3.size() - 1)));

        // Test Case 4: /29 reverse
        List<String> t4 = toList(new IPv4Iterator("172.16.0.7/29", true));
        if (!"172.16.0.7".equals(t4.get(0)) || !"172.16.0.0".equals(t4.get(t4.size() - 1))) throw new AssertionError("Test 4: " + t4);

        // Test Case 5: Starting in the middle of a block (192.168.1.0-7, start at 5 -> 5,6,7)
        List<String> t5 = toList(new IPv4Iterator("192.168.1.5/29"));
        if (!"192.168.1.5".equals(t5.get(0)) || !"192.168.1.7".equals(t5.get(t5.size() - 1))) throw new AssertionError("Test 5: " + t5);

        // Part 4: step — 每 2 个取一个
        List<String> t6 = toList(new IPv4Iterator("10.0.0.0/31", false, 2));
        if (!t6.equals(List.of("10.0.0.0"))) throw new AssertionError("Test 6 step: " + t6);
        List<String> t7 = toList(new IPv4Iterator("172.16.0.0/29", false, 2));
        if (!t7.equals(List.of("172.16.0.0", "172.16.0.2", "172.16.0.4", "172.16.0.6"))) throw new AssertionError("Test 7 step: " + t7);

        // Part 4: nextBatch
        IPv4Iterator it = new IPv4Iterator("192.168.1.0/30");
        List<String> b1 = it.nextBatch(2);
        if (!b1.equals(List.of("192.168.1.0", "192.168.1.1"))) throw new AssertionError("Test 8 batch: " + b1);
        List<String> b2 = it.nextBatch(10);
        if (!b2.equals(List.of("192.168.1.2", "192.168.1.3"))) throw new AssertionError("Test 9 batch: " + b2);
        if (!it.nextBatch(5).isEmpty()) throw new AssertionError("Test 10 batch should be empty");

        System.out.println("All 10 tests passed.");
    }
}
