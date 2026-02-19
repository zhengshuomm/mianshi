# 实现计划：LeetCode 2296 - Design a Text Editor

## 问题概述
设计一个文本编辑器类，支持光标操作、文本添加和删除功能。

## 核心需求

### 需要实现的方法：
1. **`addText(String text)`** - 在光标位置添加文本，光标移动到插入文本的右侧
2. **`deleteText(int k)`** - 删除光标左侧最多 k 个字符，返回实际删除的字符数
3. **`cursorLeft(int k)`** - 光标左移最多 k 位，返回光标左侧最后 min(10, len) 个字符
4. **`cursorRight(int k)`** - 光标右移最多 k 位，返回光标左侧最后 min(10, len) 个字符

### 约束条件：
- 文本只包含小写英文字母
- 1 ≤ text.length, k ≤ 40
- 光标位置始终在有效范围内：0 ≤ cursor ≤ text.length
- 最多 2×10⁴ 次方法调用

## 数据结构设计

### 方案：双栈（或双端队列）结构

**核心思想：**
- 使用两个栈来模拟光标位置
- **左栈（leftStack）**：存储光标左侧的所有字符（栈顶是光标前最近的字符）
- **右栈（rightStack）**：存储光标右侧的所有字符（栈顶是光标后最近的字符）

**优势：**
- 所有操作时间复杂度为 O(k)，其中 k 是操作涉及的字符数
- 空间复杂度 O(n)，n 是文本总长度
- 实现简单直观

## 详细实现步骤

### 步骤 1：类结构设计

```java
public class TextEditor {
    // 使用两个栈来存储光标左右两侧的字符
    private Deque<Character> leftStack;   // 光标左侧字符（栈顶是最近的光标前字符）
    private Deque<Character> rightStack;  // 光标右侧字符（栈顶是最近的光标后字符）
    
    public TextEditor() {
        leftStack = new ArrayDeque<>();
        rightStack = new ArrayDeque<>();
    }
}
```

**为什么使用 Deque 而不是 Stack？**
- Stack 是遗留类，性能较差
- Deque 提供更好的 API 和性能
- 可以使用 `addFirst()`, `removeFirst()` 等操作

### 步骤 2：实现 addText(String text)

**逻辑：**
1. 遍历 text 的每个字符
2. 将每个字符添加到左栈（使用 `addLast()` 或 `push()`）
3. 光标自然移动到文本末尾（因为字符在左栈中）

**伪代码：**
```
addText(text):
    for each char c in text:
        leftStack.addLast(c)  // 或 leftStack.push(c)
```

**时间复杂度：** O(text.length)
**空间复杂度：** O(1) 额外空间

### 步骤 3：实现 deleteText(int k)

**逻辑：**
1. 计算实际可以删除的字符数 = min(k, leftStack.size())
2. 从左栈中删除相应数量的字符
3. 返回实际删除的字符数

**伪代码：**
```
deleteText(k):
    count = min(k, leftStack.size())
    for i = 0 to count-1:
        leftStack.removeLast()  // 或 leftStack.pop()
    return count
```

**时间复杂度：** O(k)
**空间复杂度：** O(1)

### 步骤 4：实现 cursorLeft(int k)

**逻辑：**
1. 计算实际移动距离 = min(k, leftStack.size())
2. 从左栈顶部取出字符，移动到右栈顶部
3. 返回左栈中最后 min(10, leftStack.size()) 个字符

**伪代码：**
```
cursorLeft(k):
    move = min(k, leftStack.size())
    for i = 0 to move-1:
        char = leftStack.removeLast()
        rightStack.addLast(char)
    
    // 获取左栈最后 min(10, leftStack.size()) 个字符
    return getLastNChars(leftStack, 10)
```

**获取最后 N 个字符的辅助方法：**
```
getLastNChars(stack, n):
    // 需要临时取出字符，构建字符串，然后放回去
    // 或者使用迭代器从后往前遍历
```

**时间复杂度：** O(k)
**空间复杂度：** O(1) 额外空间（不包括返回字符串）

### 步骤 5：实现 cursorRight(int k)

**逻辑：**
1. 计算实际移动距离 = min(k, rightStack.size())
2. 从右栈顶部取出字符，移动到左栈顶部
3. 返回左栈中最后 min(10, leftStack.size()) 个字符

**伪代码：**
```
cursorRight(k):
    move = min(k, rightStack.size())
    for i = 0 to move-1:
        char = rightStack.removeLast()
        leftStack.addLast(char)
    
    return getLastNChars(leftStack, 10)
```

**时间复杂度：** O(k)
**空间复杂度：** O(1) 额外空间

### 步骤 6：辅助方法 - getLastNChars

**问题：** 如何从栈中获取最后 N 个字符而不破坏栈结构？

**方案 1：使用临时栈**
```
getLastNChars(stack, n):
    temp = new Stack()
    count = min(n, stack.size())
    
    // 取出最后 count 个字符
    for i = 0 to count-1:
        temp.push(stack.pop())
    
    // 构建字符串
    result = ""
    while !temp.isEmpty():
        result = temp.pop() + result
        stack.push(result.charAt(result.length()-1))  // 放回去
    
    return result
```

**方案 2：使用 StringBuilder 和迭代器（如果 Deque 支持）**
```
getLastNChars(stack, n):
    // 对于 ArrayDeque，可以转换为数组或使用迭代器
    // 但需要小心顺序
```

**方案 3：直接构建字符串（推荐）**
```
getLastNChars(leftStack, n):
    int count = Math.min(n, leftStack.size())
    if (count == 0) return ""
    
    // 临时取出字符
    List<Character> temp = new ArrayList<>();
    for (int i = 0; i < count; i++) {
        temp.add(leftStack.removeLast());
    }
    
    // 构建字符串（注意顺序）
    StringBuilder sb = new StringBuilder();
    for (int i = temp.size() - 1; i >= 0; i--) {
        sb.append(temp.get(i));
    }
    
    // 放回去
    for (int i = temp.size() - 1; i >= 0; i--) {
        leftStack.addLast(temp.get(i));
    }
    
    return sb.toString();
```

## 完整实现结构

```java
public class TextEditor {
    private Deque<Character> leftStack;
    private Deque<Character> rightStack;
    
    public TextEditor() {
        leftStack = new ArrayDeque<>();
        rightStack = new ArrayDeque<>();
    }
    
    public void addText(String text) {
        // 实现步骤 2
    }
    
    public int deleteText(int k) {
        // 实现步骤 3
    }
    
    public String cursorLeft(int k) {
        // 实现步骤 4
    }
    
    public String cursorRight(int k) {
        // 实现步骤 5
    }
    
    private String getLastNChars(int n) {
        // 辅助方法：获取左栈最后 n 个字符
    }
}
```

## 边界情况处理

1. **空文本：** 所有操作都应该正确处理空栈
2. **k 超出范围：** 使用 min(k, stack.size()) 确保不越界
3. **光标在开头：** cursorLeft 应该返回空字符串
4. **光标在末尾：** cursorRight 应该返回当前文本的最后部分

## 测试用例设计

1. **基本操作测试：**
   - addText → deleteText
   - cursorLeft → cursorRight

2. **边界测试：**
   - 空编辑器操作
   - k 值大于文本长度
   - 连续多次移动光标

3. **复杂场景：**
   - 混合操作（添加、删除、移动光标）
   - 长文本操作
   - 返回字符串长度验证（最多 10 个字符）

## 优化考虑

1. **使用 ArrayDeque 而不是 Stack：** 性能更好
2. **缓存最后 10 个字符：** 如果频繁调用 cursorLeft/Right，可以考虑缓存
3. **字符串构建优化：** 使用 StringBuilder 而不是字符串拼接

## 时间复杂度分析

- `addText(text)`: O(text.length)
- `deleteText(k)`: O(k)
- `cursorLeft(k)`: O(k)
- `cursorRight(k)`: O(k)

总体：每个操作都是 O(k)，其中 k ≤ 40，非常高效。

## 空间复杂度

O(n)，其中 n 是文本的总字符数（存储在两个栈中）

## 实现优先级

1. ✅ 实现基本数据结构（两个 Deque）
2. ✅ 实现 addText（最简单）
3. ✅ 实现 deleteText（较简单）
4. ✅ 实现 getLastNChars 辅助方法
5. ✅ 实现 cursorLeft
6. ✅ 实现 cursorRight
7. ✅ 添加边界情况处理
8. ✅ 编写测试用例

## 总结

这个问题的核心是使用**双栈数据结构**来模拟光标位置，使得所有操作都能在 O(k) 时间内完成。关键点在于理解光标左右两侧字符的存储方式，以及如何高效地获取最后 N 个字符而不破坏栈结构。
