import java.util.*;

/**
 * LeetCode 127. Word Ladder
 * 
 * A transformation sequence from word beginWord to word endWord using a dictionary wordList 
 * is a sequence of words beginWord -> s1 -> s2 -> ... -> sk such that:
 * - Every adjacent pair of words differs by a single letter.
 * - Every si for 1 <= i <= k is in wordList. Note that beginWord is not a transformed word.
 * 
 * Return the number of words in the shortest transformation sequence from beginWord to endWord, 
 * or 0 if no such sequence exists.
 */
public class WordLadder {
    
    /**
     * Finds the length of shortest transformation sequence using BFS
     * 
     * @param beginWord the starting word
     * @param endWord the target word
     * @param wordList the list of valid words
     * @return the length of shortest transformation sequence, or 0 if no path exists
     */
    public int ladderLength(String beginWord, String endWord, List<String> wordList) {
        // Convert wordList to Set for O(1) lookup
        Set<String> wordSet = new HashSet<>(wordList);
        
        // If endWord is not in wordList, no transformation is possible
        if (!wordSet.contains(endWord)) {
            return 0;
        }
        
        // Special case: if beginWord equals endWord, return 1
        if (beginWord.equals(endWord)) {
            return 1;
        }
        
        // BFS queue: stores words
        Queue<String> queue = new LinkedList<>();
        queue.offer(beginWord);
        
        // Track visited words to avoid cycles
        Set<String> visited = new HashSet<>();
        visited.add(beginWord);
        
        // Remove beginWord from wordSet if present, since beginWord is not a transformed word
        // and we don't want to use it as an intermediate step
        wordSet.remove(beginWord);
        
        int level = 1; // Start at level 1 (beginWord counts as first word)
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            // Process all words at current level
            for (int i = 0; i < size; i++) {
                String currentWord = queue.poll();
                
                // Try changing each character position
                char[] wordArray = currentWord.toCharArray();
                for (int j = 0; j < wordArray.length; j++) {
                    char originalChar = wordArray[j];
                    
                    // Try all possible letters (a-z)
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) {
                            continue; // Skip if same character
                        }
                        
                        wordArray[j] = c;
                        String newWord = new String(wordArray);
                        
                        // If we found the endWord, return level + 1
                        if (newWord.equals(endWord)) {
                            return level + 1;
                        }
                        
                        // If newWord is in wordList and not visited, add to queue
                        if (wordSet.contains(newWord) && !visited.contains(newWord)) {
                            visited.add(newWord);
                            queue.offer(newWord);
                        }
                    }
                    
                    // Restore original character
                    wordArray[j] = originalChar;
                }
            }
            
            level++;
        }
        
        // No path found
        return 0;
    }
    
    /**
     * Alternative solution using bidirectional BFS for better performance
     * 
     * @param beginWord the starting word
     * @param endWord the target word
     * @param wordList the list of valid words
     * @return the length of shortest transformation sequence, or 0 if no path exists
     */
    public int ladderLengthBidirectional(String beginWord, String endWord, List<String> wordList) {
        Set<String> wordSet = new HashSet<>(wordList);
        
        if (!wordSet.contains(endWord)) {
            return 0;
        }
        
        // Two sets for bidirectional BFS
        Set<String> beginSet = new HashSet<>();
        Set<String> endSet = new HashSet<>();
        beginSet.add(beginWord);
        endSet.add(endWord);
        
        Set<String> visited = new HashSet<>();
        int level = 1;
        
        while (!beginSet.isEmpty() && !endSet.isEmpty()) {
            // Always expand the smaller set for efficiency
            if (beginSet.size() > endSet.size()) {
                Set<String> temp = beginSet;
                beginSet = endSet;
                endSet = temp;
            }
            
            Set<String> nextLevel = new HashSet<>();
            
            for (String word : beginSet) {
                char[] wordArray = word.toCharArray();
                
                for (int i = 0; i < wordArray.length; i++) {
                    char originalChar = wordArray[i];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) {
                            continue;
                        }
                        
                        wordArray[i] = c;
                        String newWord = new String(wordArray);
                        
                        // If found in endSet, we've found the path
                        if (endSet.contains(newWord)) {
                            return level + 1;
                        }
                        
                        if (wordSet.contains(newWord) && !visited.contains(newWord)) {
                            visited.add(newWord);
                            nextLevel.add(newWord);
                        }
                    }
                    
                    wordArray[i] = originalChar;
                }
            }
            
            beginSet = nextLevel;
            level++;
        }
        
        return 0;
    }
}
