import java.util.*;

public class ReportChainDemo {
    public static void main(String[] args) {
        String[] relationships = {"A,B,C", "B,D,E", "C,F,G", "G,H"};
        ReportChain rc = new ReportChain(relationships);
        
        // System.out.println("=== Part 1: Print Tree ===");
        // rc.printTree();
        
        System.out.println("\n=== Part 2: Skip Meeting Pairs ===");
        rc.printSkipMeetings();
        
        // System.out.println("\n=== Part 3: Chain for B ===");
        // rc.printChain("B");
        
        // System.out.println("\n=== Part 4: LCA for C and E ===");
        // String lca = rc.findLCA("C", "E");
        // System.out.println("LCA: " + (lca != null ? lca : "None"));
        
        // System.out.println("\n=== Part 4: LCA for A and D ===");
        // lca = rc.findLCA("A", "D");
        // System.out.println("LCA: " + (lca != null ? lca : "None"));
    }
}
