import java.util.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class KVStore2 {
    private static final int INT_BYTES = 4;

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

    FileSystem fs;
    Map<String, String> store;
    private final String METADATA_FILE = "_metadata";
    private final String FILE = "_file";
    private final String CHUNK_PREFIX = "chunk_";
    private final int CHUNK_SIZE = 1024;
    // private final S

    public KVStore2(FileSystem fs) {
        this.fs = fs;
        this.store = new HashMap<>();
    }

    public void put(String key, String val) {
        this.store.put(key, val);
    }

    public String get(String key) {
        return this.store.get(key);
    }

    public void shutDown() throws IOException{
        byte[] serializeBytes = serialize();

        if (serializeBytes.length == 0) {
            fs.saveBlob(METADATA_FILE, serializeInt(0));
            return;
        }

        int totalChunks = (serializeBytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        fs.saveBlob(METADATA_FILE, serializeInt(totalChunks));

        for (int i = 0; i < totalChunks ; i ++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, serializeBytes.length);

            byte[] chunk = new ByteArrayInputStream(serializeBytes, start, end - start).readAllBytes();
            fs.saveBlob(CHUNK_PREFIX + i, chunk);
        }
    }

    public void restore() throws IOException {
        int totalChunks = deserializeInt(fs.getBlob(METADATA_FILE));
        if (totalChunks == 0) {
            this.store = new HashMap<>();
            return;
        }

        ByteArrayOutputStream allData = new ByteArrayOutputStream();
        for (int i = 0 ; i < totalChunks ; i ++) {
            byte[] chunk = fs.getBlob(CHUNK_PREFIX + i);
            if (chunk != null) allData.write(chunk);
        }
        this.store = deserialize(allData.toByteArray());  // allData.toByteArray()
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream docs = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(docs);

        for (Map.Entry<String, String> entry: store.entrySet()) {
            byte[] key = serializeStr(entry.getKey());
            byte[] value = serializeStr(entry.getValue());

            out.writeInt(key.length);
            out.write(key);

            out.writeInt(value.length);
            out.write(value);
        }
        return docs.toByteArray();               // docs.toByteArray()
    }

    public Map<String, String> deserialize(byte[] data) throws IOException {
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
        KVStore2 emptyStore = new KVStore2(emptyFs);
        emptyStore.restore();
        assert emptyStore.get("x") == null : "should be empty after restore on blank fs";

        // 正常流程
        DefaultFileSystem fs = new DefaultFileSystem();
        KVStore2 store = new KVStore2(fs);
        store.put("name", "Alice");
        store.put("msg", "Hello\nWorld\t和 emoji 🎉");
        store.shutDown();

        KVStore2 store2 = new KVStore2(fs);
        store2.restore();
        System.out.println(store2.get("name"));
        System.out.println(store2.get("msg"));
        System.out.println("OK");
    }
}
