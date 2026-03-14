import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Part 1: Users, Follows & Snapshots
 * 社交网络：用户、关注关系、以及不可变的快照。
 */
public class SocialNetwork {

    private final Set<String> users = new HashSet<>();
    /** follower -> set of followees */
    private final Map<String, Set<String>> following = new HashMap<>();

    public SocialNetwork() {
    }

    /**
     * Add a user to the network.
     * @throws IllegalArgumentException if the user already exists
     */
    public void addUser(String userId) {
        if (users.contains(userId)) {
            throw new IllegalArgumentException("User already exists: " + userId);
        }
        users.add(userId);
        following.put(userId, new HashSet<>());
    }

    /**
     * Make follower follow followee.
     * @throws IllegalArgumentException if a user does not exist
     * Notes: A user cannot follow themselves. Duplicate follows do nothing.
     */
    public void follow(String follower, String followee) {
        if (!users.contains(follower)) {
            throw new IllegalArgumentException("User does not exist: " + follower);
        }
        if (!users.contains(followee)) {
            throw new IllegalArgumentException("User does not exist: " + followee);
        }
        if (follower.equals(followee)) {
            return; // cannot follow themselves, no-op
        }
        following.get(follower).add(followee);
    }

    /**
     * Create a snapshot of the current network state.
     * The returned object is immutable (it will not change when the network changes).
     */
    public Snapshot createSnapshot() {
        Map<String, Set<String>> copy = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : following.entrySet()) {
            copy.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        // Map<String, Set<String>> copy = following.entrySet().stream()
        // .collect(Collectors.toMap(Map.Entry::getKey, e -> new HashSet<>(e.getValue())));
        return new Snapshot(copy);
    }

    // ---------- Snapshot (immutable) ----------

    public static class Snapshot {
        /** follower -> set of followees */
        private final Map<String, Set<String>> following;
        /** followee -> set of followers，仅在 Snapshot 内由 following 推导 */
        private final Map<String, Set<String>> followers;

        Snapshot(Map<String, Set<String>> following) {
            this.following = following;
            this.followers = new HashMap<>();
            for (Map.Entry<String, Set<String>> e : following.entrySet()) {
                String follower = e.getKey();
                for (String followee : e.getValue()) {
                    if (!followers.containsKey(followee)) {
                        followers.put(followee, new HashSet<String>());
                    }
                    followers.get(followee).add(follower);
                }
            }
        }

        /**
         * Check if follower is following followee in this snapshot.
         */
        public boolean isFollowing(String follower, String followee) {
            Set<String> followees = following.get(follower);
            return followees != null && followees.contains(followee);
        }

        /**
         * Get a list of people that user_id follows.
         */
        public List<String> getFollowing(String userId) {
            Set<String> set = following.get(userId);
            return set == null ? new ArrayList<>() : new ArrayList<>(set);
        }

        /**
         * Get a list of people who follow user_id.
         */
        public List<String> getFollowers(String userId) {
            Set<String> set = followers.get(userId);
            return set == null ? new ArrayList<>() : new ArrayList<>(set);
        }

        /**
         * Recommend top K users for user_id to follow (people that their friends follow).
         * Excludes user_id themselves and users they already follow. Ties broken by id.
         */
        public List<String> recommend(String userId, int k) {
            Set<String> alreadyFollows = following.get(userId);
            if (alreadyFollows == null || alreadyFollows.isEmpty()) {
                return new ArrayList<String>();
            }
            Map<String, Integer> count = new HashMap<String, Integer>();
            for (String friend : alreadyFollows) {
                Set<String> theirFollowees = following.get(friend);
                if (theirFollowees == null) continue;
                for (String candidate : theirFollowees) {
                    if (candidate.equals(userId) || alreadyFollows.contains(candidate)) continue;
                    Integer v = count.get(candidate);
                    count.put(candidate, v == null ? 1 : v + 1);
                }
            }

            // 可以用heap
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(count.entrySet());
            entries.sort((a, b) -> (b.getValue() - a.getValue()));
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < k && i < entries.size(); i++) {
                result.add(entries.get(i).getKey());
            }
            return result;
        }
    }

    // ---------- Example / Test ----------

    public static void main(String[] args) {
        SocialNetwork network = new SocialNetwork();

        network.addUser("A");
        network.addUser("B");
        network.addUser("C");

        network.follow("A", "B");
        network.follow("B", "C");

        Snapshot snapshot1 = network.createSnapshot();

        assert snapshot1.isFollowing("A", "B") : "A should follow B";
        assert snapshot1.isFollowing("B", "C") : "B should follow C";
        assert !snapshot1.isFollowing("A", "C") : "A should not follow C";

        network.follow("A", "C");

        assert !snapshot1.isFollowing("A", "C") : "Old snapshot must not show new follow";

        Snapshot snapshot2 = network.createSnapshot();
        assert snapshot2.isFollowing("A", "C") : "New snapshot should show A follows C";

        // Part 2: get_following / get_followers
        SocialNetwork network2 = new SocialNetwork();
        network2.addUser("A");
        network2.addUser("B");
        network2.addUser("C");
        network2.addUser("D");
        network2.follow("A", "B");
        network2.follow("A", "C");
        network2.follow("B", "C");
        network2.follow("D", "A");
        Snapshot snapshot = network2.createSnapshot();
        assert snapshot.getFollowing("A").containsAll(Arrays.asList("B", "C")) && snapshot.getFollowing("A").size() == 2;
        assert snapshot.getFollowers("C").containsAll(Arrays.asList("A", "B")) && snapshot.getFollowers("C").size() == 2;

        // Part 3: recommend — A follows B,C; B follows D,E; C follows D,F → D=2, E=1, F=1
        SocialNetwork network3 = new SocialNetwork();
        network3.addUser("A");
        network3.addUser("B");
        network3.addUser("C");
        network3.addUser("D");
        network3.addUser("E");
        network3.addUser("F");
        network3.follow("A", "B");
        network3.follow("A", "C");
        network3.follow("B", "D");
        network3.follow("B", "E");
        network3.follow("C", "D");
        network3.follow("C", "F");
        Snapshot snap3 = network3.createSnapshot();
        List<String> rec = snap3.recommend("A", 2);
        assert rec.get(0).equals("D") : "D first (count 2)";
        assert rec.size() == 2 && rec.contains("D") && (rec.contains("E") || rec.contains("F"));

        System.out.println("All assertions passed.");
    }
}
