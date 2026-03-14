import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

public class MemoryAllocatorV5 {

    class Block{
        int address;
        int size;
        
        public Block(int address, int size) {
            this.size = size;
            this.address = address;
        }
    }

    int capacity;
    TreeSet<Block> freeBlocks;
    Map<Integer, Block> startToBlock;
    Map<Integer, Block> endToBlock;
    Map<Integer, Integer> allocated;


    public MemoryAllocatorV5(int capacity) {
        this.capacity = capacity;
        this.startToBlock = new HashMap<>();
        this.endToBlock = new HashMap<>();
        this.allocated = new HashMap<>();
        this.freeBlocks = new TreeSet<>((a, b) -> {
            if (a.size != b.size) {
                return a.size - b.size;
            }
             return a.address - b.address;
        });
        addFreeBlock(new Block(0, capacity));
    }


    public int allocate(int size) {
        if(size <= 0) return 0;
        Block block = new Block(0, size);
        Block matchedBlock = freeBlocks.ceiling(block);
        if (matchedBlock == null) return 0;

        allocated.put(matchedBlock.address, size);
        removeFreeBlock(matchedBlock);

        if (matchedBlock.size > size) {
            addFreeBlock(new Block(matchedBlock.address + size, matchedBlock.size - size));
        }
        return matchedBlock.address;

    }

    public void free(int address, int size) {
        if (address < 0 || size < 0) return;
        if (allocated.getOrDefault(address, 0) != size) return;
        allocated.remove(address);

        int freeEnd = address + size;
        Block prev = endToBlock.get(address);
        Block next = startToBlock.get(freeEnd);

        if (prev != null && next != null) {
            removeFreeBlock(prev);
            removeFreeBlock(next);
            addFreeBlock(new Block(prev.address, prev.size + size + next.size));
        } else if (prev != null) {
            removeFreeBlock(prev);
            addFreeBlock(new Block(prev.address, prev.size + size));
        } else if (next != null) {
            removeFreeBlock(next);
            addFreeBlock(new Block(address, next.size + size));
        } else {
            addFreeBlock(new Block(address, size));
        }

    }

    private void removeFreeBlock(Block block) {
        freeBlocks.remove(block);
        startToBlock.remove(block.address);
        endToBlock.remove(block.size+ block.address);
    }

    private void addFreeBlock(Block block) {
        freeBlocks.add(block);
        startToBlock.put(block.address, block);
        endToBlock.put(block.size + block.address, block);
    }
    
}
