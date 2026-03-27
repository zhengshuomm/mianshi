import java.util.*;

public class MonsterBattle {
    enum Type { FIRE, WATER, GRASS, ELECTRIC }

    double getDamageMultiplier(Type attacker, Type defender) {
        if (attacker == Type.FIRE && defender == Type.GRASS) return 2.0;
        if (attacker == Type.FIRE && defender == Type.WATER) return 0.5;
        if (attacker == Type.WATER && defender == Type.FIRE) return 2.0;
        if (attacker == Type.WATER && defender == Type.GRASS) return 0.5;
        if (attacker == Type.GRASS && defender == Type.WATER) return 2.0;
        if (attacker == Type.GRASS && defender == Type.FIRE) return 0.5;
        if (attacker == Type.ELECTRIC && defender == Type.WATER) return 2.0;
        return 1.0;
    }
    
    class Monster {
        String name;
        int health;
        int attack;
        Type type;

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
    }
    
    class Team {
        List<Monster> monsters;
        String name;
        
        public Team(String name, List<Monster> monsters) {
            this.name = name;
            this.monsters = monsters;
        }

        public Monster getFirstAlive() {
            for (Monster m: monsters) {
                if (m.isAlive()) {
                    return m;
                }
            }
            return null;
        }

        public boolean isDefeated() {
            return getFirstAlive() == null;
        }
    }

    public List<String> battle1(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            Monster atk = attacker.getFirstAlive();
            Monster def = defender.getFirstAlive();

            int damage = atk.attack;
            def.takeDamage(damage);
            int hp = def.health;
            if (hp <= 0) {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " is eliminated!");
            } else {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " has " + hp + " HP remaining.");
            }

            Monster tmp = atk;
            atk = def;
            def = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.name : teamA.name;
        log.add("Battle ends: " + winner + " wins!");
        return log;
    }

    public List<String> battle2(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            Monster atk = attacker.getFirstAlive();
            Monster def = defender.getFirstAlive();

            int damage = (int) Math.round(atk.attack * getDamageMultiplier(atk.type, def.type));
            def.takeDamage(damage);
            int hp = def.health;
            if (hp <= 0) {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " is eliminated!");
            } else {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " has " + hp + " HP remaining.");
            }

            Monster tmp = atk;
            atk = def;
            def = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.name : teamA.name;
        log.add("Battle ends: " + winner + " wins!");
        return log; 
    }

    public List<String> battle3(Team teamA, Team teamB) {
        List<String> log = new ArrayList<>();

        Team attacker = teamA;
        Team defender = teamB;

        while (!teamA.isDefeated() && !teamB.isDefeated()) {
            // Monster atk = attacker.getFirstAlive();
            Monster def = defender.getFirstAlive();

            int maxDamage = 0;
            Monster best = null;
            for (Monster atk : attacker.monsters) {
                if (!atk.isAlive()) continue;
                int damage = (int) Math.round(atk.attack * getDamageMultiplier(atk.type, def.type));
                if (damage > maxDamage) {
                    maxDamage = damage;
                    best = atk;
                }
            }

            Monster atk = best;
            int damage = maxDamage;
            def.takeDamage(damage);
            int hp = def.health;
            if (hp <= 0) {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " is eliminated!");
            } else {
                log.add(atk.name + " attacks " + def.name + " for " + damage + " damage. " + def.name + " has " + hp + " HP remaining.");
            }

            Monster tmp = atk;
            atk = def;
            def = tmp;
        }

        String winner = teamA.isDefeated() ? teamB.name : teamA.name;
        log.add("Battle ends: " + winner + " wins!");
        return log;
    }
}
