package it.unibo.games.rpsls.gui;

import it.unibo.games.rpsls.connector.SIBConnector;
import it.unibo.games.rpsls.connector.SIBFactory;
import it.unibo.games.rpsls.game.Game;
import it.unibo.games.rpsls.game.Player;
import it.unibo.games.rpsls.interfaces.IConnector;
import it.unibo.games.rpsls.interfaces.IGame;
import it.unibo.games.rpsls.interfaces.ICommand;
import it.unibo.games.rpsls.interfaces.IObserver;
import it.unibo.games.rpsls.interfaces.IPlayer;
import it.unibo.games.rpsls.prototypes.SimpleConnectorPrototype;
import it.unibo.games.rpsls.utils.Debug;

import java.awt.Color;
import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class MainWindow implements IObserver {

	public static Color LIGHT_COLOR = Color.GRAY;
	
	
	private JFrame frame;
	
	private ViewWelcome viewWelcome;
	private ViewMatch viewMatch;
	private ViewJoinGame viewJoinGame;
	private ViewWin viewWin;
	
	private SIBConnector connector;
	
	private IPlayer me;
	private IPlayer enemy;
	private IGame current_game;
	
	private String ME_FILENAME = "player.info";
	
	public int MAX_RESULT = 3;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Debug.setLevel(2);
					MainWindow window = new MainWindow();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public MainWindow() {
		connector = SIBConnector.getInstance();
		initialize_me();
		initialize();
	}
	
	private void initialize_me() {
		try {
			InputStreamReader f = new InputStreamReader(new FileInputStream(ME_FILENAME));
			BufferedReader r = new BufferedReader(f);
			String line = r.readLine();
			String[] info = line.split(" ");
			me = new Player(info[0]);
			me.setURI(info[1]);
			r.close();
			me = SIBFactory.getInstance().getPlayer(me.getURIToString());
		} catch (Exception e) {
			me = null;
		}
	}
	
	private void set_me(IPlayer p) {
		if (p == null)
			return;
		if (me == null || ! me.getName().equals(p.getName())) {
			me = p;
			store_me();
		}
	}
	
	private void store_me() {
		try {
			PrintWriter o = new PrintWriter(ME_FILENAME);
			String info = me.getName() + " " +  me.getURIToString();
			o.println(info);
			o.close();
			connector.createNewPlayer(me);
		} catch (Exception e) {
		}
		
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("RPSLS");
		frame.setResizable(false);
		frame.setSize(315, 450);
			
		showViewWelcome();
	}
	
	public void showViewWin() {
		viewWin = new ViewWin(current_game);
		viewWin.setMainWindow(this);
		showView(viewWin);
	}
	
	public void showViewWelcome() {
		viewWelcome = new ViewWelcome(me);
		viewWelcome.setMainWindow(this);
		connector.unwatchForWaitingGames();
		connector.unwatchForIncomingPlayer();
		showView(viewWelcome);
	}
	
	public void showViewMatch() {
		connector.watchForHit(current_game, current_game.getOpponent(), this);
		showView(viewMatch);
	}
	
	private void showView(JPanel noHide) {
		frame.getContentPane().removeAll();
		frame.getContentPane().add(noHide);
		
		frame.getContentPane().repaint();
		frame.getContentPane().revalidate();
	
		noHide.repaint();
		noHide.revalidate();
	}
	
	public void deleteGame() {
		
	}
	
	public void createNewGame(IPlayer player) {
		set_me(player);
		current_game = new Game(me, null);
		connector.createNewGame(current_game);
		connector.watchForIncomingPlayer(current_game, this);
	}
	
	public void joinGame(IGame game) {
		current_game = game;
		set_me(me);
		current_game.setGuestPlayer(me);
		current_game.setHomeAsOpponent(true);
		enemy = game.getHomePlayer();
		connector.joinGame(current_game, me);
		viewMatch = new ViewMatch(current_game, false);
		viewMatch.setMainWindow(this);
		showViewMatch();
	}
	
	public void showJoinGames(IPlayer player) {
		set_me(player);
		viewJoinGame = new ViewJoinGame();
		viewJoinGame.setMainWindow(this);
		viewJoinGame.reset();
		showView(viewJoinGame);
		connector.watchForWaitingGames(this);
	}
	
	public void sendHit(ICommand hit) {
		connector.sendHit(current_game, me, hit);
	}

	@Override
	public void updateWaitingGames(List<IGame> games) {
		viewJoinGame.appendWaitingGames(games);
	}

	@Override
	public void updateWaitingGames(IGame game) {
		viewJoinGame.appendWaitingGames(game);		
	}

	@Override
	public void updateHit(ICommand hit) {
		viewMatch.receivedEnemyHit(hit);
	}

	@Override
	public void updateIncomingPlayer(IGame game) {
		current_game = game;
		enemy = game.getGuestPlayer();
		viewMatch = new ViewMatch(current_game, true);
		viewMatch.setMainWindow(this);
		showViewMatch();
	}
}
