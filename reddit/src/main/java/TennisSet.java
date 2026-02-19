public class TennisSet {
    private int gamesA;
    private int gamesB;
    private char winner;
    private TennisGame currentGame;
    
    public TennisSet() {
        gamesA = 0;
        gamesB = 0;
        winner = '#';
        currentGame = new TennisGame();
    }
    
    public void pointTo(char player) {
        if (winner != '#') return;
        
        currentGame.pointTo(player);
        
        if (currentGame.getWinner() != '#') {
            if (currentGame.getWinner() == 'A') gamesA++;
            else gamesB++;
            
            if ((gamesA >= 6 && gamesA - gamesB >= 2) || gamesA >= 7) winner = 'A';
            else if ((gamesB >= 6 && gamesB - gamesA >= 2) || gamesB >= 7) winner = 'B';
            else currentGame = new TennisGame();
        }
    }
    
    public String getScore() {
        return gamesA + "-" + gamesB;
    }
    
    public char getWinner() {
        return winner;
    }
    
    public boolean isFinished() {
        return winner != '#';
    }
}
