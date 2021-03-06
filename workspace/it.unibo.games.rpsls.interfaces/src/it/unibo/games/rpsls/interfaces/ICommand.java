package it.unibo.games.rpsls.interfaces;


public interface ICommand extends Comparable<ICommand>, ISimpleEntity {

	public String getCommandType();
	public void setCommandType(String name);
	
	public void setIssuer(IPlayer player);
	public IPlayer getIssuer();
	
	public boolean equals(ICommand i);
	
	public void setTime(long time);
	public long getTime();
}
