import java.util.*;

/**
 * Monster Battle System
 * Part 1: Simple Battle Logic
 * Part 2: Elemental Types (Fire, Water, Grass, Electric)
 * Part 3: Optimized Targeting - pick attacker that deals most damage to defender
 * 
 * Type chart: Fire>Grass, Fire<Water; Water>Fire, Water<Grass; Grass>Water, Grass<Fire; Electric>Water
 */
public class MonsterBattle {

    enum Type { FIRE, WATER, GRASS, ELECTRIC }

    static double getDamageMultiplier(Type attacker, Type defender) {
        if (attacker == Type.FIRE && defender == Type.GRASS) return 2.0;
        if (attacker == Type.FIRE && defender == Type.WATER) return 0.5;
        if (attacker == Type.WATER && defender == Type.FIRE) return 2.0;
        if (attacker == Type.WATER && defender == Type.GRASS) return 0.5;
        if (attacker == Type.GRASS && defender == Type.WATER) return 2.0;
        if (attacker == Type.GRASS && defender == Type.FIRE) return 0.5;
        if (attacker == Type.ELECTRIC && defender == Type.WATER) return 2.0;
        return 1.0;
    }

    static int getEffectiveDamage(Monster attacker, Monster defender) {
        double mult = getDamageMultiplier(attacker.getType(), defender.getType());
        return Math.max(1, (int) Math.round(attacker.getAttack() * mult));
    }

    static class Monster {
        private final String name;
        private int health;
        private final int attack;
        private final Type type;

        public Monster(String name, int health, int attack) {
            this(name, health, attack, Type.FIRE);
        }

        public Monster(String name, int health, int attack, Type type) {
            this.name = name;
            this.health = health;
            this.attack = attack;
            this.type = type;
        }

        public boolean isAlive() {
            return health > 0;
        }

        public void takeDamage(int damage) {
            this.health -= damage;
        }

        public String getName() { return name; }
        public int getHealth() { return health; }
        public int getAttack() { return attack; }
        public Type getType() { return type; }
    }

    static class Team {
        private final String name;
        private final List<Monster> monsters;

        public Team(String name, List<Monster> monsters) {
            this.name = name;
            this.monsters = new ArrayList<>(monsters);
        }

        public Monster getFirstAlive() {
            for (Monster m : monsters) {
                if (m.isAlive()) return m;
            }
            return null;
        }

        /** Part 3: Pick the monster that deals the most damage to defender. Tie: first in list. */
        public Monster getBestAttackerAgainst(Monster defender) {
            Monster best = null;
            int bestDamage = -1;
            for (Monster m : monsters) {
                if (!m.isAlive()) continue;
                int dmg = getEffectiveDamage(m, defender);
                if (dmg > bestDamage) {
                    bestDamage = dmg;
                    best = m;
                }
            }
            return best;
        }

        public boolean isDefeated() {
            return getFirstAlive() == null;
        }

        public String getName() { return name; }
        public List<Monster> getMonsters() { return monsters; }
    }

    /** Part 1: 简单逻辑 - 第一个存活 vs 第一个存活，无类型克制 */
    public static List<String> battle1(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();
        log.add("Battle begins: " + teamA.getName() + " vs " + teamB.getName());

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            Monster atk = attacker.getFirstAlive();
            Monster def = defender.getFirstAlive();
            if (atk == null || def == null) break;

            int damage = atk.getAttack();
            def.takeDamage(damage);
            int hpAfter = def.getHealth();

            if (hpAfter <= 0) {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage. " + def.getName() + " is eliminated!");
            } else {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage. " + def.getName() + " has " + hpAfter + " HP remaining.");
            }

            Team tmp = attacker;
            attacker = defender;
            defender = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.getName() : teamA.getName();
        log.add("Battle ends: " + winner + " wins!");
        return log;
    }

    /** Part 2: 元素类型 - 第一个存活 vs 第一个存活，有类型克制 */
    public static List<String> battle2(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();
        log.add("Battle begins: " + teamA.getName() + " vs " + teamB.getName());

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            Monster atk = attacker.getFirstAlive();
            Monster def = defender.getFirstAlive();
            if (atk == null || def == null) break;

            int damage = getEffectiveDamage(atk, def);
            double mult = getDamageMultiplier(atk.getType(), def.getType());
            def.takeDamage(damage);
            int hpAfter = def.getHealth();

            String effect = mult >= 2.0 ? " (super effective!)" : mult <= 0.5 ? " (not very effective)" : "";
            if (hpAfter <= 0) {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage" + effect + ". " + def.getName() + " is eliminated!");
            } else {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage" + effect + ". " + def.getName() + " has " + hpAfter + " HP remaining.");
            }

            Team tmp = attacker;
            attacker = defender;
            defender = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.getName() : teamA.getName();
        log.add("Battle ends: " + winner + " wins!");
        return log;
    }

    /** Part 3: 优化目标 - 选伤害最高的攻击者 vs 第一个存活，有类型克制 */
    public static List<String> battle3(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();
        log.add("Battle begins: " + teamA.getName() + " vs " + teamB.getName());

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            Monster def = defender.getFirstAlive();
            Monster atk = attacker.getBestAttackerAgainst(def);
            if (atk == null || def == null) break;

            int damage = getEffectiveDamage(atk, def);
            double mult = getDamageMultiplier(atk.getType(), def.getType());
            def.takeDamage(damage);
            int hpAfter = def.getHealth();

            String effect = mult >= 2.0 ? " (super effective!)" : mult <= 0.5 ? " (not very effective)" : "";
            if (hpAfter <= 0) {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage" + effect + ". " + def.getName() + " is eliminated!");
            } else {
                log.add(atk.getName() + " attacks " + def.getName() + " for " + damage + " damage" + effect + ". " + def.getName() + " has " + hpAfter + " HP remaining.");
            }

            Team tmp = attacker;
            attacker = defender;
            defender = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.getName() : teamA.getName();
        log.add("Battle ends: " + winner + " wins!");
        return log;
    }

    static Team copyTeam(Team t) {
        List<Monster> list = new ArrayList<>();
        for (Monster m : t.getMonsters()) {
            list.add(new Monster(m.getName(), m.getHealth(), m.getAttack(), m.getType()));
        }
        return new Team(t.getName(), list);
    }

    public static void main(String[] args) {
        Monster dragon = new Monster("Dragon", 100, 25, Type.FIRE);
        Monster griffin = new Monster("Griffin", 80, 20, Type.ELECTRIC);
        Monster goblin = new Monster("Goblin", 30, 10, Type.GRASS);
        Monster orc = new Monster("Orc", 50, 15, Type.WATER);
        Monster troll = new Monster("Troll", 70, 12, Type.FIRE);

        Team teamA = new Team("Heroes", Arrays.asList(dragon, griffin));
        Team teamB = new Team("Monsters", Arrays.asList(goblin, orc, troll));

        System.out.println("=== Part 1: Simple (first vs first, no types) ===");
        for (String line : battle1(copyTeam(teamA), copyTeam(teamB))) System.out.println(line);

        System.out.println("\n=== Part 2: Elemental Types (first vs first, with types) ===");
        for (String line : battle2(copyTeam(teamA), copyTeam(teamB))) System.out.println(line);

        System.out.println("\n=== Part 3: Optimized Targeting (best attacker vs first) ===");
        for (String line : battle3(copyTeam(teamA), copyTeam(teamB))) System.out.println(line);
    }
}
