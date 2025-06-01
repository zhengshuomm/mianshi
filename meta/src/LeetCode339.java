public class LeetCode339 {
    public static void main(String[] args) {
        String input = "[8, 4, [5, [9], 3], 6]";
        int fact = 0;
        int sum = 0;
        for (int i = 0 ; i < input.length() ; i ++) {
            char c = input.charAt(i);
            if (c == '[') {
                fact ++;
            } else if (c == ']') {
                fact --;
            } else if (Character.isDigit(c)) {
                int num = c - '0';
                sum += num * fact;
            }
        }

        System.out.println(sum);
    }
}
