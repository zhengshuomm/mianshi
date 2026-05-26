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

/*
整个面试过程都在一个共享的 Google Colab 上进行，我们用平台上的文件来测试代码的正确性。在实现了一个基本的解决方案（即对每个文件的完整内容进行哈希）后，
面试官开始了一系列追问。他首先询问我实现的时间复杂度，我解释说这与所有文件大小的总和成正比。

为了改进效率，我提出了一种多阶段的方法，先使用“廉价”的哈希函数。具体来说，首先从元数据检查文件大小，然后对大小相同的文件哈希其前1024个字节，
最后才在万不得已时进行完整的哈希。面试官随后问这种新方法在最坏情况下的时间复杂度，我回答说，如果所有文件的大小和初始内容都完全相同，那么时间复杂度仍然取决于文件的总大小。
这引出了关于哈希函数的更深层次讨论。我当时苦苦思索，试图找到一个既廉价又哈希碰撞率低的哈希函数。面试后我才意识到，没有哪个哈希函数是完美的，你总能找到反例。
接着，话题转向了系统设计，面试官问我如何判断一个程序是CPU密集型还是I/O密集型。我解释说会用性能分析结果来判断：如果CPU大部分时间处于空闲状态，
程序就是I/O密集型；如果CPU持续繁忙，则是CPU密集型。
之后，面试官提出了一个更复杂的挑战：设计一个持续监控重复文件的系统。这个问题有些模糊，
所以我先与他确认了需求：系统需要在添加重复文件时通知文件所有者，并且要能处理文件的删除。我概述了一个解决方案，
即使用数据库来维护两个映射：一个是从文件哈希到文件的映射（用于重复检测和通知），另一个是从文件到其哈希的映射（用于处理删除）。
我还提到了对于海量文件，可以使用类似 MapReduce 的机制来扩展，并解释了 Map 和 Reduce 操作分别会做什么，以高效地处理数据。


面试官开始问很多follow up。包括I/O bound or CPU bound, multi nodes怎么弄，还有hash 算法怎么选。
*/
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
        File[] children = current.listFiles();
        if (children == null) return; // 处理权限不足或空目录
        
        for (File child : children) {
            if (child.isFile()) {
                files.add(child);
            } else {
                collectFiles(child, files); // 是目录就继续往下找
            }
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
