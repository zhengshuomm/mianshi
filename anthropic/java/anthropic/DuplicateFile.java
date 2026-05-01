package anthropic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory FS + duplicate finder by size then SHA-256 (from duplicate_file.py). */
public class DuplicateFile {

    static class FSNode {
        boolean isDir;
        byte[] content;
        Map<String, FSNode> children;

        static FSNode dir() {
            FSNode n = new FSNode();
            n.isDir = true;
            n.children = new HashMap<>();
            return n;
        }

        static FSNode file(byte[] c) {
            FSNode n = new FSNode();
            n.isDir = false;
            n.content = c;
            return n;
        }
    }

    public static class FileSystem {
        final FSNode root = FSNode.dir();

        public void addFile(String path, String content) {
            addFileBytes(path, content.getBytes(StandardCharsets.UTF_8));
        }

        public void addFileBytes(String path, byte[] content) {
            List<String> parts = splitPath(path);
            FSNode curr = root;
            for (int i = 0; i < parts.size() - 1; i++) {
                String p = parts.get(i);
                curr.children.putIfAbsent(p, FSNode.dir());
                curr = curr.children.get(p);
            }
            String name = parts.get(parts.size() - 1);
            curr.children.put(name, FSNode.file(content));
        }

        public List<String> listFiles(String path) {
            FSNode node = getNode(path);
            if (node == null || !node.isDir) return Collections.emptyList();
            String prefix = path.endsWith("/") ? path : path + "/";
            List<String> result = new ArrayList<>();
            for (String name : node.children.keySet()) {
                result.add(prefix + name);
            }
            return result;
        }

        public boolean isDirectory(String path) {
            FSNode n = getNode(path);
            return n != null && n.isDir;
        }

        public int getFileSize(String path) {
            FSNode n = getNode(path);
            if (n == null || n.isDir) return 0;
            return n.content.length;
        }

        public InputStream openStream(String path) {
            FSNode n = getNode(path);
            if (n == null || n.isDir) return new ByteArrayInputStream(new byte[0]);
            return new ByteArrayInputStream(n.content);
        }

        FSNode getNode(String path) {
            if ("/".equals(path)) return root;
            List<String> parts = splitPath(path);
            FSNode curr = root;
            for (String part : parts) {
                if (curr == null || !curr.isDir || !curr.children.containsKey(part)) return null;
                curr = curr.children.get(part);
            }
            return curr;
        }

        List<String> splitPath(String path) {
            String cleaned = path;
            if (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
            if (cleaned.endsWith("/")) cleaned = cleaned.substring(0, cleaned.length() - 1);
            if (cleaned.isEmpty()) return Collections.emptyList();
            String[] segs = cleaned.split("/");
            return Arrays.asList(segs);
        }
    }

    public static class DuplicateFileFinder {
        final FileSystem fs;

        public DuplicateFileFinder(FileSystem fs) {
            this.fs = fs;
        }

        public List<List<String>> findDuplicateFiles() {
            List<String> allFiles = new ArrayList<>();
            collectFiles("/", allFiles);
            Map<Integer, List<String>> sizeMap = new HashMap<>();
            for (String fp : allFiles) {
                int sz = fs.getFileSize(fp);
                sizeMap.computeIfAbsent(sz, k -> new ArrayList<>()).add(fp);
            }
            List<List<String>> result = new ArrayList<>();
            for (List<String> sizeGroup : sizeMap.values()) {
                if (sizeGroup.size() < 2) continue;
                Map<String, List<String>> hashMap = new HashMap<>();
                for (String fp : sizeGroup) {
                    String h = computeHash(fp);
                    hashMap.computeIfAbsent(h, k -> new ArrayList<>()).add(fp);
                }
                for (List<String> group : hashMap.values()) {
                    if (group.size() >= 2) {
                        Collections.sort(group);
                        result.add(group);
                    }
                }
            }
            return result;
        }

        void collectFiles(String path, List<String> files) {
            if (!fs.isDirectory(path)) {
                files.add(path);
                return;
            }
            for (String child : fs.listFiles(path)) {
                collectFiles(child, files);
            }
        }

        String computeHash(String filePath) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[4096];
                try (InputStream stream = fs.openStream(filePath)) {
                    int n;
                    while ((n = stream.read(buf)) > 0) {
                        md.update(buf, 0, n);
                    }
                }
                byte[] digest = md.digest();
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
