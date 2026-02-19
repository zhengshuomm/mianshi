/*
✅ 面试题：设计一个可恢复的迭代器系统（Resumable Iterator System）
📌 背景
你需要在 Java 中实现一组支持“恢复状态”的迭代器（ResumableIterator）。所谓可恢复的迭代器，就是它在迭代过程中可以保存当前位置（通过 getState()），之后可以用 setState() 回到原来的位置继续迭代。

这个题目主要考察：

接口设计能力

状态管理

多文件处理逻辑

测试驱动开发（TDD）

✨ 第一问 – 定义 ResumableIterator 接口
设计一个 Java 接口 ResumableIterator<T>，继承自 Iterator<T>，并添加如下两个方法：

java
Copy
Edit
public interface ResumableIterator<T> extends Iterator<T> {
    State getState();           // 获取当前迭代器状态
    void setState(State state); // 将迭代器恢复到某个状态
}
你还需要定义一个 State 类或接口，用来表示当前迭代器的状态。比如在遍历一个列表时，State 可以表示当前位置的索引。

✨ 第二问 – 实现 ListResumableIterator
实现一个 ListResumableIterator<T>，它基于一个 List<T> 实现，支持状态保存与恢复。

然后写一个完整的单元测试用来验证：

每次 getState() 后再通过 setState() 恢复，都能正确继续迭代

所有位置都能正确恢复

结束时的行为（例如 StopIteration）也要测试

✨ 第三问 – 多文件迭代器 MultiFileResumableIterator
你现在已经有了一个 ResumableFileIterator（从 JSON 文件中一行一行读取，返回字符串）。

现在你需要实现一个 MultiFileResumableIterator，它能接受一组 ResumableFileIterator，对它们“扁平化”成一个迭代器。

功能要求：

跳过空文件

当前文件读完后自动切换到下一个文件

getState() 和 setState() 必须能恢复到某个具体的文件、具体的位置。状态中应包含：

当前正在处理的文件的索引

当前文件迭代器的状态

你需要写测试覆盖所有这些情况。

✨ 第四问（进阶）– 实现异步版本（Async Iterator）
你被要求实现一个高性能的日志处理系统。现在你希望把 ResumableFileIterator 改造成异步的版本，比如 AsyncResumableFileIterator。

请你设计或实现：

如何用 Java 的 CompletableFuture 或 虚拟线程（VirtualThread） 实现异步读文件

如何预读下一行，但仍然保持状态一致性

如何用 RandomAccessFile + file.seek() 提升状态恢复性能

这部分也可以只做系统设计讨论。

🔍 题目约束
不能使用 Java 自带的序列化（Serializable）或 JSON 库

状态的保存/恢复逻辑完全由你自己实现

状态格式需要你自己定义，不能用标准格式（如 JSON）

💡 进阶 Follow-up（可以继续问）
支持 2D（二维列表）迭代器，可在嵌套结构中恢复

支持 3D 结构的迭代器

将状态序列化保存到磁盘，再加载恢复

示例说明（方便理解）：
假设你有一个列表 [1, 2, 3, 4]，你用 ListResumableIterator 来迭代它：

java
Copy
Edit
iterator.next();  // 返回 1
iterator.next();  // 返回 2
State s = iterator.getState(); // 保存状态
iterator.next();  // 返回 3
iterator.setState(s); // 恢复到状态2
iterator.next();  // 再次返回 3
 */

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 状态接口
interface State extends Serializable {
}

// ResumableIterator 接口定义
interface ResumableIterator<T> extends Iterator<T> {
    State getState();

    void setState(State state);
}

class ListResumableIterator<T> implements ResumableIterator<T> {

    private final List<T> list;
    private int index;

    public ListResumableIterator(List<T> list) {
        this.list = list;
        this.index = 0;
    }

    @Override
    public State getState() {
        return new ListState(index);
    }

    @Override
    public void setState(State state) {
        this.index = ((ListState) state).index;
    }

    @Override
    public boolean hasNext() {
        return index < list.size();
    }

    @Override
    public T next() {
        if (hasNext()) {
            return list.get(index++);
        }
        return null;
    }

    class ListState implements State {
        private static final long serialVersionUID = 1L;

        private final int index;

        public ListState(int index) {
            this.index = index;
        }
    }
}

// MultiResumableIterator 实现
class MultiResumableIterator<T> implements ResumableIterator<T> {
    private final List<ResumableIterator<T>> iterators;
    private int currentIdx;

    public MultiResumableIterator(List<ResumableIterator<T>> iterators) {
        this.iterators = iterators;
        this.currentIdx = 0;
        skipEmpty();
    }

    private void skipEmpty() {
        while (currentIdx < iterators.size() && !iterators.get(currentIdx).hasNext()) {
            currentIdx++;
        }
    }

    @Override
    public boolean hasNext() {
        skipEmpty();
        return currentIdx < iterators.size();
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return iterators.get(currentIdx).next();
    }

    @Override
    public State getState() {
        return new MultiState(
                currentIdx,
                currentIdx < iterators.size() ? iterators.get(currentIdx).getState() : null);
    }

    @Override
    public void setState(State state) {
        if (!(state instanceof MultiState))
            throw new IllegalArgumentException("Invalid state");
        MultiState ms = (MultiState) state;
        this.currentIdx = ms.iteratorIndex;
        if (this.currentIdx < iterators.size() && ms.innerState != null) {
            iterators.get(this.currentIdx).setState(ms.innerState);
        }
    }

    public static class MultiState implements State {
        private static final long serialVersionUID = 1L;
        private final int iteratorIndex;
        private final State innerState;

        public MultiState(int iteratorIndex, State innerState) {
            this.iteratorIndex = iteratorIndex;
            this.innerState = innerState;
        }
    }
}

class TwoDResumableIterator<T> implements ResumableIterator<T> {
    private final List<List<T>> matrix;
    private int outer;
    private int inner;

    public TwoDResumableIterator(List<List<T>> matrix) {
        this.matrix = matrix;
        this.outer = 0;
        this.inner = 0;
        skipEmpty();
    }

    private void skipEmpty() {
        while (outer < matrix.size() && (matrix.get(outer) == null || inner >= matrix.get(outer).size())) {
            outer++;
            inner = 0;
        }
    }

    @Override
    public boolean hasNext() {
        skipEmpty();
        return outer < matrix.size();
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return matrix.get(outer).get(inner++);
    }

    @Override
    public State getState() {
        return new TwoDState(outer, inner);
    }

    @Override
    public void setState(State state) {
        if (!(state instanceof TwoDState))
            throw new IllegalArgumentException("Invalid state");
        TwoDState s = (TwoDState) state;
        this.outer = s.outer;
        this.inner = s.inner;
    }

    public static class TwoDState implements State {
        private static final long serialVersionUID = 1L;
        private final int outer;
        private final int inner;

        public TwoDState(int outer, int inner) {
            this.outer = outer;
            this.inner = inner;
        }
    }
}

// AsyncResumableIterator 用协程方式模拟异步（使用 CompletableFuture）
abstract class AsyncResumableIterator<T> {
    public abstract CompletableFuture<Boolean> hasNextAsync();

    public abstract CompletableFuture<T> nextAsync();

    public abstract State getState();

    public abstract void setState(State state);
}

// 将 ResumableIterator 封装为异步版本
class AsyncWrapperResumableIterator<T> extends AsyncResumableIterator<T> {
    private final ResumableIterator<T> delegate;
    private final Executor executor;

    public AsyncWrapperResumableIterator(ResumableIterator<T> delegate, Executor executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> hasNextAsync() {
        return CompletableFuture.supplyAsync(delegate::hasNext, executor);
    }

    @Override
    public CompletableFuture<T> nextAsync() {
        return CompletableFuture.supplyAsync(delegate::next, executor);
    }

    @Override
    public State getState() {
        return delegate.getState();
    }

    @Override
    public void setState(State state) {
        delegate.setState(state);
    }
}

// 示例测试类（可选）
class IteratorTest {
    public static void main(String[] args) throws Exception {
        ListResumableIterator<Integer> iter1 = new ListResumableIterator<>(Arrays.asList(1, 2));
        ListResumableIterator<Integer> iter2 = new ListResumableIterator<>(Collections.emptyList());
        ListResumableIterator<Integer> iter3 = new ListResumableIterator<>(Arrays.asList(3, 4));

        MultiResumableIterator<Integer> multi = new MultiResumableIterator<>(Arrays.asList(iter1, iter2, iter3));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AsyncWrapperResumableIterator<Integer> asyncIter = new AsyncWrapperResumableIterator<>(multi, executor);

        while (asyncIter.hasNextAsync().get()) {
            System.out.println("Async next: " + asyncIter.nextAsync().get());
        }

        executor.shutdown();

        // 可以加入 getState/setState 的测试
    }
}
