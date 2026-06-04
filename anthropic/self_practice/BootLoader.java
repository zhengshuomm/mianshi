package anthropic.self_practice;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Instruction {
    String op;
    int val;

    public Instruction(String op, int val) {
        this.op =  op;
        this.val = val;
    }
}

class RunResult {
    int accumulate;
    boolean success;

    public RunResult(int accumulate, boolean success) {
        this.accumulate = accumulate;
        this.success = success;
    }
}

public class BootLoader {
    public List<Instruction> readFile(){
        List<Instruction> res = new ArrayList<>();
        try {
            
            BufferedReader read = new BufferedReader(new FileReader("/Users/szheng/code/mianshi/anthropic/self_practice/test.txt"));
            String line;
            while ( (line = read.readLine()) != null) {
                String[] lines = line.trim().split(" ");
                String op = lines[0];
                String val = lines[1].replace("+", "");
                res.add(new Instruction(op, Integer.parseInt(val)));
            }
        } catch (Exception e) {

        }
        return res;
    }

    public RunResult execute(List<Instruction> instructions) {
        Set<Integer> visited = new HashSet<>();
        int pointer = 0;
        int accumulate = 0;
        while (pointer >= 0 && pointer < instructions.size()) {
            if (!visited.add(pointer)) {
                return new RunResult(accumulate, false);
            }
            Instruction cur = instructions.get(pointer);
            switch (cur.op) {
                case "next":
                    pointer ++;
                    break;
                case "jump":
                    pointer += cur.val;
                    break;
                case "plus":
                    accumulate += cur.val;
                    pointer ++;
                    break;
                default:
                    break;
            }
        }
        return new RunResult(accumulate, true);
    }

    public int fixAndRun(List<Instruction> instructions) {
        for (int i  = 0 ; i < instructions.size() ; i ++) {
            Instruction cur = instructions.get(i);
            if (cur.op.equals("plus")) {
                continue;
            }

            String originalOp = cur.op;
            cur.op = originalOp.equals("next") ? "jump" : "next";

            RunResult r  = execute(instructions);
            if (r.success) {
                return r.accumulate;
            }
            cur.op = originalOp;
        }
        return -1;
    }
}
