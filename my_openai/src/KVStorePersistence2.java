import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class KVStorePersistence2 {

    public interface FileSystem {
        void saveBlob(String name, byte[] data);
        byte[] getBlob(String name);
    }

    public static class DefaultFileSystem implements FileSystem {
        Map<String, byte[]> blobs = new HashMap<>();
       
        public void saveBlob(String name, byte[] data) {
            blobs.put(name, data == null ? new byte[0]: data);
        }

        public byte[] getBlob(String name) {
            return blobs.getOrDefault(name, new byte[0]);
        }
    }

    private static final int INT_BYTES = 4;

    public static byte[] serializeInt(int value) {
        return new byte[] {(byte) (value >> 24), (byte)(value >> 16), (byte) (value >> 8), (byte) value};
    }

    public static int deserializeInt(byte[] data) {
        if (data == null || data.length < INT_BYTES) return 0;
        return ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
             | ((data[2] & 0xFF) << 8)  |  (data[3] & 0xFF);
    }

    public static byte[] serializeStr(String value) throws IOException {
        if (value == null) return new byte[0];
        return value.getBytes("UTF-8");
    }

    public static String deserializeStr(byte[] data) throws IOException {
        return new String(data, "UTF-8");
    }

    private FileSystem fs;
    private Map<String, String> store;
    private static final int CHUNK_SIZE = 1024;
    private static final String METADATA_FILE = "_metadata";
    private static final String CHUNK_PREFIX = "chunk_";

    public KVStorePersistence2(FileSystem fileSystem) {
        this.fs = fileSystem;
        this.store = new HashMap<>();
    }

    public void put(String key, String value) {
        this.store.put(key, value);
    }

    public String get(String key) {
        return this.store.get(key);
    }

    public void shutdown() throws IOException {
        byte[] serializedBytes = serialize();

        if (serializedBytes.length == 0) {
            fs.saveBlob(METADATA_FILE, serializeInt(0));
            return;
        }

        int totalChunks = (serializedBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        fs.saveBlob(METADATA_FILE, serializeInt(totalChunks));

        for (int i = 0 ; i < totalChunks ; i ++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, serializedBytes.length);

            byte[] chunk = new ByteArrayInputStream(serializedBytes, start, end - start).readAllBytes();
            fs.saveBlob(CHUNK_PREFIX + i, chunk);
        }
    }

    public void restore() throws IOException {
        int totalChunks = deserializeInt(fs.getBlob(METADATA_FILE));
        if (totalChunks == 0) {
            store = new HashMap<>();
            return;
        }

        ByteArrayOutputStream allData = new ByteArrayOutputStream();
        for (int i =  0 ; i < totalChunks ; i ++) {
            byte[] chunk = fs.getBlob(CHUNK_PREFIX + i);
            if (chunk != null) allData.write(chunk);
        }

        store = deserialize(allData.toByteArray());
    }

    private byte[] serialize() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream docs = new DataOutputStream(out);
        for (Map.Entry<String, String> entry : store.entrySet()) {
            byte[] key = serializeStr(entry.getKey());
            byte[] value = serializeStr(entry.getValue());

            docs.writeInt(key.length);
            docs.write(key);

            docs.writeInt(value.length);
            docs.write(value);
        }
        return out.toByteArray();
    }

    private Map<String, String> deserialize(byte[] data) throws IOException {
        Map<String, String> out = new HashMap<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        while (in.available() > 0) {
            byte[] key = new byte[in.readInt()];
            in.readFully(key);

            byte[] value = new byte[in.readInt()];
            in.readFully(value);
            out.put(deserializeStr(key), deserializeStr(value));
        }
        return out;
    }

    public static void main(String[] args) throws IOException {
        // restore 在 shutdown 之前调用：空 FileSystem，不应崩溃
        DefaultFileSystem emptyFs = new DefaultFileSystem();
        KVStorePersistence2 emptyStore = new KVStorePersistence2(emptyFs);
        emptyStore.restore();
        assert emptyStore.get("x") == null : "should be empty after restore on blank fs";

        // 正常流程
        DefaultFileSystem fs = new DefaultFileSystem();
        KVStorePersistence2 store = new KVStorePersistence2(fs);
        store.put("name", "Alice");
        store.put("msg", "Hello\nWorld\t和 emoji 🎉");
        store.shutdown();

        KVStorePersistence2 store2 = new KVStorePersistence2(fs);
        store2.restore();
        System.out.println(store2.get("name"));
        System.out.println(store2.get("msg"));
        System.out.println("OK");
    }
}
