public class ValidWordAbbr408 {
    public static void main(String[] args) {
        ValidWordAbbr408 v = new ValidWordAbbr408();
        String word = "internationalization", abbr = "i12iz4";
        System.out.println(v.validWordAbbreviation(word, abbr));
    }

    public boolean validWordAbbreviation(String word, String abbr) {
        int m = word.length(), n = abbr.length();
        int i = 0, j = 0;
        while (i < m) {
            if (j >= n) return false;
            char a = word.charAt(i);
            char b = abbr.charAt(j);
            if (a == b) {
                i ++;
                j ++;
            } else if (Character.isDigit(b)) {
                int k = j;
                while (k < n && Character.isDigit(abbr.charAt(k))) k ++;
                String number  = abbr.substring(j, k);
                if (number.charAt(0) == '0' ) return false;
                i += Integer.parseInt(number);
                j = k;
            } else {
                return false;
            }
        }
        return i == m && j == n;
    }
}