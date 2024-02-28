package bguspl.set.ex;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //////
    /**
     * The current set of tokens placed on the table (values = slots)
     */
    private LinkedList<Integer> currSet;

    /**
     * The dealer
     */
    private Dealer dealer;  
    
    /**
     * locking object for synchronization
     */
    public Object lockPlayer;
    public Object lockAI;

    /**
     * flags representing the state of the player
     */
    public volatile boolean toPunish = false;
    public volatile boolean toPoint = false;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        /////
        currSet = new LinkedList<Integer>();
        this.dealer = dealer;
        this.terminate = false;
        this.score = 0;
        lockPlayer = new Object();
        lockAI = new Object();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {  
        playerThread = Thread.currentThread();             
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human){ //create AI under synchronized and wait, to control the order the threds are created
            synchronized(lockAI){
                createArtificialIntelligence();            
                try{
                    lockAI.wait();
                }catch(InterruptedException e){}       
            }
        }
        synchronized(Dealer.lockGame){ //control the order the threds are created
            Dealer.lockGame.notifyAll();
        }
        while (!terminate) {
            // TODO implement main player loop
            if (!human){
                synchronized(lockAI){
                        lockAI.notifyAll(); //every loop the AI thread waits until a new loop begins, and wakes up by notify
                }
            }
            else{
                synchronized(lockPlayer){
                    try{
                        lockPlayer.wait(); //let go of lockPlayer, so keyPress can lock it
                    }catch (InterruptedException e){} // catch lockPlayer again after keyPress "let go" and notify
                }
            }
            if(toPunish){
                penalty();
            }
            if(toPoint){
                synchronized(Dealer.lockGame){
                    try{
                        Dealer.lockGame.wait(); //let go of Dealer.lockGame, so dealer can remove and put cards, until all crds are placed - then notified
                    }catch (InterruptedException e){}
                }            
                point();
            }    
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        synchronized(Dealer.lockGame){ //control the order the threds are terminated
            Dealer.lockGame.notifyAll();
        }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {                     
            synchronized(lockAI){ //control the order the threds are created
                lockAI.notifyAll();
            }          
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                int slot = (int)(Math.random()*env.config.tableSize);
                keyPressed(slot);
                synchronized(lockAI){
                    try{
                        lockAI.wait();//after offering a randon slot to keyPress, wait until the players loop resets(wether the player needs to punished\ recine a point\ none)
                    }catch(InterruptedException e){}       
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id); 
        aiThread.start();                 
    }
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if (!human){
            aiThread.interrupt();
            try { aiThread.join(); } catch (InterruptedException ignored) {}
        }        
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed. and put\remove a token for me 
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement 
        if(!toPoint & !toPunish & !Dealer.isBusy & Dealer.wakeByPlayer == -1){ //the player can use keyPressed only when it is not in timeout, and thedealer isn't removing\placing cards     
            if (table.slotToCard[slot] != null){ //there is a card in that slot
                if (currSet.contains(slot)){ //a token is already placed on that slot
                    removeToken(slot);
                    table.removeToken(id, slot);
                }
                else if (currSet.size() < env.config.featureSize) { //no more than (setSize), 3 for magic number
                    currSet.add(slot); 
                    table.placeToken(id, slot);
                    if (currSet.size() == env.config.featureSize){ //set needs to be checked
                        if(env.util.testSet(getCurrSet())){
                            Dealer.wakeByPlayer = id;
                            toPoint = true; 
                        }
                        else{
                            toPunish = true;
                        }
                        synchronized(lockPlayer){
                            lockPlayer.notifyAll(); //continue running loop
                        }  
                    }
                }
            }
        }
    }       

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
        ////        
        env.ui.setFreeze(id,env.config.pointFreezeMillis);
        long freezeTimeoutTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
        while (freezeTimeoutTime - System.currentTimeMillis() > 0){ //for pointFreezeMillis, update timer every second  
            env.ui.setFreeze(id,freezeTimeoutTime - System.currentTimeMillis());           
            try{
                Thread.sleep(980);
            }catch (InterruptedException e){}                                       
        }            
        env.ui.setFreeze(id,0);
        toPoint = false; //reset toPoint value
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        env.ui.setFreeze(id,env.config.penaltyFreezeMillis);
        long freezeTimeoutTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        
        while (freezeTimeoutTime - System.currentTimeMillis() > 0){ //for penaltyFreezeMillis, update timer every second  

                env.ui.setFreeze(id,freezeTimeoutTime - System.currentTimeMillis());         
                try{
                    Thread.sleep(980);
                }catch (InterruptedException e){}                                       
            }            
        env.ui.setFreeze(id,0);
        toPunish = false; //reset toPunish value
    }

    public int score() {
        return score;
    }

    /////
    //returns an array representing the cards that the player has tokens on
    public int[] getCurrSet() {
        int[] currSetOutput = new int[currSet.size()];
        Iterator<Integer> iterator = currSet.iterator();
        int i = 0;   
        while (iterator.hasNext()) {
            int slot = iterator.next();
            currSetOutput[i] = table.slotToCard[slot]; // translate slot to card and add to the array
            i++;
        }
        return currSetOutput;
    }

    public int getId(){
        return id;
    }

    public void resetcurrSet(){
        currSet = new LinkedList<Integer>();
    }

    //removes a token from a specific slot, by removing it from the currSet field
    public void removeToken(int slot){
        Iterator<Integer> iterator = currSet.iterator();
        boolean found = false;
        while(!found & iterator.hasNext()){
            int element = iterator.next();
            if(element == slot){
                iterator.remove();
                found = true;
            } 
        }     
    }
}