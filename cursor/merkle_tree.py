import hashlib

class Node:
    def __init__(self, data=None, left=None, right=None):
        self.left = left
        self.right = right
        self.parent = None
        
        # 如果是叶子节点，哈希数据本身；如果是中间节点，哈希两个子节点的组合
        if left is None and right is None:
            self.hash = self._hash_func(data)
        else:
            self.hash = self._hash_func(left.hash + right.hash)

        # 建立父子关系
        if left: left.parent = self
        if right: right.parent = self

    def _hash_func(self, data):
        return hashlib.sha256(str(data).encode()).hexdigest()

    def update_hash(self):
        """重新计算当前节点的哈希值"""
        if self.left and self.right:
            self.hash = self._hash_func(self.left.hash + self.right.hash)
        # 如果是叶子节点，hash 在外部修改 data 后由外部触发更新

class MerkleTree:
    def __init__(self, data_list):
        # 1. 构建叶子节点
        self.leaves = [Node(data=d) for d in data_list]
        # 2. 递归构建整棵树
        self.root = self._build_tree(self.leaves)

    def _build_tree(self, nodes):
        if len(nodes) == 1:
            return nodes[0]
        
        parents = []
        for i in range(0, len(nodes), 2):
            left = nodes[i]
            # 如果是奇数个，最后一个节点与自己配对（或者根据业务逻辑处理）
            right = nodes[i+1] if i+1 < len(nodes) else nodes[i]
            parents.append(Node(left=left, right=right))
        
        return self._build_tree(parents)

    def update_content(self, index, new_content):
        """
        这就是面试官说的 '一条链上去' 的逻辑
        """
        leaf = self.leaves[index]
        # 更新叶子节点哈希
        leaf.hash = leaf._hash_func(new_content)
        
        # 沿着 parent 链条向上追溯，直到 root
        curr = leaf.parent
        while curr:
            old_hash = curr.hash
            curr.update_hash()
            if old_hash == curr.hash:
                # 如果某一层哈希没变，上面的也不用更新了（优化点）
                break
            curr = curr.parent
        print(f"Updated index {index}. New Root: {self.root.hash[:10]}...")


def compare_trees(node_a, node_b, diff_indices=None):
    """
    递归对比两个节点，找出所有哈希不同的叶子节点索引
    """
    if diff_indices is None:
        diff_indices = []

    # 1. 如果哈希相同，说明这个分支完全一致，直接跳过
    if node_a.hash == node_b.hash:
        return diff_indices

    # 2. 如果哈希不同，且是叶子节点，说明找到了差异点
    if node_a.left is None and node_a.right is None:
        diff_indices.append(node_a.leaf_index)
        return diff_indices

    # 3. 如果哈希不同且是中间节点，递归对比左右子树
    if node_a.left and node_b.left:
        compare_trees(node_a.left, node_b.left, diff_indices)
    if node_a.right and node_b.right:
        compare_trees(node_a.right, node_b.right, diff_indices)
        
    return diff_indices
    
# --- 使用示例 ---
data = ["file1", "file2", "file3", "file4"]
tree = MerkleTree(data)
print(f"Initial Root: {tree.root.hash[:10]}...")

# 模拟 upload(filename, content)
tree.update_content(1, "file2_modified")
print(tree.root.hash)
print(tree.root.left.hash)
print(tree.leaves[0] == tree.root)