/*
Id allocator. Capacity 0-N.
        int Allocate() : returns avilable ID within 0-N range.
        int Release(int ID) : releases given ID and that ID becomes available for allocation.
        Write O(1) solution. After doing that the result was space (N).
        I had solved this one so gave the solution of O(1) where you don’t inititializa with N numbers in the queue.
        Total bytes = N * 4(int ID) + overhead of set + overhead of queue.
        Now interviewer asked:
        We want to save space we dont care about runtime time complexisty how would you save space?
        Here I allocated N bits in byte array and every bit represents the ID from 0-N. (didn’t have to code)
        bit value 0 represents avilable and 1 represent taken. The index/place of the bit in byte array represents int ID.
        The time complexity became O(N) because we have to traverse all byte array bit by bit
        and figure out which bit is availale and return the index/place of that bit in byte array.
        Interviewer then asked :
        Can we add some more space and improve rumtime instead of O(N).
        It was back and forth with some approaches then I came up with complete binary tree aaproach.
        node will represnet the range of bits .
        – ROOT node (int val, range 0-N)
        – ROOT.left (int val, range 0 - mid)
        – ROOT.right (int val, range mid+1 - N)
        If the value of the node is 1 then that whole range is taken (IDs are allocated)
        If the value of the node is 0 then there is a zero in the Node’s range.
        Then leaf node can contain the actual byte array.
        and then you do recursive traversal to left/right to find zero bit.
        The time complexity becomes O(logN)
        Space - the height is logN of the tree so :
        Node memory : 2^logN * ( 4 (bytes left pointer) + 4 (bytes right) +
        4 (bytes val) + 8 ( bytes for range) )
        Later I realized that node could represent one BYTE instead of every bit.
        so byte array can be presented in complete binary tree form.
        It was already 1 hr so we didnt discuss this approach further.
*/

public class IdAlloactor {
    int N;
    SegmentTree root;
    public IdAlloactor(int N) {
        this.N = N;
        this.root = buildTree(0, N-1);
    }

    public SegmentTree buildTree( int start, int end) {
        if (start == end) return new SegmentTree(start, end, 1);
        SegmentTree root = new SegmentTree(start, end);
        root.left = buildTree(start, (start + end) / 2);
        root.right = buildTree((start + end) / 2 + 1, end);
        root.num = root.left.num + root.right.num;
        return root;
    }

    public int allocate(){
        return allocate(root);
    }

    public int allocate(SegmentTree root) {
        if (root == null) {
            return -1;
        }
        // it's already full
        int val = -1;
        if (root.num == 0) return -1;
        if (root.start == root.end) {
            root.num = 0;
            return root.start;
        }
        if (root.left.num > 0) {
            val = allocate(root.left);
        } else {
            val = allocate(root.right);
        }
        root.num = root.left.num + root.right.num;
        return val;
    }

    public void release(int id) {
        release(root, 0, N - 1, id);
    }

    public void release(SegmentTree root, int start, int end, int id) {
        if (root.start == root.end && root.start == id) {
            root.num = 1;
            return;
        }
        int mid = (start + end ) / 2;
        if (id <= mid) {
            release(root.left, start, mid, id);
        } else {
            release(root.right, mid + 1, end, id);
        }
        root.num = root.left.num + root.right.num;
    }

    public static void main(String[] args) {
        IdAlloactor allocator = new IdAlloactor(3);
        System.out.println(allocator.allocate());
        System.out.println(allocator.allocate());
        System.out.println(allocator.allocate());
        System.out.println(allocator.allocate());
        allocator.release(1);
        System.out.println(allocator.allocate());
    }
}

class SegmentTree {
    int start;
    int end;
    int num;

    SegmentTree left;
    SegmentTree right;

    public SegmentTree(int start, int end, int num) {
        this.start = start;
        this.end = end;
        this.num = num;
    }

    public SegmentTree(int start, int end) {
        this.start = start;
        this.end = end;
    }
}
