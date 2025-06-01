public class LowestCommonAncestorOfBTII1644 {
    class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;
    }

    class Result {
        TreeNode root;
        int count;

        public Result(TreeNode r, int count) {
            this.root = r;
            this.count = count;
        }
    }

    public TreeNode lowest(TreeNode root, TreeNode p, TreeNode q) {
        Result r  = dfs(root, p, q);
        return r.root;
    }

    public Result dfs(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null) return new Result(null, 0);
        Result l = dfs(root.left, p, q);
        Result r = dfs(root.right, p, q);

        if (root == p || root == q) {
            return new Result(root, 1 + l.count + r.count);
        }

        if (l.root != null && r.root != null) return new Result(root, 2);
        return l.root == null ? r : l;
    }
}
