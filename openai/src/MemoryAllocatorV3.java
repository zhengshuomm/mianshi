import java.util.*;

/**
 * Memory Allocator V3 - Balanced BST 实现
 * - 空闲块按 (size, start) 放入 TreeSet，分配时 ceiling(size) 即最小满足块，O(log n)
 * - startToBlock / endToBlock 用于 free 时 O(1) 查前后块并合并
 */
public class MemoryAllocatorV3 {

    static class Block {
        int start;
        int size;

        Block(int start, int size) {
            this.start = start;
            this.size = size;
        }

        int end() { return start + size; }
        @Override public String toString() { return "[" + start + "," + size + "]"; }
    }

    private static final Comparator<Block> BY_SIZE_THEN_START =
        Comparator.comparingInt((Block b) -> b.size).thenComparingInt(b -> b.start);

    private final int capacity;
    private final TreeSet<Block> freeBySize;   // 按 (size, start)，用于 allocate 找最小满足块
    private final TreeMap<Integer, Block> startToBlock; // start -> block，用于 free 找 next
    private final TreeMap<Integer, Block> endToBlock;   // end -> block，用于 free 找 prev
    private final Map<Integer, Block> allocated;        // address -> block

    public MemoryAllocatorV3(int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("Total capacity must be positive");
        }
        this.capacity = totalCapacity;
        this.freeBySize = new TreeSet<>(BY_SIZE_THEN_START);
        this.startToBlock = new TreeMap<>();
        this.endToBlock = new TreeMap<>();
        this.allocated = new HashMap<>();
        addFreeBlock(new Block(0, totalCapacity));
    }

    private void addFreeBlock(Block b) {
        freeBySize.add(b);
        startToBlock.put(b.start, b);
        endToBlock.put(b.end(), b);
    }

    private void removeFreeBlock(Block b) {
        freeBySize.remove(b);
        startToBlock.remove(b.start);
        endToBlock.remove(b.end());
    }

    public int allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Allocation size must be positive");
        }

        // 最小块且 size >= size：用 (0, size) 做 ceiling key
        Block key = new Block(0, size);
        Block block = freeBySize.ceiling(key);
        if (block == null) {
            throw new IllegalStateException("Cannot allocate " + size + ": insufficient contiguous memory");
        }

        removeFreeBlock(block);
        int addr = block.start;

        if (block.size > size) {
            addFreeBlock(new Block(addr + size, block.size - size));
        }

        allocated.put(addr, new Block(addr, size));
        return addr;
    }

    public void free(int address, int size) {
        if (address < 0 || address >= capacity) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
        if (size <= 0 || address + size > capacity) {
            throw new IllegalArgumentException("Invalid size or range");
        }

        Block block = allocated.get(address);
        if (block == null || block.size != size) {
            throw new IllegalArgumentException("Invalid free: no allocation at " + address + " with size " + size);
        }
        allocated.remove(address);

        int freedEnd = address + size;
        Block prev = endToBlock.get(address);   // 前一块：end == address
        Block next = startToBlock.get(freedEnd); // 后一块：start == address+size

        if (prev != null && next != null) {
            removeFreeBlock(prev);
            removeFreeBlock(next);
            prev.size += size + next.size;
            addFreeBlock(prev);
        } else if (prev != null) {
            removeFreeBlock(prev);
            prev.size += size;
            addFreeBlock(prev);
        } else if (next != null) {
            removeFreeBlock(next);
            next.start = address;
            next.size += size;
            addFreeBlock(next);
        } else {
            addFreeBlock(new Block(address, size));
        }
    }

    public int getFreeMemory() {
        int total = 0;
        for (Block b : freeBySize) total += b.size;
        return total;
    }

    public int getLargestFreeBlock() {
        return freeBySize.isEmpty() ? 0 : freeBySize.last().size;
    }

    public String status() {
        return "Free(bySize): " + freeBySize + "\nAllocated: " + allocated;
    }

    public static void main(String[] args) {
        MemoryAllocatorV3 alloc = new MemoryAllocatorV3(100);

        int addr1 = alloc.allocate(20);
        int addr2 = alloc.allocate(30);
        int addr3 = alloc.allocate(40);
        alloc.free(20, 30);
        int addr4 = alloc.allocate(25);
        alloc.free(0, 20);
        alloc.free(20, 25);

        if (addr1 != 0 || addr2 != 20 || addr3 != 50 || addr4 != 20) throw new AssertionError("addresses");
        if (alloc.getFreeMemory() != 60 || alloc.getLargestFreeBlock() != 50) throw new AssertionError("final state");

        System.out.println("All tests passed.");
        System.out.println(alloc.status());
    }
}
