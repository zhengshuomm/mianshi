import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 与 openai_python/kv_store.py 等价的 Java 实现。
 * 持久化 key-value 存储：将内存中的字典序列化为字节并保存到"模拟"文件系统，
 * 支持按块存储（每块最多 1KB），支持任意字符（含换行、emoji 等）。
 * 不使用 JSON、pickle 等，自行设计长度前缀的序列化格式。
 */
public class KVStorePersistence {

    /** 模拟文件系统：按名称保存/读取 blob */
    public interface FileSystem {
        void saveBlob(String name, byte[] data);
        byte[] getBlob(String name);
    }

    /** 默认实现，测试时注入 mock */
    public static class DefaultFileSystem implements FileSystem {
        private final Map<String, byte[]> blobs = new HashMap<>();

        @Override
        public void saveBlob(String name, byte[] data) {
            blobs.put(name, data == null ? new byte[0] : data.clone());
        }

        @Override
        public byte[] getBlob(String name) {
            return blobs.getOrDefault(name, new byte[0]);
        }
    }

    // --- 题目提供的辅助函数（此处实现）---
    private static final int INT_BYTES = 4;

    public static byte[] serializeInt(int value) {
        return new byte[] {
            (byte)(value >> 24), (byte)(value >> 16), (byte)(value >> 8), (byte)value
        };
    }

    public static int deserializeInt(byte[] data) {
        if (data == null || data.length < INT_BYTES) return 0;
        return ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
             | ((data[2] & 0xFF) << 8)  |  (data[3] & 0xFF);
    }

    public static byte[] serializeStr(String value) {
        if (value == null) return new byte[0];
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String deserializeStr(byte[] data) {
        if (data == null || data.length == 0) return "";
        return new String(data, StandardCharsets.UTF_8);
    }

    // --- KVStore ---
    private final FileSystem fs;
    private Map<String, String> store;
    private static final int CHUNK_SIZE = 1024;
    private static final String METADATA_FILE = "_metadata";
    private static final String CHUNK_PREFIX = "chunk_";

    public KVStorePersistence(FileSystem fileSystem) {
        this.fs = fileSystem;
        this.store = new HashMap<>();
    }

    /** 在内存中保存一个 key-value 对 */
    public void put(String key, String value) {
        store.put(key, value);
    }

    /** 根据 key 查找 value */
    public String get(String key) {
        return store.get(key);
    }

    /** 将整个 store 序列化并写入文件系统（按块 + 元数据） */
    public void shutdown() throws IOException {
        byte[] serializedBytes = serialize();

        if (serializedBytes.length == 0) {
            fs.saveBlob(METADATA_FILE, serializeInt(0));
            return;
        }

        int totalChunks = (serializedBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        fs.saveBlob(METADATA_FILE, serializeInt(totalChunks));

        for (int idx = 0; idx < totalChunks; idx++) {
            int start = idx * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, serializedBytes.length);
            // 用 ByteArrayInputStream 截取块，避免 arraycopy
            byte[] chunk = new ByteArrayInputStream(serializedBytes, start, end - start).readAllBytes();
            fs.saveBlob(CHUNK_PREFIX + idx, chunk);
        }
    }

    /** 从文件系统读取字节并重建 store */
    public void restore() throws IOException {
        int totalChunks = deserializeInt(fs.getBlob(METADATA_FILE));

        if (totalChunks == 0) {
            store = new HashMap<>();
            return;
        }

        ByteArrayOutputStream allData = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = fs.getBlob(CHUNK_PREFIX + i);
            if (chunk != null) allData.write(chunk);
        }

        store = deserialize(allData.toByteArray());
    }

    /**
     * 将字典转为字节。
     * 格式：<keyLen(4字节)><key><valueLen(4字节)><value> ...
     */
    private byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(out);
        for (Map.Entry<String, String> e : store.entrySet()) {
            byte[] keyBytes = serializeStr(e.getKey());
            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);

            byte[] valueBytes = serializeStr(e.getValue());
            dos.writeInt(valueBytes.length);
            dos.write(valueBytes);
        }
        return out.toByteArray();
    }

    /** 将字节反序列化为字典，用 DataInputStream 逐段读取，无需手动管理位置 */
    private Map<String, String> deserialize(byte[] data) throws IOException {
        Map<String, String> out = new HashMap<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        while (in.available() > 0) {
            //    byte[] keyBytes = in.readNBytes(in.readInt());
            //    byte[] valueBytes = in.readNBytes(in.readInt());
            byte[] keyBytes = new byte[in.readInt()];
            in.readFully(keyBytes);
            byte[] valueBytes = new byte[in.readInt()];
            in.readFully(valueBytes);
            out.put(deserializeStr(keyBytes), deserializeStr(valueBytes));
        }
        return out;
    }

    // --- 简单测试 ---
    public static void main(String[] args) throws IOException {
        DefaultFileSystem fs = new DefaultFileSystem();
        KVStorePersistence store = new KVStorePersistence(fs);

        store.put("name", "Alice");
        store.put("msg", "Hello\nWorld\t和 emoji 🎉");
        store.shutdown();

        KVStorePersistence store2 = new KVStorePersistence(fs);
        store2.restore();

        System.out.println(store2.get("name"));
        System.out.println(store2.get("msg"));
        System.out.println("OK");
    }
}
