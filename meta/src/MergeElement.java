import java.util.Arrays;

public class MergeElement {
        public int solve(int[] A) {
            int n = A.length;
            int[] sum = new int[A.length + 1];
            for (int i = 1 ; i <= A.length ; i ++) {
                sum[i] = A[i - 1] + sum[i - 1];
            }
//            System.out.println(Arrays.toString(sum));
            int[][] dp = new int[n][n];
            for (int len = 1 ; len < n ; len ++) {
                for (int start = 0, end = start + len ; end < n ; start ++, end ++) {
                    if (len == 0) {
                        // dp[start][end] = A[start];
                    } else if (len == 1) {
                        dp[start][end] = A[start] + A[end];
//                        System.out.println(start + "\t" + end + "\t" + dp[start][end]);

                    } else {
                        dp[start][end] = Integer.MAX_VALUE;
                        for (int k = start ; k < end ; k ++ ) {
//                            System.out.println(dp[start][k] + dp[k + 1][end] + sum[k + 1] - sum[start] + sum[end + 1] - sum[k + 1]);
                            dp[start][end] = Math.min(dp[start][end], dp[start][k] + dp[k + 1][end] + sum[k + 1] - sum[start] + sum[end + 1] - sum[k + 1]);
//                            System.out.println(start + "\t" + end + "\t" + dp[start][end]);
                        }
                    }
                }
            }
//            System.out.println(Arrays.toString(dp[0]));
//             System.out.println(Arrays.toString(dp[1]));
            return dp[0][n - 1];
        }

        public static void main(String[] args) {
            MergeElement m = new MergeElement();
            int[] a = {1, 2, 3, 4};
            m.solve(a);
        }

}
