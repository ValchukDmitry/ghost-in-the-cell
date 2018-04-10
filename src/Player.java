import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/

interface IEvent extends Comparable<IEvent> {
    String toString();

    int getStep();
}

interface IEventAdder {
    void addEvents(Queue<IEvent> events, GameObject gameObject);
}

class AttacksEventAdder implements IEventAdder {

    private static int MAX_ATTACK_COUNT = 5;
    private static int MAX_SCORE = 1000;

    class Attack implements Comparable<Attack>{
        private int score;
        private Factory from;
        private Factory to;
        private int count;

        public Attack(int score, Factory from, Factory to, int count) {
            this.score = score;
            this.from = from;
            this.to = to;
            this.count = count;
        }

        public Factory getFrom() {
            return from;
        }

        public Factory getTo() {
            return to;
        }

        public int getCount() {
            return count;
        }

        public IEvent getEvent(int step) {
            return new MoveEvent(step, count, from.id, to.id);
        }

        @Override
        public int compareTo(Attack o) {
            return this.score < o.score ? -1 : 1;
        }
    };

    @Override
    public void addEvents(Queue<IEvent> events, GameObject gameObject) {
        List<Factory> myFactories = new LinkedList<>();
        List<Factory> enemyFactories = new LinkedList<>();
        List<Factory> factories = gameObject.getFactories();
        for (Factory f : factories) {
            if (f.own > 0) {
                myFactories.add(f);
            } else {
                enemyFactories.add(f);
            }
        }
        int attackCount = 0;
        Queue<Attack> attacks = new PriorityQueue<>();
        for (Factory e : enemyFactories) {
            for (Factory f : myFactories) {
                int score = calcScore(f, e, gameObject);
                int sent = howMuchToSent(f, e, gameObject);
                attacks.add(new Attack(score, f, e, sent));
            }
        }
        while(attacks.size()>0 && attackCount < MAX_ATTACK_COUNT) {
            Attack currentAttack = attacks.poll();
            if(currentAttack.score>300) {
                break;
            }
            attacks.clear();
            events.add(currentAttack.getEvent(gameObject.getCurrentStep()));
            System.err.print(currentAttack.getEvent(gameObject.getCurrentStep()).toString());
            recalculatePredictions(currentAttack.getFrom(), currentAttack.getTo(), gameObject, currentAttack.getCount());
            attackCount++;
            for (Factory e : enemyFactories) {
                for (Factory f : myFactories) {
                    int score = calcScore(f, e, gameObject);
                    if(score > 300) {
                        continue;
                    }
                    int sent = howMuchToSent(f, e, gameObject);
                    attacks.add(new Attack(score, f, e, sent));
                }
            }
        }
        System.err.println();
    }

    private static int calcScore(Factory friend, Factory enemy, GameObject gameObject) {
        int distance = gameObject.getDistances()[friend.id][enemy.id];
        int enemyTroops = enemy.troopsOnStep.get(distance);
        int enemyProduction = Math.abs(enemy.own) * enemy.production;
        int score = MAX_SCORE;
        boolean friendly = false;
        for (Integer troopsCount : enemy.troopsOnStep) {
            if (troopsCount > 0) {
                friendly = true;
            }
        }
        if (friendly) {
            return MAX_SCORE;
        }
        int sent = howMuchToSent(friend, enemy, gameObject);
        if(sent==0) {
            return MAX_SCORE;
        }
        if (enemy.production > 0) {
            score = distance + (int) (enemyProduction * (distance + 1) + enemyTroops) / enemy.production;
        } else {
            score = 30 + distance + (int) (enemyProduction * (distance + 1) + enemyTroops);
        }
        for(Integer troopsCount : friend.troopsOnStep) {
            if(troopsCount - sent < 0) {
                return MAX_SCORE;
            }
        }
        return score;
    }

    private static int howMuchToSent(Factory friend, Factory enemy, GameObject gameObject) {
        int distance = gameObject.getDistances()[friend.id][enemy.id] + 1;
        int enemyTroops = enemy.troopsOnStep.get(distance);
        if(enemyTroops > 0) {
            return 0;
        }
        return Math.abs(enemyTroops) + 1;
    }

    private static void recalculatePredictions(Factory friend, Factory enemy, GameObject gameObject, int sent) {
        List<Integer> friendTroopsOnStep = friend.troopsOnStep;
        for (int i = 0; i < friendTroopsOnStep.size(); i++) {
            friend.troopsOnStep.set(i, friendTroopsOnStep.get(i) - sent);
        }
        int distance = gameObject.getDistances()[friend.id][enemy.id];
        List<Troop> troops = gameObject.getTroops();
        troops.add(new Troop(1, friend.id, enemy.id, sent, distance));
        gameObject.setTroops(troops);
        enemy.calculateFutureTroops(gameObject.getFactories(), troops, gameObject.getFuturePrediction());
    }
}

class MoveEvent implements IEvent {
    private int step;
    private int count;
    private int from;
    private int to;

    public MoveEvent(int step, int count, int from, int to) {
        this.step = step;
        this.count = count;
        this.from = from;
        this.to = to;
    }

    public String toString() {
        return "MOVE " + from + " " + to + " " + count + "; ";
    }

    public int getStep() {
        return step;
    }

    @Override
    public int compareTo(IEvent o) {
        return this.step > o.getStep() ? -1 : 1;
    }
}

class Factory implements Comparable<Factory> {
    public int id;
    public int own;
    public int troopsCount;
    public int production;
    public int score;
    public boolean moved;
    public List<Troop> enemiesOnTheWay;
    public List<Troop> friendsOnTheWay;
    public boolean afterBomb;
    public int stepsAfterBomb;
    public List<Integer> troopsOnStep;

    Factory(int id, int a, int b, int c, int d) {
        this.id = id;
        this.own = a;
        this.troopsCount = b;
        this.production = c;
        this.score = 0;
        this.moved = false;
        this.enemiesOnTheWay = new LinkedList<Troop>();
        this.friendsOnTheWay = new LinkedList<Troop>();
        this.afterBomb = d != 0;
        stepsAfterBomb = d;
    }

    public void calculateFutureTroops(List<Factory> factories, List<Troop> troops, int steps) {
        this.troopsOnStep = new ArrayList<Integer>(steps);
        troopsOnStep.add(this.troopsCount * (this.own > 0 ? 1 : -1));
        int curOwn = this.own;
        for (int g = 1; g < steps; g++) {
            int currentResult = troopsOnStep.get(g - 1);
            currentResult += this.production * curOwn;
            boolean changedOwn = false;
            for (Troop troop : troops) {
                if (troop.to == this.id && troop.turns == g) {
                    if (troop.own == this.own) {
                        currentResult += troop.count * troop.own;
                    } else {
                        if (Math.abs(troop.count) > Math.abs(currentResult)) {
                            changedOwn = true;
                        }
                        if(curOwn != 0) {
                            currentResult = currentResult + troop.count * troop.own;
                        } else {
                            currentResult = Math.abs(Math.abs(currentResult) - Math.abs(troop.count)) *
                                    (changedOwn ? troop.own : -1);
                        }
                    }
                }
            }
            if (changedOwn) {
                curOwn = currentResult == 0 ? curOwn : currentResult < 0 ? -1 : 1;
            }
            troopsOnStep.add(currentResult);
        }
    }

    public int compareTo(Factory f2) {
        return this.id > f2.id ? 1 : -1;
    }

    public String sendTroops(String curCommand, Factory f2, int count) {
        curCommand += "MOVE " + this.id + " " + f2.id + " " + count + "; ";
        this.troopsCount -= count;
        f2.addFriendTroops(new Troop(1, this.id, f2.id, count, 100));
        return curCommand;
    }

    public String updateBase(String curCommand) {
        if (this.troopsCount >= 10) {
            curCommand += "INC " + this.id + "; ";
        }
        return curCommand;
    }

    public void addEnemyTroops(Troop enemy) {
        this.enemiesOnTheWay.add(enemy);
    }

    public void addFriendTroops(Troop friend) {
        this.enemiesOnTheWay.add(friend);
    }

    public List<Factory> sortByDistances(List<Factory> factories, int[][] distancies) {
        for (Factory f : factories) {
            f.score = distancies[this.id][f.id];
        }
        Collections.sort(factories);
        return factories;
    }
}

class Troop {
    public int own;
    public int from;
    public int to;
    public int count;
    public int turns;

    Troop(int own, int from, int to, int count, int turns) {
        this.own = own;
        this.from = from;
        this.to = to;
        this.count = count;
        this.turns = turns;
    }
}

class GameObject {
    private int currentStep;
    private int[][] distances;
    private int friendBombs;
    private int enemyBombs;
    private List<Factory> factories;
    private List<Troop> troops;
    private int futurePrediction;

    public int getFuturePrediction() {
        return futurePrediction;
    }

    public void setFuturePrediction(int futurePrediction) {
        this.futurePrediction = futurePrediction;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public int[][] getDistances() {
        return distances;
    }

    public void setDistances(int[][] distances) {
        this.distances = distances;
    }

    public int getFriendBombs() {
        return friendBombs;
    }

    public void setFriendBombs(int friendBombs) {
        this.friendBombs = friendBombs;
    }

    public int getEnemyBombs() {
        return enemyBombs;
    }

    public void setEnemyBombs(int enemyBombs) {
        this.enemyBombs = enemyBombs;
    }

    public List<Factory> getFactories() {
        return factories;
    }

    public void setFactories(List<Factory> factories) {
        this.factories = factories;
    }

    public List<Troop> getTroops() {
        return troops;
    }

    public void setTroops(List<Troop> troops) {
        this.troops = troops;
    }
}

class Player {
    private static int[][] distances;

    public static void main(String args[]) {
        int bombs = 2;
        Scanner in = new Scanner(System.in);
        Random rand = new Random();
        int futureCalculationSteps = 20;
        int factoryCount = in.nextInt(); // the number of factories
        distances = new int[factoryCount][factoryCount];
        int linkCount = in.nextInt(); // the number of links between factories
        for (int i = 0; i < factoryCount; i++) {
            for (int g = 0; g < factoryCount; g++) {
                distances[i][g] = 100000;
            }
        }
        int step = 0;
        for (int i = 0; i < linkCount; i++) {
            int factory1 = in.nextInt();
            int factory2 = in.nextInt();
            int distance = in.nextInt();
            distances[factory1][factory2] = distance;
            distances[factory2][factory1] = distance;
        }
        GameObject gameObject = new GameObject();
        gameObject.setDistances(distances);
        gameObject.setEnemyBombs(2);
        gameObject.setFriendBombs(2);
        Queue<IEvent> eventsQueue = new PriorityQueue<>();
        List<IEventAdder> eventAdders = new LinkedList<>();
        eventAdders.add(new AttacksEventAdder());
        // game loop
        while (true) {
            step++;
            List<Factory> factories = new LinkedList<Factory>();
            List<Troop> troops = new LinkedList<Troop>();
            int entityCount = in.nextInt();
            StringBuilder commands = new StringBuilder();
            for (int i = 0; i < entityCount; i++) {
                int entityId = in.nextInt();
                String entityType = in.next();
                int arg1 = in.nextInt();
                int arg2 = in.nextInt();
                int arg3 = in.nextInt();
                int arg4 = in.nextInt();
                int arg5 = in.nextInt();
                if (entityType.equals("FACTORY")) {
                    factories.add(new Factory(entityId, arg1, arg2, arg3, arg4));
                }
                if (entityType.equals("TROOP")) {
                    troops.add(new Troop(arg1, arg2, arg3, arg4, arg5));
                }
            }
            for (Factory f : factories) {
                f.calculateFutureTroops(factories, troops, futureCalculationSteps);
            }

            gameObject.setCurrentStep(step);
            gameObject.setFuturePrediction(futureCalculationSteps);
            gameObject.setFactories(factories);
            gameObject.setTroops(troops);

            for (IEventAdder eventAdder : eventAdders) {
                eventAdder.addEvents(eventsQueue, gameObject);
            }
            while (eventsQueue.size() > 0 && eventsQueue.peek().getStep() == step) {
                commands.append(eventsQueue.poll().toString());
            }

//            for (Factory f : factories) {
//                System.err.print(f.id + " ");
//                for (Integer prediction : f.troopsOnStep) {
//                    System.err.print(prediction + " ");
//                }
//                System.err.println();
//            }

            commands.append("MSG " + eventsQueue.size());
            System.out.println(commands.toString());
        }
    }
}
