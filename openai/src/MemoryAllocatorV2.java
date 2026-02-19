import java.util.*;

/**
 * Memory Allocator V2 - 参考 Python 实现
 * - 空闲块用双向链表，按地址有序
 * - 分配时整块移除或原地缩小（不新建节点）
 * - 释放时一次判断与 prev/next 合并
 *
 * ========== How to Optimize ==========
 *
 * --- To Fix Fragmentation ---
 *
 * 1. Segregated Free Lists（按大小分桶）
 *    - 维护多条空闲链表，按块大小分类（如：小块 / 中块 / 大块）
 *    - 分配时先查对应大小的桶，再 fallback 到更大桶
 *    - 优点：查找更快、大块不会被小块请求拆碎
 *
 * 2. Best-Fit Strategy（最佳适应）
 *    - 不取第一个够用的块，而是遍历所有空闲块，选「能装下且最小的」
 *    - 优点：减少外部碎片、大块留给大请求
 *    - 缺点：每次分配 O(n) 搜索，比 First-Fit 慢
 *
 * 3. Buddy System（伙伴系统）
 *    - 块大小只能是 2 的幂（2, 4, 8, 16...），向上取整到 2^k
 *    - 大块可对半分裂成两个「伙伴」；释放时若伙伴空闲则合并
 *    - 合并只需查伙伴是否空闲，O(1)，且无碎片（同大小块对齐）
 *
 * --- To Improve Speed ---
 *
 * 4. Balanced BST（平衡二叉搜索树）
 *    - 以块大小或起始地址为 key，用红黑树/AVL 存空闲块
 *    - 查找「第一个 ≥ size」或「最小满足 size」：O(log n)
 *    - 插入/删除合并后的块：O(log n)
 *
 * 5. Bitmap（位图）
 *    - 仅当所有块大小相同时适用（如固定 4KB 页）
 *    - 用一位表示一页是否空闲，分配/释放即找位+置位，可近似 O(1)
 *
 * --- To Improve Space ---
 *
 * 6. Implicit Free List（隐式空闲链表）
 *    - 不在外部维护链表，在内存块内部写 header：块大小 + 是否空闲
 *    - 遍历时按「当前块起始 + 当前块长度」跳到下一块
 *    - 省掉指针空间，但遍历仍是 O(n)
 *
 * 7. Boundary Tags（边界标记）
 *    - 每块头部和尾部都存：大小 + 是否空闲（footer 便于从后往前看）
 *    - 释放时可根据前一块的 footer 在 O(1) 找到前一块并合并
 *    - 配合隐式链表，合并前后块都不用扫整条链
 *
 * ========== 进阶问题与实现思路 ==========
 *
 * --- 1. Alignment（对齐，如地址必须是 8 的倍数）---
 * 做法：
 * - 分配时：找到空闲块后，将「可用起始地址」向上对齐到 align。
 *   aligned_start = (block.start + align - 1) / align * align;
 * - 若 block 内 [aligned_start, aligned_start+size) 仍完全在 block 内，则从 aligned_start 切块；
 *   否则跳过该块，找下一块。
 * - 块的实际「可用」区域是 [aligned_start, aligned_start+size)；块头可能保留
 *   [block.start, aligned_start) 作为 padding（可记入块头或单独小空闲块）。
 * - 实现：allocate(size, align) 中先按 size + (align-1) 预留，再算对齐起始；
 *   free 时仍按实际分配时记录的 (address, size) 释放。
 *
 * --- 2. 如何防止用户释放正在使用的内存（非法 free）---
 * 做法（本实现已做）：
 * - 用 allocated 表记录所有已分配块：address -> (start, size)。
 * - free(address, size) 时：
 *   a) 检查 address 是否在 [0, capacity) 且 address+size <= capacity；
 *   b) 检查 allocated.get(address) 是否存在且记录的 size 与传入 size 一致；
 *   c) 若不存在或 size 不符，抛异常（Invalid free / double free / 非法地址）。
 * - 可选：每个分配块给唯一 id，free 时传 id 而非 (address, size)，防止 use-after-free 后再次误 free。
 *
 * --- 3. 如何实现 realloc()（在保留数据的前提下调整块大小）---
 * 思路：
 * - realloc(old_address, new_size)：
 *   a) 若 new_size <= 当前块 size：可原地「缩小」，只更新元数据，返回原 address；
 *   b) 若当前块后方有连续空闲且 当前size + 后继空闲 >= new_size：扩展当前块（合并后继空闲），
 *      不拷贝数据，返回原 address；
 *   c) 否则：allocate(new_size) 得到新块，将旧块数据拷贝到新块，free(old_address, old_size)，
 *      返回新 address。
 * - 需要 allocated 里记录每块 size，以便知道当前块大小和拷贝长度。
 *
 * --- 4. 如何做到 Thread-Safe（线程安全）---
 * 做法：
 * - 对 allocate / free / realloc 等修改内部状态的方法加同一把锁（如 ReentrantLock 或 synchronized）。
 * - 所有访问 freeListHead、allocated、以及任何空闲/已分配结构的地方都在锁内执行。
 * - 若用 BST/树等结构，仍用一把全局锁即可；更细粒度（如 per-bucket 锁）可减少争用但实现复杂。
 * - 避免在持锁时调用用户回调或外部 I/O，防止死锁和长时间占锁。
 *
 * --- 5. 与真实硬件/OS 内存管理的区别 ---
 * - 本实现是「用户态堆分配器」、单进程内的一段连续「虚拟」区间；不涉及物理页、MMU、缺页。
 * - 真实 OS：物理页分配、虚拟地址映射、TLB、多级页表、按页对齐（如 4KB）；本实现只是逻辑上的字节数组。
 * - 真实硬件：有 cache line 对齐、DMA 对齐、NUMA 等；本实现不涉及。
 * - 本实现不提供内存保护（如只读/不可执行），不涉及进程隔离；真实 OS 有权限位和地址空间隔离。
 */
public class MemoryAllocatorV2 {

    static class FreeBlock {
        int start;
        int size;
        FreeBlock next;
        FreeBlock prev;

        FreeBlock(int start, int size) {
            this.start = start;
            this.size = size;
        }

        int end() { return start + size; }
        @Override public String toString() { return "[" + start + "," + size + "]"; }
    }

    private final int capacity;
    private FreeBlock freeListHead;
    private final Map<Integer, FreeBlock> allocated; // address -> block (start, size)

    public MemoryAllocatorV2(int totalCapacity) {
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("Total capacity must be positive");
        }
        this.capacity = totalCapacity;
        this.freeListHead = new FreeBlock(0, totalCapacity);
        this.allocated = new HashMap<>();
    }

    public int allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Allocation size must be positive");
        }

        FreeBlock current = freeListHead;
        while (current != null) {
            if (current.size >= size) {
                int addr = current.start;

                if (current.size == size) {
                    removeFreeBlock(current);
                } else {
                    current.start += size;
                    current.size -= size;
                }

                allocated.put(addr, new FreeBlock(addr, size));
                return addr;
            }
            current = current.next;
        }

        throw new IllegalStateException("Cannot allocate " + size + ": insufficient contiguous memory");
    }

    public void free(int address, int size) {
        if (address < 0 || address >= capacity) {
            throw new IllegalArgumentException("Invalid address: " + address);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }
        if (address + size > capacity) {
            throw new IllegalArgumentException("Free range exceeds memory bounds");
        }

        FreeBlock block = allocated.get(address);
        if (block == null || block.size != size) {
            throw new IllegalArgumentException("Invalid free: no allocation at " + address + " with size " + size);
        }
        allocated.remove(address);

        int freedEnd = address + size;

        FreeBlock current = freeListHead;
        FreeBlock prevBlock = null;

        while (current != null && current.start < address) {
            prevBlock = current;
            current = current.next;
        }

        boolean mergePrev = prevBlock != null && prevBlock.end() == address;
        boolean mergeNext = current != null && freedEnd == current.start;

        if (mergePrev && mergeNext) {
            prevBlock.size += size + current.size;
            removeFreeBlock(current);
        } else if (mergePrev) {
            prevBlock.size += size;
        } else if (mergeNext) {
            current.start = address;
            current.size += size;
        } else {
            insertFreeBlockAfter(prevBlock, new FreeBlock(address, size));
        }
    }

    private void removeFreeBlock(FreeBlock block) {
        if (block.prev != null) {
            block.prev.next = block.next;
        } else {
            freeListHead = block.next;
        }
        if (block.next != null) {
            block.next.prev = block.prev;
        }
    }

    private void insertFreeBlockAfter(FreeBlock prev, FreeBlock newBlock) {
        if (prev == null) {
            newBlock.next = freeListHead;
            if (freeListHead != null) {
                freeListHead.prev = newBlock;
            }
            freeListHead = newBlock;
        } else {
            newBlock.next = prev.next;
            newBlock.prev = prev;
            if (prev.next != null) {
                prev.next.prev = newBlock;
            }
            prev.next = newBlock;
        }
    }

    public int getFreeMemory() {
        int total = 0;
        for (FreeBlock p = freeListHead; p != null; p = p.next) {
            total += p.size;
        }
        return total;
    }

    public int getLargestFreeBlock() {
        int max = 0;
        for (FreeBlock p = freeListHead; p != null; p = p.next) {
            max = Math.max(max, p.size);
        }
        return max;
    }

    public String status() {
        List<String> free = new ArrayList<>();
        for (FreeBlock p = freeListHead; p != null; p = p.next) {
            free.add(p.toString());
        }
        return "Free: " + free + "\nAllocated: " + allocated;
    }

    public static void main(String[] args) {
        // 测试用例：与题目注释一致
        MemoryAllocatorV2 alloc = new MemoryAllocatorV2(100);

        int addr1 = alloc.allocate(20);
        if (addr1 != 0) throw new AssertionError("addr1 should be 0");
        // Memory: [Allocated(0-19)] [Free(20-99)]

        int addr2 = alloc.allocate(30);
        if (addr2 != 20) throw new AssertionError("addr2 should be 20");
        // Memory: [Allocated(0-19)] [Allocated(20-49)] [Free(50-99)]

        int addr3 = alloc.allocate(40);
        if (addr3 != 50) throw new AssertionError("addr3 should be 50");
        // Memory: [Allocated(0-19)] [Allocated(20-49)] [Allocated(50-89)] [Free(90-99)]

        alloc.free(20, 30);
        // Memory: [Allocated(0-19)] [Free(20-49)] [Allocated(50-89)] [Free(90-99)]

        int addr4 = alloc.allocate(25);
        if (addr4 != 20) throw new AssertionError("addr4 should be 20 (use the gap we just made)");
        // Memory: [Allocated(0-19)] [Allocated(20-44)] [Free(45-49)] [Allocated(50-89)] [Free(90-99)]

        alloc.free(0, 20);
        // Memory: [Free(0-19)] [Allocated(20-44)] [Free(45-49)] [Allocated(50-89)] [Free(90-99)]

        alloc.free(20, 25);
        // Memory: [Free(0-49)] [Allocated(50-89)] [Free(90-99)] - merges with BOTH sides

        // 验证最终状态: [Free(0-49)] [Allocated(50-89)] [Free(90-99)]
        if (alloc.getFreeMemory() != 60) throw new AssertionError("Total free should be 60");
        if (alloc.getLargestFreeBlock() != 50) throw new AssertionError("Largest free block should be 50");

        System.out.println("All tests passed.");
        System.out.println(alloc.status());
    }
}
