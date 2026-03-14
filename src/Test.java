import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(Arrays.asList("1","2","3","4","5","6"));
        // for (int  i = 0 ; i < 1 ; i ++) {
        // }
        list.remove(1);
        list.set(1, "10");
        System.out.println(Arrays.toString(list.toArray()));


    }
}
