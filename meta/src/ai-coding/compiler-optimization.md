# Compiler Optimization

## 题目背景

给定若干 instruction file，每个文件里包含一组简单的三地址表达式。

目标：

- 解析 instruction。
- 计算执行这些 instruction 的 time cost 和 memory cost。
- 后续可以做 compiler optimization，降低 time 和 memory。

面试中给了类似目录：

```text
src/
test/
  instruction1.txt
  instruction2.txt
  instruction3.txt
test.py
```

需要实现：

```python
def extract_time_and_mem_cost(instruction_file):
    # TODO
    return time, mem
```

或者某些 test 只 assert 一个 total cost：

```python
assert extract_time_and_mem_cost("test/instruction1.txt") == 14
```

注意：这类题最大的坑通常不是 parser，而是 cost model。面试时必须先确认：

- 每个 operation 的 time cost 是多少？
- assignment 是否算 time？
- variable load / constant load 是否算 time？
- memory cost 是 peak live temporaries，还是 number of variables，还是 total allocated results？
- input vars 是否算 memory？
- output `res` 是否算 memory？
- 能不能做 optimization？

如果面试官没有给清楚，要先问。

## Example

### instruction1.txt

```text
res1 = var1 + var2
res2 = var3 - var4
res3 = res2 + var5
res = res1 + res3
```

### instruction2.txt

```text
res1 = var1 * var2
res2 = var3 - var4
res3 = res2 / var5
res = res1 + res3
```

### instruction3.txt

```text
res1 = 10 * var2
res2 = var3 * 100 - var4
res3 = res1 / 2
res = res2 + res3
```

## Stage 1：Parsing

### 支持的表达式

常见输入可能是：

```text
lhs = operand1 op operand2
```

例如：

```text
res1 = var1 + var2
res2 = var3 - var4
res3 = res1 / 2
```

但也可能出现：

```text
res2 = var3 * 100 - var4
```

这说明 RHS 不一定只有一个 binary operation，可能是一个小表达式。

### 简化策略

面试里可以先实现简单 parser：

- 先支持 binary expression。
- 如果测试包含更复杂表达式，再扩展为 token-based parser 或 Python AST-like parser。

为了安全，可以写一个简单 tokenizer + shunting-yard，把表达式转成 AST 或 postfix，再计算 cost。

## Stage 2：Cost model

因为题目描述里数字有坑，建议把 cost model 做成可配置。

一种常见设定：

```python
OP_TIME = {
    "+": 1,
    "-": 1,
    "*": 3,
    "/": 5,
}
```

Memory cost 可能有几种解释。

### 解释 A：总共产生了多少 intermediate result

例如：

```text
res1 = ...
res2 = ...
res3 = ...
res = ...
```

memory cost = 4 个 result variable。

优点：

- 简单。

缺点：

- 不是真实 peak memory。
- 不考虑变量生命周期。

### 解释 B：peak live temporaries

真实 compiler 更常见：

- 一个临时变量如果后续不再使用，可以释放。
- memory cost 是同时 live 的最大变量数。

例如：

```text
res1 = var1 + var2
res2 = var3 - var4
res3 = res2 + var5
res = res1 + res3
```

`res2` 在计算完 `res3` 后不再需要。

优点：

- 更像 compiler memory optimization。

缺点：

- 要做 liveness analysis。

### 解释 C：time + memory weighted total

有些 unit test 可能只 assert 一个数字，比如：

```python
assert extract_time_and_mem_cost("instruction1.txt") == 14
```

这时可能要求返回：

```text
total_cost = time + memory
```

或者：

```text
total_cost = time_weight * time + mem_weight * mem
```

面试时必须确认。

## 参考实现 1：Binary expression parser

这版适合 RHS 都是：

```text
a + b
a - b
a * b
a / b
```

### Code

```python
from dataclasses import dataclass


OP_TIME = {
    "+": 1,
    "-": 1,
    "*": 3,
    "/": 5,
}


@dataclass
class Instruction:
    lhs: str
    left: str
    op: str
    right: str


def parse_binary_line(line):
    line = line.strip()
    if not line or line.startswith("#"):
        return None

    lhs, rhs = line.split("=")
    lhs = lhs.strip()
    tokens = rhs.strip().split()

    if len(tokens) != 3:
        raise ValueError(f"expected binary expression, got: {line}")

    left, op, right = tokens
    return Instruction(lhs=lhs, left=left, op=op, right=right)


def parse_binary_file(path):
    instructions = []
    with open(path) as f:
        for line in f:
            inst = parse_binary_line(line)
            if inst is not None:
                instructions.append(inst)
    return instructions
```

## Time cost

```python
def compute_time_cost(instructions):
    total = 0
    for inst in instructions:
        total += OP_TIME[inst.op]
    return total
```

## Memory cost：按 total result variables

```python
def compute_memory_total_results(instructions):
    return len({inst.lhs for inst in instructions})
```

## Memory cost：按 peak live temporaries

### 思路

1. 先统计每个 variable 最后一次被使用的位置。
2. 执行每条 instruction：
   - lhs 产生一个新 value，占内存。
   - 如果 operand 是之前的 temporary，并且这是它最后一次使用，可以释放。
3. 记录 peak live count。

### 注意

- 输入变量 `var1`, `var2` 通常不算 temporary memory。
- 只有 instruction 产生的 `lhs` 算 temporary/result memory。
- 最终输出 `res` 是否算 memory，要看题目定义。这里先算。

### Code

```python
def is_number(token):
    try:
        int(token)
        return True
    except ValueError:
        return False


def compute_peak_memory(instructions):
    defined = {inst.lhs for inst in instructions}

    last_use = {}
    for i, inst in enumerate(instructions):
        for operand in [inst.left, inst.right]:
            if operand in defined:
                last_use[operand] = i

    live = set()
    peak = 0

    for i, inst in enumerate(instructions):
        # lhs is newly produced
        live.add(inst.lhs)
        peak = max(peak, len(live))

        # operands can be freed after their last use
        for operand in [inst.left, inst.right]:
            if operand in live and last_use.get(operand) == i:
                live.remove(operand)

    return peak
```

## extract_time_and_mem_cost

根据 unit test 要求，可以返回 tuple 或 total。

```python
def extract_time_and_mem_cost(path, memory_mode="peak", return_total=False):
    instructions = parse_binary_file(path)
    time = compute_time_cost(instructions)

    if memory_mode == "peak":
        mem = compute_peak_memory(instructions)
    elif memory_mode == "total_results":
        mem = compute_memory_total_results(instructions)
    else:
        raise ValueError(f"unknown memory_mode: {memory_mode}")

    if return_total:
        return time + mem

    return time, mem
```

## Stage 3：支持复杂表达式

如果测试包含：

```text
res2 = var3 * 100 - var4
```

那么 RHS 有两个 operator。

这时可以：

1. 写一个完整 expression parser。
2. 或者把 RHS tokenize 后按 precedence 计算 time cost。

如果只需要 cost，不需要真正求值，可以用 shunting-yard。

### Tokenizer

```python
def tokenize(expr):
    tokens = []
    i = 0
    while i < len(expr):
        ch = expr[i]
        if ch.isspace():
            i += 1
            continue
        if ch in "+-*/()":
            tokens.append(ch)
            i += 1
            continue
        if ch.isalnum() or ch == "_":
            j = i
            while j < len(expr) and (expr[j].isalnum() or expr[j] == "_"):
                j += 1
            tokens.append(expr[i:j])
            i = j
            continue
        raise ValueError(f"unexpected char: {ch}")
    return tokens
```

### Count operation cost from expression

```python
def expression_time_cost(expr):
    tokens = tokenize(expr)
    total = 0
    for token in tokens:
        if token in OP_TIME:
            total += OP_TIME[token]
    return total
```

这能处理：

```text
var3 * 100 - var4
```

time = `*` cost + `-` cost。

但如果需要 liveness，需要知道 RHS 用到了哪些 variables。

```python
def expression_variables(expr):
    vars_ = []
    for token in tokenize(expr):
        if token in OP_TIME or token in {"(", ")"}:
            continue
        if is_number(token):
            continue
        vars_.append(token)
    return vars_
```

## General instruction parser

```python
@dataclass
class GeneralInstruction:
    lhs: str
    rhs: str
    variables: list[str]
    time_cost: int


def parse_general_line(line):
    line = line.strip()
    if not line or line.startswith("#"):
        return None

    lhs, rhs = line.split("=", 1)
    lhs = lhs.strip()
    rhs = rhs.strip()

    return GeneralInstruction(
        lhs=lhs,
        rhs=rhs,
        variables=expression_variables(rhs),
        time_cost=expression_time_cost(rhs),
    )


def parse_general_file(path):
    instructions = []
    with open(path) as f:
        for line in f:
            inst = parse_general_line(line)
            if inst is not None:
                instructions.append(inst)
    return instructions
```

## General cost computation

```python
def compute_general_time_cost(instructions):
    return sum(inst.time_cost for inst in instructions)


def compute_general_peak_memory(instructions):
    defined = {inst.lhs for inst in instructions}

    last_use = {}
    for i, inst in enumerate(instructions):
        for var in inst.variables:
            if var in defined:
                last_use[var] = i

    live = set()
    peak = 0

    for i, inst in enumerate(instructions):
        live.add(inst.lhs)
        peak = max(peak, len(live))

        for var in inst.variables:
            if var in live and last_use.get(var) == i:
                live.remove(var)

    return peak


def extract_time_and_mem_cost_general(path, return_total=False):
    instructions = parse_general_file(path)
    time = compute_general_time_cost(instructions)
    mem = compute_general_peak_memory(instructions)

    if return_total:
        return time + mem
    return time, mem
```

## Stage 4：Compiler optimization

题目说 optimize compiler time and memory，常见优化有：

1. Constant folding
2. Common subexpression elimination
3. Dead code elimination
4. Liveness-based memory reuse

### Optimization 1：Constant folding

如果表达式全是常数：

```text
res1 = 10 * 2
```

可以编译期算成：

```text
res1 = 20
```

Runtime time cost 可以变成 0 或 assignment cost，取决于题目定义。

### Optimization 2：Algebraic simplification

```text
x + 0 -> x
x * 1 -> x
x * 0 -> 0
x / 1 -> x
```

这可以减少 operation time。

### Optimization 3：Common subexpression elimination

```text
res1 = var1 + var2
res2 = var1 + var2
res = res1 + res2
```

`var1 + var2` 可以只计算一次。

注意：

- 需要保证变量没有 mutation。
- 本题是 simple SSA-like instruction，没有 reassignment 时比较安全。

### Optimization 4：Dead code elimination

如果某个 result 后续没有被使用，也不是最终 output：

```text
tmp = var1 + var2
res = var3 + var4
```

`tmp` 可以删掉。

### Optimization 5：Liveness / memory reuse

即使不改变 time，也可以降低 peak memory：

- 某个 temp 最后一次使用后释放。
- 新 temp 可以复用旧内存 slot。

这就是前面的 peak memory 计算。

## 面试中最容易踩的坑

### 坑 1：unit test 的数字不是你想的 cost model

例如 instruction1：

```text
res1 = var1 + var2
res2 = var3 - var4
res3 = res2 + var5
res = res1 + res3
```

如果：

```python
OP_TIME["+"] = 1
OP_TIME["-"] = 1
```

time = 4。

如果 memory 是 result variables：

```text
res1, res2, res3, res
```

mem = 4。

total = 8，不是 14。

所以如果 test 期待 14，说明题目 cost model 可能还有：

- variable read cost
- constant load cost
- assignment cost
- operator 不同权重
- memory 按 variable access count 算
- input variables 也算 memory
- 或者返回的不是 `time + mem`

面试时要问清楚。

### 坑 2：只支持 binary expression，结果遇到 `var3 * 100 - var4`

`rhs.split()` 长度不是 3。

要么：

- 明确先只支持 binary，等 test fail 再扩展。
- 要么一开始就写 tokenizer，统计所有 operator 和 variables。

### 坑 3：Memory 是 peak，不是 total

Compiler optimization 题通常更关心 peak memory。

比如：

```text
res1 = var1 + var2
res2 = var3 - var4
res3 = res2 + var5
res = res1 + res3
```

`res2` 在 `res3` 后就可以释放。

如果只数所有 lhs，不能体现 memory reuse。

### 坑 4：Constants 不应该当 variable

```text
res1 = 10 * var2
```

`10` 是 constant，不应该当作 live variable。

## 测试用例

```python
def test_expression_time_cost():
    assert expression_time_cost("var1 + var2") == 1
    assert expression_time_cost("var1 * var2") == 3
    assert expression_time_cost("var3 * 100 - var4") == 4


def test_expression_variables():
    assert expression_variables("var1 + var2") == ["var1", "var2"]
    assert expression_variables("10 * var2") == ["var2"]
    assert expression_variables("var3 * 100 - var4") == ["var3", "var4"]


def test_general_cost_instruction1(tmp_path):
    path = tmp_path / "instruction1.txt"
    path.write_text(
        "res1 = var1 + var2\n"
        "res2 = var3 - var4\n"
        "res3 = res2 + var5\n"
        "res = res1 + res3\n"
    )

    time, mem = extract_time_and_mem_cost_general(str(path))
    assert time == 4
    assert mem == 3


def test_general_cost_instruction3(tmp_path):
    path = tmp_path / "instruction3.txt"
    path.write_text(
        "res1 = 10 * var2\n"
        "res2 = var3 * 100 - var4\n"
        "res3 = res1 / 2\n"
        "res = res2 + res3\n"
    )

    time, mem = extract_time_and_mem_cost_general(str(path))
    assert time == 12
    assert mem == 3
```

为什么 `instruction1` peak mem 是 3？

执行：

```text
1. res1 live                     live={res1}, peak=1
2. res2 live                     live={res1,res2}, peak=2
3. res3 live, res2 last used      live={res1,res3}, peak=3 before free
4. res live, res1/res3 last used  live={res}, peak=3
```

这里 peak 取决于“先分配 lhs，再释放 operands”还是“先读取 operands 后释放，再写 lhs”。不同定义会不同。

如果面试官的 mem model 是先释放 operands 再分配 lhs，peak 可能是 2。

所以这也是必须确认的坑。

## 面试解释重点

- 先问 cost model：
  - operation cost
  - variable access cost
  - constant cost
  - assignment cost
  - memory 是 peak 还是 total

- Parser 先做简单版：
  - 如果 RHS 都是 binary，直接 split。
  - 如果出现多 operator，升级 tokenizer。

- Memory 如果按 compiler 语义：
  - 做 liveness analysis。
  - 最后一次使用后释放。

- Optimization 不是盲目改：
  - constant folding 降 time。
  - common subexpression elimination 降重复计算。
  - dead code elimination 降 time 和 mem。
  - liveness/memory reuse 降 peak mem。

## 可扩展参考：一个完整入口

```python
def extract_time_and_mem_cost(path):
    instructions = parse_general_file(path)
    time = compute_general_time_cost(instructions)
    mem = compute_general_peak_memory(instructions)
    return time, mem
```

如果 unit test 期待单个数字：

```python
def extract_total_cost(path):
    time, mem = extract_time_and_mem_cost(path)
    return time + mem
```

但最终应该以题目给定的 cost model 为准。
