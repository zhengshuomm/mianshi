import java.util.*;

public class IPv4IteratorV2  implements Iterator<String>, Iterable<String>{
    
    long start;
    long current;
    boolean reverse;
    private static final long MAX_IP = 0xFFFFFFFFL;
    private long rMin;
    private long rMax;

    public IPv4IteratorV2(String startIp) {
        // this.start = parse(startIp);
        // this.current = start;
        this(startIp, false);
    }

    public IPv4IteratorV2(String startIp, boolean reverse) {
        // this.start = parse(startIp);
        // this.current = start;
        this.reverse = reverse;

        if (startIp.contains("/")) {
            int slash = startIp.indexOf("/");
            String ip = startIp.substring(0, slash);
            int prefixLength = Integer.parseInt(startIp.substring(slash+ 1));
            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException();
            }
            this.start = parse(ip);
            int suffixBit = 32 - prefixLength;
            long mask = (1 << suffixBit) - 1;
            long base = start & (~mask);
            this.rMin = base;
            this.rMax = base + mask;
        } else {
            this.start = parse(startIp);
            this.rMin = 0;
            this.rMax = MAX_IP;
        }

        this.current = start;
        rMin = 0;
        rMax = MAX_IP;
    }

    public boolean hasNext(){
        return reverse? current >= 0 : current <= MAX_IP;
    }

    public String next() {
        if (!hasNext()) throw new NoSuchElementException("Past range max");
        String s = toStr(current);
        if(reverse) current --;
        else current ++;
        return s;

    }

    private long parse(String ip) {
        String[] parts = ip.split("\\.");
        long v = 0;
        for (int i = 0 ; i < 4; i ++) {
            int num = Integer.parseInt(parts[i]);
            if (num < 0 || num > 255) {
                throw new IllegalArgumentException();
            }
            v = (v << 8) | num;
        }
        return v;
    }

    private String toStr(long ip) {
        return ((ip >> 24) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    @Override
    public Iterator<String> iterator() {
        return this;
    }

    public static void main(String[] args) {
        IPv4IteratorV2 f = new IPv4IteratorV2("10.0.0.5", false);
        assert "10.0.0.5".equals(f.next());
        assert "10.0.0.6".equals(f.next());

        IPv4IteratorV2 r = new IPv4IteratorV2("10.0.0.2", true);
        assert "10.0.0.2".equals(r.next());
        assert "10.0.0.1".equals(r.next());

        IPv4IteratorV2 maxIt = new IPv4IteratorV2("255.255.255.255", false);
        assert maxIt.hasNext();
        assert "255.255.255.255".equals(maxIt.next());
        assert !maxIt.hasNext();

        try {
            new IPv4IteratorV2("256.0.0.1");
            assert false;
        } catch (IllegalArgumentException ignored) {
        }

        IPv4IteratorV2 d = new IPv4IteratorV2("10.0.0.5/31", false);
        System.out.println(d.next());
        System.out.println(d.next());
        System.out.println(d.next());


        System.out.println("IPv4IteratorV2 main 测试通过（需加 -ea 才检查 assert）");
    }
}
