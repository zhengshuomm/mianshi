/**
 * Tennis Game Score Tracker
 * 
 * Tracks scores for a tennis game between two players (A and B).
 * Handles standard tennis scoring rules including deuce and advantage.
 */
public class TennisGame {
    private int scoreA;
    private int scoreB;
    private char winner;

    public TennisGame() {
        scoreA = 0;
        scoreB = 0;
        winner = '#';
    }

    public void pointTo(char player) {
        if (winner != '#') {
            return;
        }

        if (player == 'A') {
            scoreA++;
        } else {
            scoreB++;
        }

        if (scoreA >= 4 || scoreB >= 4) {
            if (scoreA - scoreB >= 2) {
                winner = 'A';
            } else if (scoreB - scoreA >= 2) {
                winner = 'B';
            }
        }
    }

    public String getScore() {
        if (scoreA >= 3 && scoreB >= 3) {
            int minScore = Math.min(scoreA, scoreB);
            int offset = minScore - 3;
            int displayA = scoreA - offset;
            int displayB = scoreB - offset;
            return displayA + ":" + displayB;
        }
        return scoreA + ":" + scoreB;
    }

    public char getWinner() {
        return winner;
    }
}
