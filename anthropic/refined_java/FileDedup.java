package anthropic.refined_java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDedup {
    public static List<List<String>> findDuplicateFiles(String rootPath) {
        File root = new File(rootPath);

        List<File> files = new ArrayList<>();
        collectFiles(root, files);

        // Step 1: group by file size
        Map<Long, List<File>> sizeToFiles = new HashMap<>();

        for (File file : files) {
            long size = file.length();

            if (!sizeToFiles.containsKey(size)) {
                sizeToFiles.put(size, new ArrayList<>());
            }

            sizeToFiles.get(size).add(file);
        }

        // Step 2: only hash files with same size
        Map<String, List<String>> hashToFiles = new HashMap<>();

        for (List<File> sameSizeFiles : sizeToFiles.values()) {
            if (sameSizeFiles.size() <= 1) {
                continue;
            }

            for (File file : sameSizeFiles) {
                try {
                    String hash = sha256(file);

                    if (!hashToFiles.containsKey(hash)) {
                        hashToFiles.put(hash, new ArrayList<>());
                    }

                    hashToFiles.get(hash).add(file.getPath());

                } catch (Exception e) {
                    System.err.println("Skip file: " + file.getPath());
                }
            }
        }

        /**
        Map<String, List<String>> hashToFiles = new HashMap<>();

        for (File file : files) {
            try {
                String hash = sha256(file);

                if (!hashToFiles.containsKey(hash)) {
                    hashToFiles.put(hash, new ArrayList<>());
                }

                hashToFiles.get(hash).add(file.getPath());

            } catch (Exception e) {
                System.err.println("Skip file: " + file.getPath());
            }
        }
        **/ 

        List<List<String>> result = new ArrayList<>();

        for (List<String> group : hashToFiles.values()) {
            if (group.size() > 1) {
                result.add(group);
            }
        }

        return result;
    }

    private static void collectFiles(File current, List<File> files) {
        if (current.isFile()) {
            files.add(current);
            return;
        }

        if (!current.isDirectory()) {
            return;
        }

        File[] children = current.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {
            collectFiles(child, files);
        }
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {

            int len;

            while ((len = input.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        }

        byte[] hashBytes = digest.digest();

        StringBuilder sb = new StringBuilder();

        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        System.out.println(sb.toString());

        return sb.toString();
    }

    public static void main(String[] args) {
        String rootPath = "/Users/szheng/code/mianshi/anthropic/refined_java/file_size_test";

        List<List<String>> duplicates = findDuplicateFiles(rootPath);
        System.out.println(duplicates.size());

        for (List<String> group : duplicates) {
            System.out.println(group);
        }
    }
}
