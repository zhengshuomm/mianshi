import java.util.*;

public class BO5 {
    private List<TennisSet> sets;
    private char winner;
    private TennisSet currentSet;
    private int setsA, setsB;
    
    public BO5() {
        sets = new ArrayList<>();
        winner = '#';
        currentSet = new TennisSet();
        setsA = setsB = 0;
    }
    
    public void pointTo(char player) {
        if (winner != '#') return;
        
        currentSet.pointTo(player);
        
        if (currentSet.isFinished()) {
            sets.add(currentSet);
            if (currentSet.getWinner() == 'A') setsA++;
            else setsB++;
            
            if (setsA >= 3) winner = 'A';
            else if (setsB >= 3) winner = 'B';
            else currentSet = new TennisSet();
        }
    }
    
    public String getMatchScore() {
        StringJoiner sj = new StringJoiner(" ");
        for (TennisSet set : sets) sj.add(set.getScore());
        if (!currentSet.isFinished() && !sets.isEmpty()) sj.add(currentSet.getScore());
        return sj.toString();
    }
    
    public char getWinner() { return winner; }
    public int getSetsA() { return setsA; }
    public int getSetsB() { return setsB; }
}
