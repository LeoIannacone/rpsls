package it.unibo.games.rpsls.interfaces.client;

import it.unibo.games.rpsls.interfaces.ICommand;
import it.unibo.games.rpsls.interfaces.IGame;
import it.unibo.games.rpsls.interfaces.IPlayer;

public interface IClientConnector {
	
	// Will be a Singleton
	
	// Generic
	public void connect();
	public void disconnect();
	public void init();
	
	// Games
	public boolean createNewGame(IGame game);
	public boolean joinGame(IGame game, IPlayer player);
	public boolean leaveGame(IGame game, IPlayer player);
	public boolean endGame(IGame game);
	public boolean deleteGame(IGame game);
	public boolean updateGameStatus(IGame game, String status);
	public String getGameStatus(IGame game);
	public boolean updateGameScore(IGame game);
	// Game subscribe
	public void watchForWaitingGames(IClientObserver observer);
	public void unwatchForWaitingGames();
	
	// Player
	public boolean createNewPlayer(IPlayer player);
	// Player subscribe
	public void watchForIncomingPlayer(IGame game, IClientObserver observer);
	public void unwatchForIncomingPlayer();
	public void watchForGameEnded(IGame game, IClientObserver observer);
	public void unwatchForGameEnded();
	
	// Hits
	public boolean sendHit(IGame game, IPlayer player, ICommand hit);
	// Hits subscribe
	public void watchForHit(IGame game, IPlayer player, IClientObserver observer);
	public void unwatchForHit();
	
	//unsubscribe all
	public void unwatchAll();
	
}
