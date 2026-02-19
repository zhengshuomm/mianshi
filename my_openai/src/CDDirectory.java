
import java.util.*;

public class CDDirectory {
    public static void main(String[] args) {
        // 1. 正常路径拼接
        print("1. 正常拼接: " + cd("/foo/bar", "baz")); // /foo/bar/baz

        // 2. 处理 ..（向上一级）
        print("2. 向上一级: " + cd("/foo/bar", "..")); // /foo

        // 3. 处理 .（当前目录）
        print("3. 当前目录: " + cd("/foo/bar", ".")); // /foo/bar

        // 4. 多层 ..
        print("4. 多层向上: " + cd("/foo/bar/baz", "../../qux")); // /foo/qux

        // 5. 根目录边界（试图超出根目录）
        print("5. 超出根目录: " + cd("/", "..")); // Null

        // 6. 绝对路径（newDir 以 / 开头）
        print("6. 绝对路径: " + cd("/foo/bar", "/baz/qux")); // /baz/qux

        // 7. 混合 . 和 ..
        print("7. 混合路径: " + cd("/foo/bar", "./baz/../qux")); // /foo/bar/qux

        // 8. 空目录名
        print("8. 空路径: " + cd("/foo", "")); // /foo

        System.out.println("\n=== 软链接功能测试 ===");

        // 9. 单个软链接
        print("9. 单链接: " + cd2("/foo/bar", "baz",
                new String[] { "/foo/bar", "/abc" })); // /abc/baz

        // 10. 链式软链接（A->B->C）
        print("10. 链式链接: " + cd2("/foo/bar", "baz",
                new String[] { "/foo/bar", "/abc", "/abc", "/bcd", "/bcd/baz", "/xyz" })); // /xyz

        // 11. 最长匹配优先
        print("11. 最长匹配: " + cd2("/foo/bar", "baz",
                new String[] { "/foo/bar", "/abc", "/foo/bar/baz", "/xyz" })); // /xyz

        // 12. 循环检测
        print("12. 循环检测: " + cd2("/foo/bar", "baz",
                new String[] { "/foo/bar", "/abc", "/abc", "/foo/bar" })); // loop

        // 13. 无匹配的软链接
        print("13. 无匹配: " + cd2("/foo/bar", "baz",
                new String[] { "/other", "/xyz" })); // /foo/bar/baz

        // 14. 软链接到根目录
        print("14. 链接到根: " + cd2("/foo/bar", "baz",
                new String[] { "/foo/bar", "/" })); // /baz

        // 15. 部分路径匹配（不应该匹配）
        print("15. 部分匹配: " + cd2("/foobar", "baz",
                new String[] { "/foo", "/abc" })); // /foobar/baz (不匹配)

        // 16. 软链接后使用 ..
        print("16. 链接+向上: " + cd2("/foo/bar", "../qux",
                new String[] { "/foo", "/abc" })); // /abc/qux

        // 17. 多层链式循环
        print("17. 多层循环: " + cd2("/a", "test",
                new String[] { "/a", "/b", "/b", "/c", "/c", "/a" })); // loop

        // 18. 链接后路径为空
        print("18. 链接后空: " + cd2("/foo/bar", "",
                new String[] { "/foo/bar", "/abc" })); // /abc

        // 19. 嵌套路径的链接
        print("19. 嵌套链接: " + cd2("/foo/bar/baz", "qux",
                new String[] { "/foo", "/x", "/foo/bar", "/y" })); // /y/baz/qux (最长匹配)

        // 20. 链接到更短的路径
        print("20. 缩短路径: " + cd2("/foo/bar/baz", "qux",
                new String[] { "/foo/bar/baz", "/x" })); // /x/qux

        System.out.println("\n=== 边界条件测试 ===");

        // 21. 根目录的软链接
        print("21. 根目录链接: " + cd2("/", "foo",
                new String[] { "/", "/abc" })); // /abc/foo

        // 22. 空的软链接字典
        print("22. 空字典: " + cd2("/foo/bar", "baz", new String[] {})); // /foo/bar/baz

        // 23. 相对路径中的软链接
        print("23. 相对路径链接: " + cd2("/foo", "bar/baz",
                new String[] { "/foo/bar", "/abc" })); // /abc/baz

        // 24. 软链接值也有软链接
        print("24. 传递链接: " + cd2("/foo", "test",
                new String[] { "/foo", "/bar", "/bar", "/baz" })); // /baz/test

        // 25. 自我循环
        print("25. 自我循环: " + cd2("/foo", "bar",
                new String[] { "/foo", "/foo" })); // loop
    }

    public static void print(String str) {
        System.out.println(str);
    }

    public static String cd2(String currentDir, String newDir, String[] dict) {
        TreeMap<String, String> map = new TreeMap<>((a, b) -> {
            int numA = a.split("/").length;
            int numB = b.split("/").length;
            if (numA != numB)
                return numB - numA;
            return a.compareTo(b);
        });

        for (int i = 0; i < dict.length; i = i + 2) {
            map.put(dict[i], dict[i + 1]);
        }
        String target = cd(currentDir, newDir);
        return dfs(target, map, new HashSet<>());
    }

    private static String dfs(String path, TreeMap<String, String> map, Set<String> visited) {
        for (String visit : visited) {
            if (path.startsWith(visit)) {
                return "loop";
            }
        }
        for (String key : map.keySet()) {
            if (path.startsWith(key)) {
                String newPath = path.replaceFirst(key, map.get(key));
                visited.add(key);
                return dfs(newPath, map, visited);
            }
        }
        return path;
    }

    public static String cd(String currentDir, String newDir) {
        String targetDir = sanitize(currentDir, newDir);
        Stack<String> stack = new Stack<>();
        String[] targets = targetDir.split("/");
        for (String target : targets) {
            if (target.equals(".") || target.equals(""))
                continue;
            if (target.equals("..")) {
                if (!stack.isEmpty())
                    stack.pop();
                else
                    continue;
            } else {
                stack.push(target);
            }
        }
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.insert(0, "/" + stack.pop());
        }
        if (sb.length() == 0)
            sb.append("/");
        return sb.toString();

    }

    private static String sanitize(String currentDir, String newDir) {
        if (newDir.startsWith("/"))
            return newDir;
        if (newDir.startsWith("~"))
            return "/home/user/" + newDir.substring(1);
        if (currentDir.startsWith("~")) {
            currentDir = "/home/user/" + currentDir.substring(1);
        }
        if (!currentDir.endsWith("/")) {
            currentDir += "/";
        }
        return currentDir + newDir;
    }
}
