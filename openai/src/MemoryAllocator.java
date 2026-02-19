import java.util.*;

/**
 * Memory Allocator - 类似 malloc/free
 *
 * Part 1: 基本实现
 * - First-fit 分配策略
 * - 释放时合并相邻空闲块（coalescing）
 *
 * Part 2: 复杂度与改进
 *
 * Time Complexity:
 * - allocate: O(n) - 可能遍历所有空闲块才能找到合适位置
 * - free: O(n) - 查找已分配块 O(n)，合并相邻块 O(n)
 *
 * Space Complexity:
 * - O(m)，m = 空闲块数量。碎片化严重时 m 会很大
 *
 * Weaknesses:
 * - 外部碎片：随时间产生许多小空隙。例如总共 100 字节空闲但分散在 10 个 10 字节块中，无法分配 50 字节
 * - 线性搜索：遍历列表 O(n)，块多时变慢
 * - 无法移动：不能移动已分配数据来填补空隙（需支持内存拷贝/重定位）
 *
 * 改进方向：
 * - Best-fit / Worst-fit 减少碎片
 * - 按大小分桶（segregated lists）加速查找
 * - 压缩/整理（compaction）移动块消除碎片
 */
public class MemoryAllocator {

    private final int totalCapacity;
    private final List<Block> freeBlocks;
    private final List<Block> allocatedBlocks;

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

    public MemoryAllocator(int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("totalCapacity must be positive");
        }
        this.totalCapacity = totalCapacity;
        this.freeBlocks = new ArrayList<>();
        this.allocatedBlocks = new ArrayList<>();
        freeBlocks.add(new Block(0, totalCapacity));
    }

    /**
     * 分配 size 字节，返回起始地址
     */
    public int allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        // First-fit: 找第一个足够大的空闲块
        for (int i = 0; i < freeBlocks.size(); i++) {
            Block b = freeBlocks.get(i);
            if (b.size >= size) {
                freeBlocks.remove(i);
                allocatedBlocks.add(new Block(b.start, size));

                // 若有剩余，放回空闲列表
                if (b.size > size) {
                    freeBlocks.add(new Block(b.start + size, b.size - size));
                    freeBlocks.sort(Comparator.comparingInt(x -> x.start));
                }
                return b.start;
            }
        }
        throw new IllegalStateException("Not enough contiguous memory");
    }

    /**
     * 释放从 address 开始的 size 字节
     */
    public void free(int address, int size) {
        if (address < 0 || address >= totalCapacity) {
            throw new IllegalArgumentException("address out of bounds");
        }
        if (address + size > totalCapacity) {
            throw new IllegalArgumentException("address + size exceeds capacity");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }

        // 查找是否已分配
        for (int i = 0; i < allocatedBlocks.size(); i++) {
            Block b = allocatedBlocks.get(i);
            if (b.start == address && b.size == size) {
                allocatedBlocks.remove(i);
                // 加入空闲并合并相邻块
                coalesce(new Block(address, size));
                return;
            }
        }
        throw new IllegalStateException("Block was not allocated");
    }

    private void coalesce(Block newFree) {
        freeBlocks.add(newFree);
        freeBlocks.sort(Comparator.comparingInt(x -> x.start));

        // 合并相邻块
        boolean merged;
        do {
            merged = false;
            for (int i = 0; i < freeBlocks.size() - 1; i++) {
                Block a = freeBlocks.get(i);
                Block b = freeBlocks.get(i + 1);
                if (a.end() == b.start) {
                    freeBlocks.remove(i + 1);
                    freeBlocks.set(i, new Block(a.start, a.size + b.size));
                    merged = true;
                    break;
                }
            }
        } while (merged);
    }

    public List<Block> getFreeBlocks() { return new ArrayList<>(freeBlocks); }
    public List<Block> getAllocatedBlocks() { return new ArrayList<>(allocatedBlocks); }

    /** 调试用：打印当前状态 */
    public String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("Free: ").append(freeBlocks).append("\n");
        sb.append("Allocated: ").append(allocatedBlocks);
        return sb.toString();
    }

    public static void main(String[] args) {
        MemoryAllocator alloc = new MemoryAllocator(100);

        // 官方测试用例
        int addr1 = alloc.allocate(20);
        if (addr1 != 0) throw new AssertionError("addr1 should be 0, got " + addr1);

        int addr2 = alloc.allocate(30);
        if (addr2 != 20) throw new AssertionError("addr2 should be 20, got " + addr2);

        int addr3 = alloc.allocate(40);
        if (addr3 != 50) throw new AssertionError("addr3 should be 50, got " + addr3);

        alloc.free(20, 30);
        // [Allocated(0-19)] [Free(20-49)] [Allocated(50-89)] [Free(90-99)]

        int addr4 = alloc.allocate(25);
        if (addr4 != 20) throw new AssertionError("addr4 should be 20, got " + addr4);
        // [Allocated(0-19)] [Allocated(20-44)] [Free(45-49)] [Allocated(50-89)] [Free(90-99)]

        alloc.free(0, 20);
        // [Free(0-19)] [Allocated(20-44)] [Free(45-49)] [Allocated(50-89)] [Free(90-99)]

        alloc.free(20, 25);
        // [Free(0-49)] [Allocated(50-89)] [Free(90-99)] - 合并左右两侧
        List<Block> free = alloc.getFreeBlocks();
        List<Block> allocd = alloc.getAllocatedBlocks();
        if (free.size() != 2) throw new AssertionError("Should have 2 free blocks");
        if (free.get(0).start != 0 || free.get(0).size != 50) throw new AssertionError("First free: 0-49");
        if (free.get(1).start != 90 || free.get(1).size != 10) throw new AssertionError("Second free: 90-99");
        if (allocd.size() != 1 || allocd.get(0).start != 50 || allocd.get(0).size != 40) throw new AssertionError("Allocated: 50-89");

        System.out.println("All tests passed!");
        System.out.println(alloc.status());
    }
}
