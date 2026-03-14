import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面试题：给定一棵表示文件存储的树，把文件系统里的文件和路径全部打印出来（类似 tree 命令）。
 * 假设树节点的类已经写好，只需实现打印逻辑。
 */
public class FileSystemTree {

    /** 假设面试官给的节点类（已有方法） */
    static class FileNode {
        private final String name;
        private final boolean directory;
        private final List<FileNode> children;

        public FileNode(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
            this.children = new ArrayList<>();
        }

        public String getName() { return name; }
        public boolean isDirectory() { return directory; }
        public List<FileNode> getChildren() { return children; }

        public void addChild(FileNode node) { children.add(node); }
    }

    /**
     * 打印整棵文件树，格式类似：
     * root/
     * +-- folder1/
     * |   +-- file1.txt
     * |   \-- file2.txt
     * \-- folder2/
     *     \-- file3.txt
     */
    public static void printTree(FileNode root) {
        if (root == null) return;
        System.out.println(root.getName() + (root.isDirectory() ? "/" : ""));
        printChildren(root.getChildren(), "");
    }

    private static void printChildren(List<FileNode> children, String prefix) {
        if (children == null || children.isEmpty()) return;
        int n = children.size();
        for (int i = 0; i < n; i++) {
            FileNode node = children.get(i);
            boolean isLast = (i == n - 1);
            String branch = isLast ? "\\-- " : "+-- ";
            String suffix = node.isDirectory() ? "/" : "";
            System.out.println(prefix + branch + node.getName() + suffix);

            String nextPrefix = prefix + (isLast ? "    " : "|   ");
            printChildren(node.getChildren(), nextPrefix);
        }
    }

    /**
     * 可选：按路径逐行打印（每行一条完整路径）
     * root
     * root/folder1
     * root/folder1/file1.txt
     * ...
     */
    public static void printAllPaths(FileNode root) {
        if (root == null) return;
        printPathsRec(root, root.getName());
    }

    private static void printPathsRec(FileNode node, String path) {
        System.out.println(path);
        if (node.getChildren() == null) return;
        for (FileNode child : node.getChildren()) {
            String childPath = path + (node.isDirectory() ? "/" : "") + child.getName();
            printPathsRec(child, childPath);
        }
    }

    // ---------- 测试（含 4 层深度）----------
    public static void main(String[] args) {
        FileNode root = new FileNode("root", true);
        FileNode f1 = new FileNode("folder1", true);
        FileNode f2 = new FileNode("folder2", true);
        root.addChild(f1);
        root.addChild(f2);

        // folder1 下再套一层目录，共 4 层：root -> folder1 -> subfolder -> deep.txt
        FileNode sub = new FileNode("subfolder", true);
        f1.addChild(sub);
        f1.addChild(new FileNode("file1.txt", false));
        f1.addChild(new FileNode("file2.txt", false));
        sub.addChild(new FileNode("deep.txt", false));
        sub.addChild(new FileNode("another.txt", false));

        f2.addChild(new FileNode("file3.txt", false));

        System.out.println("=== Tree 风格打印 (4 层) ===");
        printTree(root);

        System.out.println("\n=== 全路径逐行打印 ===");
        printAllPaths(root);
    }
}
