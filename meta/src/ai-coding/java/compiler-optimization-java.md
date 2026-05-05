# Compiler Optimization Java Version

## 题目阶段

1. 解析 instruction file。
2. 计算 time cost 和 memory cost。
3. 支持复杂 RHS，例如 `var3 * 100 - var4`。
4. 讨论 compiler optimization：constant folding、common subexpression elimination、dead code elimination、liveness memory reuse。

## Java 参考实现

```java
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CompilerOptimization {
    private static final Map<String, Integer> OP_TIME = Map.of(
            "+", 1,
            "-", 1,
            "*", 3,
            "/", 5
    );

    static class Instruction {
        String lhs;
        String rhs;
        List<String> variables;
        int timeCost;

        Instruction(String lhs, String rhs, List<String> variables, int timeCost) {
            this.lhs = lhs;
            this.rhs = rhs;
            this.variables = variables;
            this.timeCost = timeCost;
        }
    }

    public static int[] extractTimeAndMemCost(String path) throws IOException {
        List<Instruction> instructions = parseFile(path);
        int time = computeTime(instructions);
        int mem = computePeakMemory(instructions);
        return new int[]{time, mem};
    }

    public static int extractTotalCost(String path) throws IOException {
        int[] cost = extractTimeAndMemCost(path);
        return cost[0] + cost[1];
    }

    static List<Instruction> parseFile(String path) throws IOException {
        List<Instruction> result = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            Instruction inst = parseLine(line);
            if (inst != null) result.add(inst);
        }
        return result;
    }

    static Instruction parseLine(String line) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) return null;

        String[] parts = line.split("=", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("bad instruction: " + line);
        }

        String lhs = parts[0].trim();
        String rhs = parts[1].trim();
        return new Instruction(lhs, rhs, expressionVariables(rhs), expressionTimeCost(rhs));
    }

    static int computeTime(List<Instruction> instructions) {
        int total = 0;
        for (Instruction inst : instructions) {
            total += inst.timeCost;
        }
        return total;
    }

    static int computePeakMemory(List<Instruction> instructions) {
        Set<String> defined = new HashSet<>();
        for (Instruction inst : instructions) {
            defined.add(inst.lhs);
        }

        Map<String, Integer> lastUse = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            for (String var : instructions.get(i).variables) {
                if (defined.contains(var)) {
                    lastUse.put(var, i);
                }
            }
        }

        Set<String> live = new HashSet<>();
        int peak = 0;

        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);

            live.add(inst.lhs);
            peak = Math.max(peak, live.size());

            for (String var : inst.variables) {
                Integer last = lastUse.get(var);
                if (last != null && last == i) {
                    live.remove(var);
                }
            }
        }

        return peak;
    }

    static int expressionTimeCost(String expr) {
        int total = 0;
        for (String token : tokenize(expr)) {
            total += OP_TIME.getOrDefault(token, 0);
        }
        return total;
    }

    static List<String> expressionVariables(String expr) {
        List<String> vars = new ArrayList<>();
        for (String token : tokenize(expr)) {
            if (OP_TIME.containsKey(token)) continue;
            if (token.equals("(") || token.equals(")")) continue;
            if (isNumber(token)) continue;
            vars.add(token);
        }
        return vars;
    }

    static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;

        while (i < expr.length()) {
            char ch = expr.charAt(i);

            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }

            if ("+-*/()".indexOf(ch) >= 0) {
                tokens.add(String.valueOf(ch));
                i++;
                continue;
            }

            if (Character.isLetterOrDigit(ch) || ch == '_') {
                int j = i;
                while (j < expr.length()) {
                    char cj = expr.charAt(j);
                    if (Character.isLetterOrDigit(cj) || cj == '_') {
                        j++;
                    } else {
                        break;
                    }
                }
                tokens.add(expr.substring(i, j));
                i = j;
                continue;
            }

            throw new IllegalArgumentException("unexpected char: " + ch);
        }

        return tokens;
    }

    static boolean isNumber(String token) {
        if (token.isEmpty()) return false;
        for (char ch : token.toCharArray()) {
            if (!Character.isDigit(ch)) return false;
        }
        return true;
    }
}
```

## 测试思路

```java
assert CompilerOptimization.expressionTimeCost("var1 + var2") == 1;
assert CompilerOptimization.expressionTimeCost("var1 * var2") == 3;
assert CompilerOptimization.expressionTimeCost("var3 * 100 - var4") == 4;

assert CompilerOptimization.expressionVariables("10 * var2").equals(List.of("var2"));
assert CompilerOptimization.expressionVariables("var3 * 100 - var4").equals(List.of("var3", "var4"));
```

## Cost model 坑点

如果 unit test 期待：

```java
assert extractTotalCost("instruction1.txt") == 14;
```

需要确认：

- `+ - * /` 各自 time cost。
- variable read 是否算 time。
- constant load 是否算 time。
- assignment 是否算 time。
- memory 是 peak live temporaries，还是 total variables。
- input vars 是否算 memory。
- `res` 是否算 memory。

不要在没确认 cost model 时硬套公式。

## Compiler optimization 讨论

- Constant folding：
  - `10 * 2 -> 20`
- Algebraic simplification：
  - `x + 0 -> x`
  - `x * 1 -> x`
  - `x / 1 -> x`
- Common subexpression elimination：
  - 重复的 `a + b` 只算一次。
- Dead code elimination：
  - 如果 temp 后续没用，且不是最终 output，可以删除。
- Liveness memory reuse：
  - temp 最后一次使用后释放，降低 peak memory。

## 面试重点

- 先问 cost model。
- RHS 可能不是简单 binary expression，要能 tokenize。
- Constants 不应该当 variable。
- Memory 如果按 compiler 语义，通常要做 liveness analysis。
- Optimization 要说明降低的是 time、memory，还是两者。

