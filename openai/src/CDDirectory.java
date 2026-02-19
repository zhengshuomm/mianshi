import java.util.*;

/*
要求实现cd(current_dir, new dir), 返回最终的path, 比如：
cd(/foo/bar, baz) = /foo/bar/baz
cd(/foo/../, ./baz) = /baz
cd(/, foo/bar/../../baz) = /baz
cd(/, ..) = Null
第二问可不可以加上对～符号也就是home directory的支持
完成以后难度加大，第三个参数是soft link的dictionary，比如：
cd(/foo/bar, baz, {/foo/bar: /abc}) = /abc/baz
cd(/foo/bar, baz, {/foo/bar: /abc, /abc: /bcd, /bcd/baz: /xyz}) = /xyz
dictionary 里有可能有短匹配和长匹配，应该先匹配长的(more specific), 比如：
cd(/foo/bar, baz, {/foo/bar: /abc, /foo/bar/baz: /xyz}) = /xyz
要判断dictionary里是否有循环




疑问
1. 不知道给定的path里第一个是不是结尾有 ‘/’, 如果没有的话自己要check一下加上
2. 第二问不知道～ home directory是在哪里出现，在string开始还是中间某个部分。是在第一个string还是第二个string

我做的时候assume结尾有可能没有‘/’, 因为不知道～出现在哪所以没做这问。
Key point
1. 第三问的condense soft links需要仔细一下 2. Match的时候要先match最长的
 */
public class CDDirectory {
    public static void main(String[] args) {
        System.out.println("=== 基本 CD 功能测试 ===");
        
        // 1. 正常路径拼接
        print("1. 正常拼接: " + cd("/foo/bar", "baz"));  // /foo/bar/baz
        
        // 2. 处理 ..（向上一级）
        print("2. 向上一级: " + cd("/foo/bar", ".."));  // /foo
        
        // 3. 处理 .（当前目录）
        print("3. 当前目录: " + cd("/foo/bar", "."));  // /foo/bar
        
        // 4. 多层 ..
        print("4. 多层向上: " + cd("/foo/bar/baz", "../../qux"));  // /foo/qux
        
        // 5. 根目录边界（试图超出根目录）
        print("5. 超出根目录: " + cd("/", ".."));  // Null
        
        // 6. 绝对路径（newDir 以 / 开头）
        print("6. 绝对路径: " + cd("/foo/bar", "/baz/qux"));  // /baz/qux
        
        // 7. 混合 . 和 ..
        print("7. 混合路径: " + cd("/foo/bar", "./baz/../qux"));  // /foo/bar/qux
        
        // 8. 空目录名
        print("8. 空路径: " + cd("/foo", ""));  // /foo
        
        System.out.println("\n=== 软链接功能测试 ===");
        
        // 9. 单个软链接
        print("9. 单链接: " + cd2("/foo/bar", "baz", 
            new String[]{"/foo/bar", "/abc"}));  // /abc/baz
        
        // 10. 链式软链接（A->B->C）
        print("10. 链式链接: " + cd2("/foo/bar", "baz", 
            new String[]{"/foo/bar", "/abc", "/abc", "/bcd", "/bcd/baz", "/xyz"}));  // /xyz
        
        // 11. 最长匹配优先
        print("11. 最长匹配: " + cd2("/foo/bar", "baz", 
            new String[]{"/foo/bar", "/abc", "/foo/bar/baz", "/xyz"}));  // /xyz
        
        // 12. 循环检测
        print("12. 循环检测: " + cd2("/foo/bar", "baz", 
            new String[]{"/foo/bar", "/abc", "/abc", "/foo/bar"}));  // loop
        
        // 13. 无匹配的软链接
        print("13. 无匹配: " + cd2("/foo/bar", "baz", 
            new String[]{"/other", "/xyz"}));  // /foo/bar/baz
        
        // 14. 软链接到根目录
        print("14. 链接到根: " + cd2("/foo/bar", "baz", 
            new String[]{"/foo/bar", "/"}));  // /baz
        
        // 15. 部分路径匹配（不应该匹配）
        print("15. 部分匹配: " + cd2("/foobar", "baz", 
            new String[]{"/foo", "/abc"}));  // /foobar/baz (不匹配)
        
        // 16. 软链接后使用 ..
        print("16. 链接+向上: " + cd2("/foo/bar", "../qux", 
            new String[]{"/foo", "/abc"}));  // /abc/qux
        
        // 17. 多层链式循环
        print("17. 多层循环: " + cd2("/a", "test", 
            new String[]{"/a", "/b", "/b", "/c", "/c", "/a"}));  // loop
        
        // 18. 链接后路径为空
        print("18. 链接后空: " + cd2("/foo/bar", "", 
            new String[]{"/foo/bar", "/abc"}));  // /abc
        
        // 19. 嵌套路径的链接
        print("19. 嵌套链接: " + cd2("/foo/bar/baz", "qux", 
            new String[]{"/foo", "/x", "/foo/bar", "/y"}));  // /y/baz/qux (最长匹配)
        
        // 20. 链接到更短的路径
        print("20. 缩短路径: " + cd2("/foo/bar/baz", "qux", 
            new String[]{"/foo/bar/baz", "/x"}));  // /x/qux
        
        System.out.println("\n=== 边界条件测试 ===");
        
        // 21. 根目录的软链接
        print("21. 根目录链接: " + cd2("/", "foo", 
            new String[]{"/", "/abc"}));  // /abc/foo
        
        // 22. 空的软链接字典
        print("22. 空字典: " + cd2("/foo/bar", "baz", new String[]{}));  // /foo/bar/baz
        
        // 23. 相对路径中的软链接
        print("23. 相对路径链接: " + cd2("/foo", "bar/baz", 
            new String[]{"/foo/bar", "/abc"}));  // /abc/baz
        
        // 24. 软链接值也有软链接
        print("24. 传递链接: " + cd2("/foo", "test", 
            new String[]{"/foo", "/bar", "/bar", "/baz"}));  // /baz/test
        
        // 25. 自我循环
        print("25. 自我循环: " + cd2("/foo", "bar", 
            new String[]{"/foo", "/foo"}));  // loop
    }

    public static void print(String str) {
        System.out.println(str);
    }

    public static String cd(String currentDir, String newDir) {
        String target = sanitize(currentDir, newDir);
        Stack<String> stack = new Stack<>();
        String[] targets = target.split("/");
        for (int i = 0 ; i < targets.length ; i ++) {
            String str = targets[i];
            if (str.equals(".") || str.equals("")) continue;
            if (str.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.pop();
                } else {
                    return "Null";
                }
            } else {
                stack.push(str);
            }
        }
        String res = "";
        while (!stack.isEmpty()) {
            res = "/" + stack.pop() + res;
        }
        return res;
    }

    public static String cd2(String currentDir, String newDir, String[] dict) {
        Comparator<String> comp = (s1, s2) -> {
            int depth1 = s1.length() - s1.replace("/", "").length();
            int depth2 = s2.length() - s2.replace("/", "").length();
            if (depth1 != depth2) return depth2 - depth1; // 深度倒序
            return s1.compareTo(s2); // 字典序
        };
        TreeMap<String, String> map = new TreeMap<>(comp);
        for (int i = 0 ; i <= dict.length - 2 ; i = i +2) {
            map.put(dict[i], dict[i + 1]);

        }
        String target = cd(currentDir, newDir);
        return dfs(target, map, new HashSet<>());
    }

    public static String dfs(String path, Map<String, String> map, Set<String> visited) {
        for (String v: visited) {
            if (path.startsWith(v)) {
                return "loop";
            }
        }
        // 看上去只是前缀匹配
        for (String key: map.keySet()) {
            String value = map.get(key);
            if (path.startsWith(key)) {
                String newPath = path.replaceFirst(key, value);
                visited.add(key);
                return dfs(newPath, map, visited);
            }
        }
        return path;
    }

    public static String sanitize(String currentDir, String newDir) {
        // 看上去很怪
        if (newDir.startsWith("/")) {
            return newDir;
        } else if (newDir.startsWith("~")) {
            return "/home" + newDir.substring(1);
        } else if (currentDir.startsWith("~")) {
            // 怪
            currentDir = "/home" + currentDir.substring(1);
        }
        if (currentDir.length() > 0 && !currentDir.endsWith("/")) {
            currentDir += "/";
        }
        return currentDir + newDir;
    }



}