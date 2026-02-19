import java.io.*;
import java.util.*;

// Time-based Versioned Key-Value Store (极简版)
public class KVStore2 {
    private final Map<String, TreeMap<Long, String>> store = new HashMap<>();

    public synchronized void set(String key, String value, long timestamp) {
        store.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
    }

    public synchronized String get(String key, long timestamp) {
        TreeMap<Long, String> versions = store.get(key);
        if (versions == null) return null;
        Map.Entry<Long, String> entry = versions.floorEntry(timestamp);
        return entry == null ? null : entry.getValue();
    }

    public synchronized void save(String path) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            for (Map.Entry<String, TreeMap<Long, String>> e : store.entrySet()) {
                for (Map.Entry<Long, String> v : e.getValue().entrySet()) {
                    write(out, e.getKey());
                    out.writeLong(v.getKey());
                    write(out, v.getValue());
                }
            }
        }
    }

    public synchronized void load(String path) throws IOException {
        store.clear();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
            while (in.available() > 0) {
                String key = read(in);
                long ts = in.readLong();
                String val = read(in);
                store.computeIfAbsent(key, k -> new TreeMap<>()).put(ts, val);
            }
        }
    }

    private void write(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        out.writeInt(b.length);
        out.write(b);
    }

    private String read(DataInputStream in) throws IOException {
        byte[] b = new byte[in.readInt()];
        in.readFully(b);
        return new String(b, "UTF-8");
    }

    public static void main(String[] args) throws IOException {
        KVStore2 store = new KVStore2();
        store.set("user1", "hello", 10);
        store.set("user1", "world", 20);
        
        System.out.println(store.get("user1", 15)); // hello
        System.out.println(store.get("user1", 25)); // world
        
        store.save("kv2.dat");
        
        KVStore2 store2 = new KVStore2();
        store2.load("kv2.dat");
        System.out.println(store2.get("user1", 25)); // world
    }
}

/** 
public void shutdown() {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos)) {

        // Serialization Format:[num_entries: int] -> [key1_len: int][key1: byte[]][val1_len: int][val1: byte[]]
        // dos.writeInt(map.size());

        for (Map.Entry<String, String> entry : map.entrySet()) {
            byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);

            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);
            dos.writeInt(valueBytes.length);
            dos.write(valueBytes);
        }

        medium.saveBlob(baos.toByteArray());
        isClosed = true;

    } catch (IOException e) {
        throw new RuntimeException("Serialization failed", e);
    }
}

public void restore() {
    byte[] data = medium.getBlob();
    if (data == null || data.length == 0) {
        return;
    }

    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais)) {

        map.clear();
        // int entryCount = dis.readInt();

        while (dis.available()>0) {
            int keyLength = dis.readInt();
            byte[] keyBytes = new byte[keyLength];
            dis.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            int valueLength = dis.readInt();
            byte[] valueBytes = new byte[valueLength];
            dis.readFully(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);

            map.put(key, value);
        }

    } catch (IOException e) {
        throw new RuntimeException("Deserialization failed", e);
    }
}

**/