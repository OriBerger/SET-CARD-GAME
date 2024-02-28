# Set-Card-Game

## Overview

* This project is an implementation of the card game "Set," a real-time card game involving pattern recognition. The game contains a deck of 81 cards, each with four features: color, number, shape, and shading. Players compete to find "legal sets" of three cards that adhere to specific rules regarding these features.
  


## Game Flow

 * The game starts with 12 cards drawn from the deck, arranged in a 3x4 grid on the table.
  Players place tokens on cards. Once a player places a third token, they request the dealer to verify the legality of the set.
  If the set is not legal, the player is frozen for a specified time as a penalty.
  If the set is legal, the cards are replaced, and the player earns a point but is also frozen for a shorter time period.


## Game Features
 * ### Cards and Features
    Cards are represented as integers from 0 to 80.
    Each card has 4 features, each of size 3.

 * ### The Table
    Holds cards in a 3x4 grid.
    Keeps track of tokens placed by players.

* ### The Players
  Supported player types are human and non-human.
  Non-human players are simulated by threads.
  Players control 12 unique keys on the keyboard to place or remove tokens.

 * ### The Dealer
    Handles the game flow, card dealing, and point awarding.
    Ensures fair play through a first-come-first-serve (FIFO) order for checking legal sets.
  
### Legal Set
A legal set is a set of 3 cards that for each of the four features — color, number, shape, and shading — adhere to one of two conditions:

  * All cards have the same value for the feature.
  
  * All cards have different values for the feature.
    
  ![Screenshot 2023-08-28 at 12-06-40 Microsoft Word - Assignment2-v1 1-30 11 22 docx - Assignment2-V1 0-V1 1-changes-1 pdf](https://github.com/Talmal6/Set-Card-Game/assets/130377913/416b34b1-f3dd-4e49-9fdb-7f390af9d52f)
      
      
    these 3 cards do form a set, because the shadings of the three cards are all the same,while the numbers, the colors, and the shapes are all different.



## Project Goals
### 1. Practice Concurrent Programming

  * Utilize Java 8 to implement multi-threading in the game, focusing on handling simultaneous actions of multiple players and the dealer.

### 2. Practice Java Synchronization

  * Apply Java's built-in synchronization mechanisms to ensure seamless and fair game interactions between multiple threads.

### 3. Gain Basic Experience in Unit Testing

  * Incorporate unit tests to validate the functionality of multi-threading and synchronization in the game.
## Multi-threading Focus
### Players as Threads

* Each player is represented by a separate thread. For non-human players, another thread is created to simulate their key presses.
### Action Queue

* A synchronized queue holds incoming actions from players. This queue ensures that when a player places their third token, they must wait for the dealer to validate the set.
### Dealer as Main Thread

* The dealer is represented as the main thread controlling the game flow. It performs various tasks like dealing cards, checking sets, announcing winners and more.
### Fair Synchronization

* The FIFO-based synchronization mechanism ensures that if two players try to claim a set at the same time, they are serviced in the order they claimed.

## Visual Overview

<img src="https://github.com/Talmal6/Set-Card-Game/assets/130377913/ca225e84-27fd-47ee-8e77-bf9b93f97a50" width="600" height="400">

## About

* This project was developed as a part of the "System Programming" course at Ben-Gurion University. It serves as a platform to practice concurrent programming in a Java 8 environment, focusing on Java Threads and Java Synchronization.
#### Disclaimer
* The User Interface and key control functionalities in this game were not developed by me. These components were provided by the Teaching Assistants of the course. My work focused on the backend logic, game rules, and multi-threading aspects of the application.
