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
要判断dictionary里是
 */
public class CDDirectory {
    public static void main(String[] args) {
//        print(cd("/foo/bar/", "baz"));
//        print(cd("/foo/../", "./baz"));
//        print(cd("/", "foo/bar/../../baz/"));
//        print(cd("/", ".."));
//        print(cd("~/", ".."));
        print(cd2("/foo/bar", "baz", new String[]{"/foo/bar", "/abc"}));
        print(cd2("/foo/bar", "baz", new String[]{"/foo/bar", "/abc", "/abc", "/bcd", "/bcd/baz",
        "/xyz"}));
        print(cd2("/foo/bar", "baz", new String[]{"/foo/bar", "/abc", "/foo/bar/baz", "/xyz"}));
        print(cd2("/foo/bar", "baz", new String[]{"/foo/bar", "/abc", "/abc", "/foo/bar"}));
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
        Comparator<String> comp = Comparator
                .comparingInt((String s) -> s.length() - s.replace("/", "").length())
                .reversed()
                .thenComparing(Comparator.naturalOrder());
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