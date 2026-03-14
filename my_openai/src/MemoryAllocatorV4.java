import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class MemoryAllocatorV4 {

    class Block {
        int start;
        int size;

        public Block(int start, int size) {
            this.start = start;
            this.size = size;
        }

        public int end() {
            return this.start + this.size;
        }
        @Override public String toString() { return "[" + start + "," + size + "]"; }

    }

    TreeSet<Block> freeBlocks;
    TreeMap<Integer, Block> startToBlock;
    TreeMap<Integer, Block> endToBlock;
    Map<Integer, Integer> allocated;
    int capacity;

    private static final Comparator<Block> BY_SIZE_THEN_START =
    Comparator.comparingInt((Block b) -> b.size).thenComparingInt(b -> b.start);
    
    public MemoryAllocatorV4(int capacity) {
        this.capacity = capacity;
        freeBlocks = new TreeSet<>(BY_SIZE_THEN_START);
        startToBlock = new TreeMap<>();
        endToBlock = new TreeMap<>();
        allocated = new HashMap<>();
        addFreeBlock(new Block(0, capacity));
    }

    private void addFreeBlock(Block block) {
        freeBlocks.add(block);
        startToBlock.put(block.start, block);
        endToBlock.put(block.end(), block);
    }

    private void removeFreeBlock(Block block) {
        freeBlocks.remove(block);
        startToBlock.remove(block.start);
        endToBlock.remove(block.end());
    }


    public int allocate(int size) {
        if (size <= 0) return 0;
        Block block = new Block(0, size);
        Block matchedBlock = freeBlocks.ceiling(block);
        if (matchedBlock == null) return 0;

        // freeBlocks.remove(matchedBlock);
        removeFreeBlock(matchedBlock);
        int startAddress = matchedBlock.start;

        if (matchedBlock.size > size) {
            // freeBlocks.add(new Block(startAddress + size, matchedBlock.size - size));
            addFreeBlock(new Block(startAddress + size, matchedBlock.size - size));
        }
        allocated.put(startAddress, size);
        return startAddress;
    }

    public void free(int address, int size) {
        if (size < 0 || address < 0) return;
        if (!allocated.containsKey(address) || allocated.get(address) != size) return;

        allocated.remove(address);
        int endAddress = address + size;

        Block prev = endToBlock.get(address);   // 左侧空闲块：其 end == 当前释放区间的 start
        Block next = startToBlock.get(endAddress); // 右侧空闲块：其 start == 当前释放区间的 end

        if (prev != null && next != null) {
            removeFreeBlock(prev);
            removeFreeBlock(next);
            addFreeBlock(new Block(prev.start, prev.size + size + next.size));
        } else if (prev != null) {
            removeFreeBlock(prev);
            addFreeBlock(new Block(prev.start, prev.size + size));
        } else if (next != null) {
            removeFreeBlock(next);
            addFreeBlock(new Block(address, next.size + size));
        } else {
            addFreeBlock(new Block(address, size));
        }
    }

    public int getFreeMemory() {
        int total = 0;
        for (Block b : freeBlocks) total += b.size;
        return total;
    }

    public int getLargestFreeBlock() {
        return freeBlocks.isEmpty() ? 0 : freeBlocks.last().size;
    }

    public String status() {
        return "Free(bySize): " + freeBlocks + "\nAllocated: " + allocated;
    }

    public static void main(String[] args) {
        MemoryAllocatorV4 alloc = new MemoryAllocatorV4(100);

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
