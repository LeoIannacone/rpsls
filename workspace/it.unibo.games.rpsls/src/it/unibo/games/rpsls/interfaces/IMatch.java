package it.unibo.games.rpsls.interfaces;

public interface IMatch {
	
	public void setHomePlayer(IPlayer home);
	public IPlayer getHomePlayer();
	
	public void setGuestPlayer(IPlayer guest);
	public IPlayer getGuestPlayer();
	
	public void setHomeScore(int n);
	public void increaseHomeScore();
	public int getHomeScore();
	
	public void setGuestScore(int n);
	public void increaseGuestScore();
	public int getGuestScore();
	
	public void setStatus(String status);
	public String getStatus();
	
}
