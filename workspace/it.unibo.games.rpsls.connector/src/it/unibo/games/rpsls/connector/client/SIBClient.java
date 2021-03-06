package it.unibo.games.rpsls.connector.client;

import java.util.Date;
import java.util.Vector;

import sofia_kp.KPICore;
import sofia_kp.SSAP_XMLTools;
import sofia_kp.SSAP_sparql_response;
import sofia_kp.iKPIC_subscribeHandler;

import it.unibo.games.rpsls.connector.Config;
import it.unibo.games.rpsls.connector.SIBFactory;
import it.unibo.games.rpsls.game.Game;
import it.unibo.games.rpsls.interfaces.IGame;
import it.unibo.games.rpsls.interfaces.ICommand;
import it.unibo.games.rpsls.interfaces.IPlayer;
import it.unibo.games.rpsls.interfaces.client.IClientConnector;
import it.unibo.games.rpsls.interfaces.client.IClientObserver;
import it.unibo.games.rpsls.utils.Debug;

public class SIBClient implements IClientConnector, iKPIC_subscribeHandler {

	private static SIBClient instance;
	private SIBSubscriptionWaitingGames waitingGamesSubscription;
	private SIBSubscriptionWaitingIncomingPlayer incomingPlayerSubscription;
	private SIBSubscriptionHit hitSubscription;
	private SIBSubscriptionLeaveGame leaveSubscription;
	

	
	/**
	 * Declaration of SIB
	 */	
	private KPICore kp;  //direct interface with the SIB
	private SSAP_XMLTools xml_tools;  // utility methods to compose messages and manage responses
	private String xml =""; //conventionally used for storing the messages from the SIB
	private boolean ack = false; // Conventionally used for checking SIB response
	
	public static SIBClient getInstance() {
		if (instance == null)
			instance = new SIBClient();
		return instance;
	}
	
	private SIBClient() {
		Debug.print(2, this.getClass().getCanonicalName() + ": SIBConnector: " + "Connecting to " + Config.SIB_NAME + " @ " + Config.SIB_HOST + ":" + Config.SIB_PORT);
		kp = new KPICore(Config.SIB_HOST, Config.SIB_PORT, Config.SIB_NAME);
		Debug.print(2, this.getClass().getCanonicalName() + ": SIBConnector: " + "Connected");
		xml_tools = new SSAP_XMLTools();
	}
	
	@Override
	public void init() {

	}

	@Override
	public void connect() {	
		//Trying to join SIB
		xml = kp.join();
		ack = xml_tools.isJoinConfirmed(xml);
		if (!ack)
			System.err.println("Error: unable to join the SIB");
		else
			Debug.print(2, this.getClass().getCanonicalName() + ": connect: " + "SIB joined");

	}

	@Override
	public void disconnect() {
		xml = kp.leave();
		ack = xml_tools.isLeaveConfirmed(xml);
		if(!ack){
			System.err.println ("Error during LEAVE");
		}   
		else
			Debug.print(2, this.getClass().getCanonicalName() + ": disconnect: " + "SIB leaved");
	}

	@Override
	public boolean createNewGame(IGame game) {
		
		/**
		 * we need to add for each game session identified by URI:
		 * 
		 * 		- an HomePlayer
		 * 		- a GuestPlayer (if not null)
		 * 		- a GameStatus (initially set to WAITING)
		 * 		- a Score (data property)
		 * 		- a CommandInterface (a new URI that we must initialize)
		 * 		- a GameDescription
		 */
		
		Vector<Vector<String>> triples = new Vector<Vector<String>>();
		
		Vector<String> v;
		String uri = game.getURIToString();
		
		//insert in SIB a new URI with type GameSession
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.RDF + "type", Config.NAME_SPACE + "GameSession", "URI", "URI");
		triples.add(v);
	
		//add new game session to RPSLS
		v = xml_tools.newTriple(Config.NAME_SPACE + "RPSLS", Config.NAME_SPACE + "HasGameSession", Config.NAME_SPACE + game.getURIToString(), "URI", "URI");
		triples.add(v);
		
		//HomePlayer
		if(game.getHomePlayer() != null){
			v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasHome", Config.NAME_SPACE + game.getHomePlayer().getURIToString(), "URI", "URI");
			triples.add(v);
		}
		
		//GuestPlayer
		if(game.getGuestPlayer() != null){
			v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasGuest", Config.NAME_SPACE + game.getGuestPlayer().getURIToString(), "URI", "URI");
			triples.add(v);
		}
		
		//Score
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "hasScore", game.getScore(), "URI", "literal");
		triples.add(v);
		
		//Status
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasStatus", Config.NAME_SPACE + game.getStatus(), "URI", "URI");
		triples.add(v);
		
		//CommandInterface
		if( game.getCommandInterface() == null ){
			game.setCommandInterface(new SIBCommandInterface());
		}
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasCommandInterface", Config.NAME_SPACE + game.getCommandInterface().getURIToString() , "URI", "URI");
		triples.add(v);
		
		// TODO: GameDescription
		
		xml = kp.insert(triples);
		
		ack = xml_tools.isInsertConfirmed(xml);
		if(!ack){
			System.err.println ("Error Inserting new Game in the SIB");
		}
		else
			Debug.print(2, this.getClass().getCanonicalName() + ": createNewGame: " + "Created new game:\n     " + game.toString());
		return ack;
	}

	@Override
	public boolean joinGame(IGame game, IPlayer player) {
		/**
		 * when a player joins a game previously registered  
		 * we need to update:
		 * 		- guest player
		 * 		- status
		 * 		- score (in a game in waiting is null)
		 */
		
		boolean ack;
		String xml = "";
		xml = kp.insert(Config.NAME_SPACE + game.getURIToString(), Config.NAME_SPACE + "HasGuest", Config.NAME_SPACE + player.getURIToString(), "URI", "URI");
		ack = xml_tools.isInsertConfirmed(xml);
		if (ack) {
			game.setGuestPlayer(player);
			game.setScore("0-0");
			ack = updateGameScore(game);
			Debug.print(2, this.getClass().getCanonicalName() + ": joinGame: " + player.getURIToString() + " joined " + game.getURIToString());
		
			ack = updateGameStatus(game, Game.ACTIVE);
			if (! ack)
				System.err.println("Error joining game");
		}
		else 
			System.err.println("SIBConnector:updateGameStatus: error inserting guest player");
		return ack;
	}

	@Override
	public boolean leaveGame(IGame game, IPlayer player) {
		Debug.print(2, player.getURIToString() + " leaved " + game.getURIToString());
		return endGame(game);
	}

	@Override
	public boolean endGame(IGame game) {
		return updateGameStatus(game, Game.ENDED);
	}

	@Override
	public boolean deleteGame(IGame game) {
		
		/**
		 * remove game from SIB
		 */
		
		String DELETE_GAME = "DELETE { <" +
				Config.NAME_SPACE + "RPSLS> <http://rpsls.games.unibo.it/Ontology.owl#HasGameSession> <" +Config.NAME_SPACE + game.getURIToString() + "> . " +
				"<" +Config.NAME_SPACE + game.getURIToString() + "> ?prop_game ?val_game . " +
				"} WHERE { " +
				"?interactive_game <http://rpsls.games.unibo.it/Ontology.owl#HasGameSession> <" +Config.NAME_SPACE + game.getURIToString() + "> . " +
				"<" +Config.NAME_SPACE + game.getURIToString() + "> ?prop_game ?val_game" +
				"}";
		
		String xml = kp.querySPARQL(DELETE_GAME);
		ack = xml_tools.isQueryConfirmed(xml);
		if (ack)
			Debug.print(2, this.getClass().getCanonicalName() + ": deleteGame: removed " + game.getURIToString() + " from SIB");
		else{
			System.err.println("Error deleting game");
		}
		
		return ack;
	}

	@Override
	public boolean updateGameStatus(IGame game, String status) {
		
		/**
		 * remove old-status triple, then, insert a triple that
		 * describe the new status
		 */
		
		boolean ack, delete;
		String SPARQL_REMOVE = "DELETE { <" + Config.NAME_SPACE + game.getURIToString() + "> " +
				"<"+ Config.NAME_SPACE + "HasStatus> " +
				"?status} WHERE { " +
				"<" + Config.NAME_SPACE + game.getURIToString() + "> " +
				"<"+ Config.NAME_SPACE + "HasStatus> " +
				"?status . FILTER ( ?status != <" + Config.NAME_SPACE + status + "> ) }";
		String xml = kp.querySPARQL(SPARQL_REMOVE);
		SSAP_sparql_response res = new SSAP_sparql_response(xml);
		delete = res.getBooleans().get(0).equalsIgnoreCase("true");
		if(delete){
			xml = kp.insert(Config.NAME_SPACE + game.getURIToString(), Config.NAME_SPACE + "HasStatus", Config.NAME_SPACE + status, "URI", "URI");
			ack = xml_tools.isInsertConfirmed(xml);
			if (ack){
				game.setStatus(status);
				Debug.print(2, this.getClass().getCanonicalName() + ": updateGameStatus: " + game.getURIToString() + " has been updated:\n    " + game.toString());
			}
			else
				System.err.println("Error updating game status");
			return ack;
		}
		else
			Debug.print(2, this.getClass().getCanonicalName() +  "Game already " + status);
		return false;
	}

	@Override
	public String getGameStatus(IGame game) {
		return SIBFactory.getInstance().getGame(game.getURIToString()).getStatus();
	}

	@Override
	public boolean createNewPlayer(IPlayer player) {
		
		/**
		 * we need to add to SIB the name of each player, identified by URI:
		 */
		
		Vector<Vector<String>> triples = new Vector<Vector<String>>();
		
		Vector<String> v;
		String uri = player.getURIToString();
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.RDF + "type", Config.NAME_SPACE + "Person", "URI", "URI");
		triples.add(v);
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "hasName", player.getName(), "URI", "literal");
		triples.add(v);
		
		xml = kp.insert(triples);
		
		ack = xml_tools.isInsertConfirmed(xml);
		if(!ack){
			System.err.println ("Error Inserting new Player in the SIB");
		}
		else
			Debug.print(2, this.getClass().getCanonicalName() + ": createNewPlayer: " + "Created " + player.getURIToString() + " with name: " + player.getName());
		return ack;
	}

	@Override
	public boolean sendHit(IGame game, IPlayer player, ICommand hit) {
		
		/**
		 * we need to add for each hit identified by URI, with type COMMAND:
		 * 
		 * 		- the type of command (Rock, Paper, Scissors, Lizard, Spock)
		 * 		- the issuers (player that hit the command)
		 * 		- the command interface (connected to the game session)
		 * 		- the time of hit
		 */
		
		Vector<Vector<String>> triples = new Vector<Vector<String>>();
		
		Vector<String> v;
		String uri = hit.getURIToString();
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.RDF + "type", Config.NAME_SPACE + "Command", "URI", "URI");
		triples.add(v);
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasCommandType", Config.NAME_SPACE + hit.getCommandType(), "URI", "URI");
		triples.add(v);
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "HasIssuer", Config.NAME_SPACE + player.getURIToString(), "URI", "URI");
		triples.add(v);
		
		v = xml_tools.newTriple(Config.NAME_SPACE + game.getCommandInterface().getURIToString(), Config.NAME_SPACE + "HasCommand", Config.NAME_SPACE + uri , "URI", "URI");
		triples.add(v);
		
		v = xml_tools.newTriple(Config.NAME_SPACE + uri, Config.NAME_SPACE + "hasTime", "" + new Date().getTime(), "URI", "literal");
		triples.add(v);
		
		xml = kp.insert(triples);
		
		ack = xml_tools.isInsertConfirmed(xml);
		if(!ack){
			System.err.println ("Error Inserting new Hit in the SIB");
		}
		else
			Debug.print(2, this.getClass().getCanonicalName() + ": sendHit: " + player.getName() + " has played " + hit.getCommandType() + ": " + hit.getURIToString());
		return ack;
	}


	public KPICore getKP(){
		return kp;
	}
	
	public SSAP_XMLTools getXMLTools(){
		return xml_tools;
	}

	@Override
	public void kpic_SIBEventHandler(String xml) {
		
		
	}
	
	@Override
	public boolean updateGameScore(IGame game) {
		
		/**
		 * remove old-score triple, then, insert a triple that
		 * describe the new score
		 */
		
		boolean ack;
		String xml = kp.remove(Config.NAME_SPACE + game.getURIToString(), Config.NAME_SPACE + "hasScore", null, "URI", "literal");
		ack = xml_tools.isRemoveConfirmed(xml);
		if(ack){
			xml = kp.insert(Config.NAME_SPACE + game.getURIToString(), Config.NAME_SPACE + "hasScore", game.getScore(), "URI", "literal");
			ack = xml_tools.isInsertConfirmed(xml);
			Debug.print(2, this.getClass().getCanonicalName() + ": updateGameScore: " + "The score in " + game.getURIToString() + " is now " + game.getScore());
		}
		else{
			System.err.println("Error updatig score");
		}
		return ack;
	}
	
	@Override
	public void watchForWaitingGames(IClientObserver observer) {
		waitingGamesSubscription = new SIBSubscriptionWaitingGames(observer);
	}

	@Override
	public void unwatchForWaitingGames() {
		if (waitingGamesSubscription != null && waitingGamesSubscription.getSubID() != null){
			waitingGamesSubscription.kpic_UnsubscribeEventHandler(waitingGamesSubscription.getSubID());
		}
	}

	@Override
	public void watchForIncomingPlayer(IGame game, IClientObserver observer) {
		incomingPlayerSubscription = new SIBSubscriptionWaitingIncomingPlayer(game, observer);
	}

	@Override
	public void unwatchForIncomingPlayer() {
		if (incomingPlayerSubscription != null && incomingPlayerSubscription.getSubID() != null){
			incomingPlayerSubscription.kpic_UnsubscribeEventHandler(incomingPlayerSubscription.getSubID());
		}		
	}

	@Override
	public void watchForHit(IGame game, IPlayer player, IClientObserver observer) {
		hitSubscription = new SIBSubscriptionHit(game, observer);
	}

	@Override
	public void unwatchForHit() {
		if (hitSubscription != null && hitSubscription.getSubID() != null)
			hitSubscription.kpic_UnsubscribeEventHandler(hitSubscription.getSubID());
	}

	@Override
	public void watchForGameEnded(IGame game, IClientObserver observer) {
		leaveSubscription = new SIBSubscriptionLeaveGame(game, observer);
	}

	@Override
	public void unwatchForGameEnded() {
		if (leaveSubscription != null && leaveSubscription.getSubID() != null)
			leaveSubscription.kpic_UnsubscribeEventHandler(leaveSubscription.getSubID());
	}

	@Override
	public void unwatchAll() {
		unwatchForIncomingPlayer();
		unwatchForGameEnded();
		unwatchForHit();
		unwatchForWaitingGames();
	}

}
