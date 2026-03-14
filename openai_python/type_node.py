class TypeNode:
    def __init__(self, base_type: str = None, children: list[TypeNode] = None ):
        self.base_type = base_type
        self.children = children
    
    def __str__(self) -> str:
        if self.is_primitive():
            return self.base_type
        res = []
        for node in self.children:
            res.append(str(node))
        combined_str = ",".join(res)
        return f"[{combined_str}]"       
    
    def is_primitive(self) -> bool:
        return self.base_type is not None

    def is_generic(self) -> bool:
        return self.is_primitive and self.base_type[0].isupper()
    
class Function:
    def __init__(self, params: list[TypeNode], return_type: TypeNode):
        self.params = params
        self.return_type = return_type

    def __str__(self):
        params_str = ",".join([str(x) for x in self.params])
        return f"({params_str}) -> {str(self.return_type)}"   

def get_return_type(args: list[TypeNode], func: Function) -> TypeNode:
    if len(func.params) != len(args):
        return None
    dict = {}
    for i in range (len(args)):
        if not match_and_bind(func.params[i], args[i], dict):
            return None
    
    return replace_generics(func.return_type, dict)

def match_and_bind(param: TypeNode, arg: TypeNode, dict):
    if param.is_primitive():
        if param.is_generic():
            if param.base_type not in dict:
                dict[param.base_type] = arg
                return True
            else:
                return str(dict[param.base_type]) == str(arg)
        else:
            return str(param) == str(arg)
    else:
        if not arg.is_primitive() and len(arg.children) == len(param.children):
            for i in range (len(arg.children)):
                if not match_and_bind(param.children[i], arg.children[i], dict):
                    return False
            return True
        return False

def replace_generics(node: TypeNode, dict) -> TypeNode:
    if node.is_primitive():
        if node.is_generic() and node.base_type in dict:
            return dict[node.base_type]
        return node
    else:
        children = []
        for child in node.children:
            children.append(replace_generics(child, dict))
        return TypeNode(children=children)

if __name__ == "__main__":

    # print(2**3)  # 8
    # --- Node: primitive (str in type_list) ---
    n_int = TypeNode("int")
    assert str(n_int) == "int"
    assert n_int.is_generic() is False

    n_str = TypeNode("str")
    assert str(n_str) == "str"
    assert n_str.is_generic() is False

    # --- Node: generic (str not in type_list) ---
    n_t = TypeNode("T")
    assert str(n_t) == "T"
    assert n_t.is_generic() is True

    n_t1 = TypeNode("T1")
    assert n_t1.is_generic() is True

    # --- Node: tuple (list of Node) ---
    n_tuple = TypeNode(children=[TypeNode("int"), TypeNode("str")])
    assert str(n_tuple) == "[int,str]"

    n_nested = TypeNode(children=[TypeNode("int"), TypeNode(children=[TypeNode("str"), TypeNode("T1")])])
    assert str(n_nested) == "[int,[str,T1]]"

    # --- Function __str__ ---
    f_simple = Function([TypeNode("int")], TypeNode("int"))
    print(str(f_simple))
    assert str(f_simple) == "(int) -> int"

    f_two = Function([TypeNode("int"), TypeNode("str")], TypeNode("str"))
    assert str(f_two) == "(int,str) -> str"

    f_generic = Function([TypeNode("int"), TypeNode("T")], TypeNode("T"))
    assert str(f_generic) == "(int,T) -> T"

    # --- get_return_type: wrong number of arguments ---
    res = get_return_type([TypeNode("int")], f_two)
    assert res is None

    # --- get_return_type: primitive match ---
    out = get_return_type([TypeNode("int")], f_simple)
    assert str(out) == "int"

    # --- get_return_type: primitive mismatch ---
    res = get_return_type([TypeNode("str")], f_simple)
    assert res is None


    # --- get_return_type: generic binding and substitution (covers line 86: node_type is str) ---
    # [int, T] -> T with args [int, str] => T binds to "str", output T => "str"
    f_int_t_out_t = Function([TypeNode("int"), TypeNode("T")], TypeNode("T"))
    out = get_return_type([TypeNode("int"), TypeNode("str")], f_int_t_out_t)
    assert str(out) == "str"

    out = get_return_type([TypeNode("int"), TypeNode("float")], f_int_t_out_t)
    assert str(out) == "float"

    # --- get_return_type: generic conflict ---
    res = get_return_type([TypeNode("int"), TypeNode("str"), TypeNode("float")], Function([TypeNode("T"), TypeNode("T"), TypeNode("int")], TypeNode("T")))
    assert res is None

    # --- get_return_type: tuple in signature and output ---
    # ([int, T], T) -> [T, int] with ([int, str], str) => output [str, int]
    f_tuple = Function(
        [TypeNode(children =[TypeNode("int"), TypeNode("T")]), TypeNode("T")],
        TypeNode(children = [TypeNode("T"), TypeNode("int")])
    )
    out = get_return_type(
        [TypeNode(children = [TypeNode("int"), TypeNode("str")]), TypeNode("str")],
        f_tuple
    )
    assert str(out) == "[str,int]"

    # --- get_return_type: output is primitive (no generic), substitute keeps it ---
    f_out_int = Function([TypeNode("int"), TypeNode("T")], TypeNode("int"))
    out = get_return_type([TypeNode("int"), TypeNode("str")], f_out_int)
    assert str(out) == "int"

    print("All test cases passed.")