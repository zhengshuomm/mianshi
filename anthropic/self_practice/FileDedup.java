package anthropic.self_practice;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileDedup {

    public List<List<String>> findDuplicateFiles(String path) throws Exception {
        File root = new File(path);
        // String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        List<File> files = new ArrayList<>();
        collectFiles(root, files);

        // group by size
        Map<Long, List<File>> sizeToFiles = new HashMap<>();
        for (File file : files) {
            long size = file.length();
            sizeToFiles.putIfAbsent(size, new ArrayList<>());
            sizeToFiles.get(size).add(file);
        }

        Map<String, List<String>> res = new HashMap<>();
        // compute
        for (List<File> sameSizFiles : sizeToFiles.values()) {
            Map<String, List<File>> hashToFiles = new HashMap<>();
            if (sameSizFiles.size() <= 1) {
                continue;
            }
            for (File file : sameSizFiles) {
                String lightHash = hash256(file, true);
                hashToFiles.putIfAbsent(lightHash, new ArrayList<>());
                hashToFiles.get(lightHash).add(file);
            }

            for (List<File> sameHashFiles : hashToFiles.values()) {
                if (sameHashFiles.size() <= 1) {
                    continue;
                }
                for (File file : sameHashFiles) {
                    String hash = hash256(file, false);
                    res.putIfAbsent(hash, new ArrayList<>());
                    res.get(hash).add(file.getPath());
                }
            }
        }

        List<List<String>> result = new ArrayList<>();
        for (List<String> group : res.values()) {
            if (group.size() >= 2) {
                result.add(group);
            }
        }
        return result;
    }

    // private String hash256(File file, boolean lightHash) throws Exception{
    //     MessageDigest digest = MessageDigest.getInstance("SHA-256");
    //     byte[] buffer = new byte[8192];
    //     try (FileInputStream input = new FileInputStream(file)) { // this one
    //         int len;
    //         while ((len = input.read(buffer))!= -1) {
    //             digest.update(buffer, 0, len);
    //             if (lightHash) {
    //                 break;
    //             }
    //         }
    //     }

    //     byte[] hashByte = digest.digest();
    //     StringBuilder sb = new StringBuilder();
    //     for (byte b : hashByte) {
    //         sb.append(String.format("%02x", b));
    //     }
    //     return sb.toString();
    // }

    private void collectFiles(File root, List<File> files) {
        if (root.isFile()) {
            files.add(root);
            return;
        } else {
            for (File file : root.listFiles()) {
                collectFiles(file, files);
            }
        }
    }

    private String hash256(File file, boolean light) throws Exception {
        MessageDigest digest =  MessageDigest.getInstance("SHA-256");
        
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int len;
            while ((len = input.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        }
        byte[] hashByte = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashByte) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    
    public static void main(String[] args) throws Exception {
        String rootPath = "/Users/szheng/code/mianshi/anthropic/refined_java/file_size_test";

        FileDedup f = new FileDedup();
        List<List<String>> duplicates = f.findDuplicateFiles(rootPath);
        System.out.println(duplicates.size());

        for (List<String> group : duplicates) {
            System.out.println(group);
        }
    }
}
