package bguspl.set.ex;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import bguspl.set.Env;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /////
    /**
     * 
     */
    public static final Object lockGame = new Object();
    public static final Object canAnnounce = new Object();

    public static volatile int wakeByPlayer = -1; //this is for to tell the dealer which player put 3 tokens

    public static volatile boolean isBusy = false; // true means that the dealer is busy now


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList()); // creates a sorted list 0 - deckSize
        terminate = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        /////
        //create players threads
        synchronized(lockGame){
            for(int i = 0; i < players.length; i++){         
                Thread playerThread = new Thread(players[i], "player " + i);
                playerThread.start();
                try{
                    lockGame.wait(); //for a serial creating
                }catch (InterruptedException e){}                
            }
        }
        /////
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {

        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        synchronized(Dealer.lockGame){
            for(int i = players.length - 1; i >= 0; i--){
                players[i].terminate();
                try{
                    lockGame.wait(); //terminate every player reversed from their creation
                }catch (InterruptedException e){}
            }
        }        
        terminate = true; //get out of the "should finish" while loop
        Thread.currentThread().interrupt(); //this is for the dealer-make him stop
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table (a player has created a legal set) and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        isBusy = true; //dealer is working->players cant do nothing
        if(wakeByPlayer != -1){ //means that wakeByPlayer = some player.id
            int[] cards = players[wakeByPlayer].getCurrSet(); //get its set 
            for(Integer card : cards){
                int slot = table.cardToSlot(card);
                for(int i = 0; i < players.length; i++){
                    if(table.slotToTokens[slot][i]){
                        table.removeToken(i, slot);
                        players[i].removeToken(slot); //remove all tokens(his and others) from the places that we need to remove a card of a set
                    }
                }                            
                table.removeCard(slot);
                deck.remove(card); //remove it also from the deck because a set is not coming back once it was found
            }
        }
        isBusy = false; //players can procceed playing
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        isBusy = true;
            boolean reset = false; //if we need to update the timer to the initial time
            int slot = 0;           
            while(slot < env.config.tableSize & deck.size() != 0){
                if (table.slotToCard[slot] == null){ //put cards only where is there are no cards
                    reset = true;
                    Integer card = deck.remove((int)(Math.random()*deck.size()));//this is for the card shuffle,take some card randomly from the deck is like a shuffle
                    table.placeCard(card,slot);
                }
                slot++;
            }
            updateTimerDisplay(reset);  //update timer to start playing
            if(wakeByPlayer != -1){ //if someone made set, the other players were waiting for the checking, then we can wake them up
                synchronized(lockGame){
                    lockGame.notifyAll();
                }        
                wakeByPlayer = -1;
            }
        isBusy = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        while(reshuffleTime - System.currentTimeMillis() > 0 & wakeByPlayer == -1){ //while the time to shuffle isn't over and no one made a set
            if(reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) 
                try{
                        Thread.sleep(1000); //sleep for a second and every second
                }catch (InterruptedException e){}
            else{
                try{
                    Thread.sleep(100); //sleep a little bit less because we want to show the seconds in a decimal way and this better for the displaying
            }catch (InterruptedException e){}
            }
            updateTimerDisplay(false);         //update timer every second or every milisecond when the warning is need to be shown        
        }
    }


    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){ //update the timer to the initial time and a little bit for the ui can be display better
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 800;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else if(reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis){
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);//set the color to be black (not a warning time)
        }
        else if(reshuffleTime - System.currentTimeMillis() >= 0){
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true); //set the color to be red (warning time)
        }
    }
   
    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        isBusy = true;
        env.ui.setCountdown(0, false);                 
        table.resetSlotToTokens(); //reset the tokens on the table
        env.ui.removeTokens(); //reset the tokens on the table in the display too
        for (int slot = 0; slot < table.slotToCard.length; slot++){
            if (table.slotToCard[slot] != null){
                deck.add(table.slotToCard[slot]); //here we need to remove a card from table and put it back to the deck because it was removed from the timer cause and for a set cause
                table.removeCard(slot);
            }
        } 
        for(Player player : players){
            player.resetcurrSet(); //reset all chosen places for every player
        }
        isBusy = false;
        synchronized(canAnnounce){
            canAnnounce.notifyAll();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        synchronized(canAnnounce){
            if(isBusy){
                try{
                    canAnnounce.wait();
                }catch (InterruptedException e){}
            }
        }
        int highScore = 0;
        LinkedList<Player> winner = new LinkedList<Player>();
        for (int i = 0; i < players.length; i++){
            Player player = players[i];
            if (player.score() > highScore){ //add the payer with the highest score
                winner = new LinkedList<Player>();
                winner.add(player); // a list that can hold a winner or the winners when there is a tie
                highScore = player.score();
            }
            else if (player.score() == highScore){
                winner.add(player);
            }
        }
        int[] winnersArray = winnersArray(winner);
        env.ui.announceWinner(winnersArray); //and displays them
    }

    ////
      //returns an array representing the winners
      public int[] winnersArray(LinkedList<Player> winnerL) {
        int[] winnerA = new int[winnerL.size()];
        Iterator<Player> iterator = winnerL.iterator();
        int i = 0;   
        while (iterator.hasNext()) {
            Player winner = iterator.next();      //run over all the winners and get their id's      
            winnerA[i] = winner.getId();
            i++;
        }
        return winnerA;
    }

}