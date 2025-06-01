import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Canvas {
    int m;
    int n;

    int maxM;
    int maxN;

    Map<Character, Rect> map;
    List<Rect> list;
    public Canvas(int m, int n) {
        this.m = m;
        this.n = n;
        this.maxM = m;
        this.maxN = n;
        map = new HashMap<>();
        list = new ArrayList<>();
    }

    public void print() {
        /* print 1
        for (int i = 0 ; i < m; i ++) {
            for (int j = 0 ; j < n; j ++) {
                System.out.print(".");
            }
            System.out.println();
        }
        */
        for (int i = 0 ; i < maxM; i ++) {
            for (int j = 0; j < maxN; j++) {
                char c = checkPos(i, j);
                System.out.print(c);
            }
            System.out.println();
        }
    }

    public void addRect(char color, int x, int y, int w, int h) {
        Rect r = new Rect(color, x, y, w, h);
        map.put(color, r);
        list.add(r);
    }

    public char checkPos(int x, int y){
        int size = list.size();
        for (int i = size - 1; i >=0 ; i --) {
            Rect r = list.get(i);
            if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
                return r.c;
            }
        }
        return '.';
    }

    public void move(char color, int x, int y) {
        Rect r = map.get(color);
        list.remove(r);
        r.x = x;
        r.y = y;
        maxM = Math.max(maxM, x + r.w + 1);
        maxN = Math.max(maxN, y + r.h + 1);
        list.add(r);
    }


    public static void main(String[] args) {
        Canvas c = new Canvas(10, 25);
//        c.print();
        c.addRect('a', 0, 0, 3, 5);
        c.addRect('c', 2, 5, 4, 4);
        c.move('c', 8, 7);
        c.print();
    }

    class Rect {
        int x, y, w, h;
        char c;
        public Rect(char c, int x, int y, int w, int h) {
            this.c = c;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
