import hashlib

class Node:

    def __init__(self, left=None, right=None, data=None):
        self.left = left
        self.right = right
        self.parent = None
        self.data = data

        if left is None and right is None:
            self.hash = self._hash_func(data);
        else:
            self.hash = self._hash_func(left.hash + right.hash)

        if left:
            left.parent = self
        if right:
            right.parent = self
    
    def _hash_func(self, data):
        return hashlib.sha256(str(data).encode()).hexdigest()
    
    def update_hash(self):
        if self.left and self.right:
            self.hash = self._hash_func(self.left.hash + self.right.hash)

class MerkleTree2:
    def __init__(self, data_list):
       self.leaves = [Node(data=d) for d in data_list]
       self.root = self._build_tree(self.leaves)   

    def _build_tree(self, nodes) -> Node:
        if len(nodes) == 1:
            return nodes[0]
        
        parents = []
        for i in range(0, len(nodes), 2):
            left = nodes[i]
            right = nodes[i + 1] if i + 1 < len(nodes) else nodes[i]
            parents.append(Node(left=left, right=right))
    
        return self._build_tree(parents)

    def update_content(self, index, new_content):
        leaf = self.leaves[index]
        leaf.hash = leaf._hash_func(new_content)

        curr = leaf.parent
        while curr:
            old_hash = curr.hash
            curr.update_hash()
            if old_hash == curr.hash:
                break
            curr = curr.parent
        
        print(f"Updated index {index}. New Root: {self.root.hash[:10]}...")

def compare_tree(node_a, node_b, result=None) -> list:
    if result is None:
        result = []
    if node_a.hash == node_b.hash:
        return result

    if node_a.left is None and node_a.right is None:
        result.append(node_a)
        return result
    
    if node_a.left and node_b.left:
        left_result = compare_tree(node_a.left, node_b.left)
    if node_a.right and node_b.right:
        right_result = compare_tree(node_a.right, node_b.right)
    
    result.extend(left_result)
    result.extend(right_result)
    return result
        
data = ["file1", "file2", "file3", "file4"]
tree = MerkleTree2(data)
print(f"Initial Root: {tree.root.hash[:10]}...")

# 模拟 upload(filename, content)
tree.update_content(1, "file2_modified")
print(tree.root.hash)
print(tree.root.left.hash)
print(tree.leaves[0] == tree.root)