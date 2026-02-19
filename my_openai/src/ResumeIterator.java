import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

interface State extends Serializable {}

interface ResumeIterator<T> extends Iterator<T> {
    State getState();
    void setState(State state);
} 

class ListResumeIterator<T> implements ResumeIterator<T> {  // ✅ 修正接口名

    private final List<T> list;
    private int index;

    public ListResumeIterator(List<T> list) {
        this.list = list;
        this.index = 0;
    }

    public State getState() {
        return new ListState(this.index);
    }

    public void setState(State state) {
        if (!(state instanceof ListState)) throw new IllegalArgumentException("Invalid state");
        this.index = ((ListState) state).getIndex();  // ✅ 使用 getter
    }

    public boolean hasNext() {
        return index < list.size();
    }

    public T next() {
        if (!hasNext()) throw new NoSuchElementException();  // ✅ 符合 Iterator 规范
        return list.get(index++);
    }

    static class ListState implements State {
        private static final long serialVersionUID = 1L;  // ✅ 添加 serialVersionUID
        private final int index;  // ✅ 私有化 + final

        public ListState(int index) {
            this.index = index;
        }
        
        public int getIndex() { return index; }  // ✅ 添加 getter
    }
}

class MultiResumableIterator<T> implements ResumeIterator<T> {  // ✅ 修正接口名

    private List<ListResumeIterator<T>> iterators;
    private int outerIndex;

    public MultiResumableIterator(List<ListResumeIterator<T>> iterators) {
        this.iterators = iterators;
        this.outerIndex = 0;

    }

    private void skipEmpty() {
        while (outerIndex < iterators.size() && !iterators.get(outerIndex).hasNext()) {
            outerIndex ++;
        }
    }

    @Override
    public boolean hasNext() {
        skipEmpty();
        return outerIndex < iterators.size();
    }

    @Override
    public T next() {
        if (!hasNext()) throw new NoSuchElementException();  // ✅ 符合 Iterator 规范
        return iterators.get(outerIndex).next();
    }

    @Override
    public State getState() {
        return new MultiState(outerIndex, 
            outerIndex < iterators.size()? iterators.get(outerIndex).getState() : null);
    }

    @Override
    public void setState(State state) {
        if (!(state instanceof MultiState)) throw new IllegalArgumentException("Invalid state");
        MultiState ms = (MultiState) state;
        this.outerIndex = ms.getOuterIndex();  // ✅ 使用 getter
        
        if (ms.getState() != null && outerIndex < iterators.size()) {  // ✅ 使用 getter
            iterators.get(outerIndex).setState(ms.getState());
        }
    }

    static class MultiState implements State {
        private static final long serialVersionUID = 1L;  // ✅ 添加 serialVersionUID
        private final int outerIndex;  // ✅ 私有化 + final
        private final State state;     // ✅ 私有化 + final

        public MultiState(int outerIndex, State state) {
            this.outerIndex = outerIndex;
            this.state = state;
        }
        
        public int getOuterIndex() { return outerIndex; }  // ✅ 添加 getter
        public State getState() { return state; }          // ✅ 添加 getter
    }
}

class TwoDResumeIterator<T> implements ResumeIterator<T> {  // ✅ 统一命名
    private final List<List<T>> matrix;
    private int outer;
    private int inner;

    public TwoDResumeIterator(List<List<T>> matrix) {  // ✅ 修正构造函数名
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
        if (hasNext()) return matrix.get(outer).get(inner ++);
        throw new NoSuchElementException();
    }

    @Override
    public State getState() {
        return new TwoDState(outer, inner);
    }

    @Override
    public void setState(State state) {
        if (!(state instanceof TwoDState)) throw new IllegalArgumentException("Invalid state");
        TwoDState s = (TwoDState) state;
        this.outer = s.getOuter();  // ✅ 使用 getter
        this.inner = s.getInner();  // ✅ 使用 getter
    }

    public static class TwoDState implements State {
        private static final long serialVersionUID = 1L;
        private final int outer;  // ✅ 添加 final
        private final int inner;  // ✅ 添加 final

        public TwoDState(int outer, int inner) {
            this.outer = outer;
            this.inner = inner;
        }
        
        public int getOuter() { return outer; }  // ✅ 添加 getter
        public int getInner() { return inner; }  // ✅ 添加 getter
    }
}

// AsyncResumeIterator 用协程方式模拟异步（使用 CompletableFuture）
abstract class AsyncResumeIterator<T> {  // ✅ 统一命名
    public abstract CompletableFuture<Boolean> hasNextAsync();
    public abstract CompletableFuture<T> nextAsync();
    public abstract State getState();
    public abstract void setState(State state);
}

class AsyncWrapperResumeIterator<T> extends AsyncResumeIterator<T> {  // ✅ 统一命名
    private final ResumeIterator<T> delegate;  // ✅ 修正类型
    private final Executor executor;

    public AsyncWrapperResumeIterator(ResumeIterator<T> delegate, Executor executor) {  // ✅ 修正类型
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Boolean> hasNextAsync() {
        return CompletableFuture.supplyAsync(() -> delegate.hasNext(), executor);
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
