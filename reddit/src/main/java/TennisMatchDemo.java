public class TennisMatchDemo {
    public static void main(String[] args) {
        TennisSet set = new TennisSet();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 4; j++) set.pointTo('A');
        }
        System.out.println("Set: " + set.getScore() + " Winner: " + set.getWinner());
        
        BO5 match = new BO5();
        for (int s = 0; s < 3; s++) {
            for (int g = 0; g < 6; g++) {
                for (int p = 0; p < 4; p++) match.pointTo('A');
            }
        }
        System.out.println("Match: " + match.getMatchScore() + " Winner: " + match.getWinner());
    }
}
