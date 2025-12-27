import java.util.*;

// 有点绕
class TypeNode {
    String baseType = null;
    List<TypeNode> children = null;

    public TypeNode(String baseType) {
        this.baseType = baseType;
    }

    public TypeNode(List<TypeNode> children) {
        this.children = children;
    }

    public boolean isPrimitive() {
        return baseType != null;
    }

    @Override
    public String toString() {
        if (isPrimitive()) return baseType;
        return "(" + String.join(", ", children.stream().map(TypeNode::toString).toList()) + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TypeNode)) return false;
        TypeNode other = (TypeNode) obj;
        if (this.isPrimitive() != other.isPrimitive()) return false;
        if (this.isPrimitive()) return this.baseType.equals(other.baseType);
        return Objects.equals(this.children, other.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseType, children);
    }
}

class Function {
    List<TypeNode> params;
    TypeNode returnType;

    public Function(List<TypeNode> params, TypeNode returnType) {
        this.params = params;
        this.returnType = returnType;
    }

    @Override
    public String toString() {
        return "(" + String.join(", ", params.stream().map(TypeNode::toString).toList()) + ") -> " + returnType.toString();
    }
}

class TypeResolver {

    public static TypeNode getReturnType(Function func, List<TypeNode> args) {
        if (func.params.size() != args.size()) return null;
        Map<String, TypeNode> typeMapping = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            if (!matchAndBind(func.params.get(i), args.get(i), typeMapping)) {
                return null;
            }
        }
        return replaceGenerics(func.returnType, typeMapping);
    }

    private static boolean matchAndBind(TypeNode param, TypeNode arg, Map<String, TypeNode> map) {
        if (param.isPrimitive()) {
            String key = param.baseType;
            if (isGeneric(key)) {
                if (!map.containsKey(key)) {
                    map.put(key, arg);
                    return true;
                } else {
                    return map.get(key).equals(arg);
                }
            } else {
                return param.equals(arg);
            }
        } else {
            if (!arg.isPrimitive() && param.children.size() == arg.children.size()) {
                for (int i = 0; i < param.children.size(); i++) {
                    if (!matchAndBind(param.children.get(i), arg.children.get(i), map)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    private static TypeNode replaceGenerics(TypeNode node, Map<String, TypeNode> map) {
        if (node.isPrimitive()) {
            if (isGeneric(node.baseType) && map.containsKey(node.baseType)) {
                return map.get(node.baseType);
            }
            return new TypeNode(node.baseType);
        } else {
            List<TypeNode> newChildren = new ArrayList<>();
            for (TypeNode child : node.children) {
                newChildren.add(replaceGenerics(child, map));
            }
            return new TypeNode(newChildren);
        }
    }

    private static boolean isGeneric(String type) {
        return type.length() == 1 && Character.isUpperCase(type.charAt(0));
    }
}

class Main {
    public static void main(String[] args) {
        TypeNode T = new TypeNode("T");
        TypeNode intNode = new TypeNode("int");
        TypeNode strNode = new TypeNode("str");

        Function func = new Function(
                List.of(T),
                new TypeNode(List.of(T, intNode))
        );

        System.out.println(func);

        TypeNode ret = TypeResolver.getReturnType(func, List.of(strNode));
        System.out.println(ret); // Expected: (str, int)




        // Test 1: Simple fixed type
        Function f1 = new Function(List.of(new TypeNode("int")), new TypeNode("int"));
        System.out.println(f1);
        System.out.println(TypeResolver.getReturnType(f1, List.of(new TypeNode("int")))); // int

        // Test 2: Basic generic
         T = new TypeNode("T");
        Function f2 = new Function(List.of(T), new TypeNode(List.of(T, new TypeNode("int"))));
         ret = TypeResolver.getReturnType(f2, List.of(new TypeNode("str")));
        System.out.println(ret); // (str, int)

        // Test 3: Nested generic
        TypeNode X = new TypeNode("X");
        Function f3 = new Function(List.of(new TypeNode(List.of(X, new TypeNode("int")))), new TypeNode(List.of(new TypeNode("int"), X)));
        TypeNode input = new TypeNode(List.of(new TypeNode("str"), new TypeNode("int")));
        TypeNode out = TypeResolver.getReturnType(f3, List.of(input));
        System.out.println(out); // (int, str)

        // Test 4: Generic passthrough
        Function f4 = new Function(List.of(T), T);
        TypeNode out4 = TypeResolver.getReturnType(f4, List.of(new TypeNode("str")));
        System.out.println(out4); // str

        // Test 5: Nested passthrough
        Function f5 = new Function(List.of(T), new TypeNode(List.of(T)));
        TypeNode out5 = TypeResolver.getReturnType(f5, List.of(new TypeNode("str")));
        System.out.println(out5); // (str)

        // Test 6: Multiple generic
        TypeNode A = new TypeNode("A");
        TypeNode B = new TypeNode("B");
        Function f6 = new Function(List.of(A, B), new TypeNode(List.of(B, A)));
        TypeNode out6 = TypeResolver.getReturnType(f6, List.of(new TypeNode("int"), new TypeNode("str")));
        System.out.println(out6); // (str, int)
    }
}

/*
📘 第 1 部分：实现类型建模类
类 Node
表示一个类型，可以是：

基础类型（如 "int" 或 "str"）

或由多个子类型组合成的复合类型（如 ["int", "str"]）

java
Copy
Edit
public class Node {
    public Node(String baseType);
    public Node(List<Node> children);
    public String toString(); // 示例输出见下方
}
举例：
java
Copy
Edit
new Node("int")        => 输出: int
new Node(Arrays.asList(new Node("int"), new Node("str")))
                        => 输出: (int, str)
类 Function
表示一个函数签名，由输入参数和返回值类型组成：

java
Copy
Edit
public class Function {
    public Function(List<Node> params, Node returnType);
    public String toString(); // 示例输出见下方
}
举例：
java
Copy
Edit
Function f = new Function(
    Arrays.asList(new Node("str"), new Node("int")),
    new Node("int")
);
f.toString() => ((str, int) -> int)
🧪 第 2 部分：实现类型推导（带泛型支持）
给定一个函数对象 Function 和一次调用的参数类型列表 invokeArgs，你需要实现：

java
Copy
Edit
public static Node getReturnType(Function func, List<Node> invokeArgs);
要求：

如果 invokeArgs 与 func.params 不匹配，应抛出异常或返回 null。

支持 泛型，例如：

若函数定义为 Function([T], [T, "int"])，调用参数为 [Node("str")]，则返回应为 ["str", "int"]

泛型可以嵌套出现在复合类型中，如 (T, "int") -> ("str", T)

提示：
泛型使用字符串 "T"、"U" 表示。

需要在推导过程中构建泛型变量的映射，并进行替换。

Node 的结构可能是递归嵌套的，需要递归处理泛型匹配与替换。

✅ 要求总结：
实现 Node 和 Function 类及其 toString() 方法。

实现 getReturnType(Function func, List<Node> invokeArgs) 方法支持泛型推导。
 */
