public class InsertIntoACircularLinkedList708 {
    static class Node {
        public int val;
        public Node next;
        public Node() {}
        public Node(int val, Node next) {
            this.val = val;
            this.next = next;
        }
    }

    public static void main(String[] args) {
        InsertIntoACircularLinkedList708 s = new InsertIntoACircularLinkedList708();
        Node n3 = new Node(1, null);
        Node n2 = new Node(2, n3);
        Node n1 = new Node(1, n2);
        n3.next = n1;
        s.print(n1);
        s.insert(n1, 0);
        s.print(n1);
    }

    public Node insert(Node head, int insertVal) {
        if (head == null) {
            Node n = new Node(insertVal, null);
            n.next = n;
            return n;
        }
        Node cur = head;
        while (true) {
            if (cur.val < cur.next.val) {
                if (cur.val <= insertVal && insertVal <= cur.next.val) {
                    cur.next = new Node(insertVal, cur.next);
                    break;
                }
            } else if (cur.val > cur.next.val) {
                if (cur.val <= insertVal || cur.next.val >= insertVal) {
                    cur.next = new Node(insertVal, cur.next);
                    break;
                }
            } else {
                // 这有个疑问
                if (cur.next == head) {
                    cur.next = new Node(insertVal, head);
                    break;
                }
            }
            cur = cur.next;
        }
        return head;
    }

    public void print(Node head) {
        Node c = head;
        while (c.next != head) {
            System.out.println(c.val);
            c = c.next;
        }
        System.out.println(c.val);
    }
}
