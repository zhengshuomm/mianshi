import java.lang.reflect.Type;
import java.util.*;

public class TypeNode {
    String baseType;
    List<TypeNode> children;

    public TypeNode(String baseType) {
        this.baseType = baseType;
    }

    public TypeNode(List<TypeNode> children) {
        this.children = children;
    }

    public boolean isPrimitive() {
        return baseType != null;
    }

    public String toString() {
        if (isPrimitive()) return baseType;
        return "(" + String.join(",", children.stream().map(m -> m.toString()).toList()) + ")";
    }
}

class Function {
    List<TypeNode> params;
    TypeNode returnType;

    public Function( List<TypeNode> params, TypeNode returnType) {
        this.params = params;
        this.returnType = returnType;
    }

    public String toString(){
        StringBuilder sb = new StringBuilder("(");
        for (TypeNode node : params) {
            sb.append(node.toString()).append(",");
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append(")->").append(returnType.toString());
        return sb.toString();
    }
}


class TypeResolver2 {
    public static TypeNode getReturnType(Function func, List<TypeNode> args) {
        if (func.params.size() != args.size()) return null;
        Map<String, TypeNode> map = new HashMap<>();
        for (int i = 0 ; i < args.size() ; i ++) {
            if (!matchAndBind(func.params.get(i), args.get(i), map)) {
                return null;
            }
        }
        return replace(func.returnType, map);
    }

    public static boolean matchAndBind(TypeNode param, TypeNode arg, Map<String, TypeNode> map) {
        if (param.isPrimitive()) {
            String key = param.baseType;
            if (isGeneric(key)) {
                if (!map.containsKey(key)) {
                    map.put(key, arg);
                    return true;
                } else {
                    return map.get(key).baseType.equals(arg.baseType);
                }
            } else {
                return param.baseType.equals(arg.baseType);
            }
        } else {
            if (!arg.isPrimitive() && arg.children.size() == param.children.size()) {
                for (int i = 0 ; i < arg.children.size() ; i ++) {
                    if (!matchAndBind(param.children.get(i), arg.children.get(i), map)) {
                        return false;
                    }
                }
                return true;
            }
            return false;    
        }
    }

    public static TypeNode replace(TypeNode node, Map<String, TypeNode> map) {
        if (node.isPrimitive()) {
            if (isGeneric(node.baseType) && map.containsKey(node.baseType)) {
                return map.get(node.baseType);
            }
            return node;

        } else {
            List<TypeNode> children = new ArrayList<>();
            for (TypeNode n : node.children) {
                children.add(replace(n, map));
            }
            return new TypeNode(children);
        }
    }

    public static boolean isGeneric(String key) {
        return key.length() == 1 && Character.isUpperCase(key.charAt(0));
    }
}

class Main2 {
    public static void main(String[] args) {
        List<Integer> list = Arrays.asList(1,2,3);
        List<String> strs = new ArrayList<>();
        for (Integer node : list) {
            strs.add(node.toString());
        }
        System.out.println( "(" + String.join(", ", strs) + ")");

        // String result = list.stream().map(String::valueOf).collect(Collectors.joining(","));
        // System.out.println(result);

        TypeNode t1 = new TypeNode(List.of(new TypeNode("int"), new TypeNode("str")));
        TypeNode t2 = new TypeNode(List.of(new TypeNode("int"), new TypeNode("str")));
        System.out.println(t1.equals(t2) + "object equals");


        TypeNode T = new TypeNode("T");
        TypeNode intNode = new TypeNode("int");
        TypeNode strNode = new TypeNode("str");

        Function func = new Function(
                List.of(T),
                new TypeNode(List.of(T, intNode))
        );

        System.out.println(func);

        TypeNode ret = TypeResolver2.getReturnType(func, List.of(strNode));
        System.out.println(ret); // Expected: (str, int)




        // Test 1: Simple fixed type
        Function f1 = new Function(List.of(new TypeNode("int")), new TypeNode("int"));
        System.out.println(f1);
        System.out.println(TypeResolver2.getReturnType(f1, List.of(new TypeNode("int")))); // int

        // Test 2: Basic generic
         T = new TypeNode("T");
        Function f2 = new Function(List.of(T), new TypeNode(List.of(T, new TypeNode("int"))));
         ret = TypeResolver2.getReturnType(f2, List.of(new TypeNode("str")));
        System.out.println(ret); // (str, int)

        // Test 3: Nested generic
        TypeNode X = new TypeNode("X");
        Function f3 = new Function(List.of(new TypeNode(List.of(X, new TypeNode("int")))), new TypeNode(List.of(new TypeNode("int"), X)));
        TypeNode input = new TypeNode(List.of(new TypeNode("str"), new TypeNode("int")));
        TypeNode out = TypeResolver2.getReturnType(f3, List.of(input));
        System.out.println(out); // (int, str)

        // Test 4: Generic passthrough
        Function f4 = new Function(List.of(T), T);
        TypeNode out4 = TypeResolver2.getReturnType(f4, List.of(new TypeNode("str")));
        System.out.println(out4); // str

        // Test 5: Nested passthrough
        Function f5 = new Function(List.of(T), new TypeNode(List.of(T)));
        TypeNode out5 = TypeResolver2.getReturnType(f5, List.of(new TypeNode("str")));
        System.out.println(out5); // (str)

        // Test 6: Multiple generic
        TypeNode A = new TypeNode("A");
        TypeNode B = new TypeNode("B");
        Function f6 = new Function(List.of(A, B), new TypeNode(List.of(B, A)));
        TypeNode out6 = TypeResolver2.getReturnType(f6, List.of(new TypeNode("int"), new TypeNode("str")));
        System.out.println(out6); // (str, int)
    }
}
