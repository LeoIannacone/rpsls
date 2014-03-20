package it.unibo.games.rpsls.connector;

import it.unibo.games.rpsls.interfaces.ICommand;
import it.unibo.games.rpsls.interfaces.IGame;
import it.unibo.games.rpsls.interfaces.IObserver;
import it.unibo.games.rpsls.utils.Debug;

import java.util.Vector;

import sofia_kp.KPICore;
import sofia_kp.SSAP_sparql_response;

public class SIBSubscriptionHit extends SIBSubscription {

	protected IObserver observer;

	protected static String SUBSCRIPTION_QUERY= "SELECT ?uri_command WHERE { " +
			"<http://rpsls.games.unibo.it/Ontology.owl#%s> <http://rpsls.games.unibo.it/Ontology.owl#HasCommand> ?uri_command . " +
			"?uri_command <http://rpsls.games.unibo.it/Ontology.owl#HasIssuer> <http://rpsls.games.unibo.it/Ontology.owl#%s> }";


	public SIBSubscriptionHit(IGame game, IObserver observer){
		String xml = "";
		this.observer = observer;
		kp = new KPICore(Config.SIB_HOST, Config.SIB_PORT, Config.SIB_NAME);
		kp.join();
		xml = kp.subscribeSPARQL(String.format(SUBSCRIPTION_QUERY, game.getCommandInterface().getURIToString(), game.getOpponent().getURIToString()), this);
		subID = null;
		if(xml_tools.isSubscriptionConfirmed(xml))
		{
			try{
				subID = xml_tools.getSubscriptionID(xml);
			}
			catch(Exception e){ e.printStackTrace(); }
		}
		else{
			System.err.println ("Error during subscription");
		}	
		SSAP_sparql_response resp = xml_tools.get_SPARQL_query_results(xml);//An object to manage the sparql response
		getNewObjectsFromResults(resp);
	}

	@Override
	public void getNewObjectsFromResults(SSAP_sparql_response resp) {
		Vector<String[]> values = resp.getResultsForVar("uri_command");
		for (String[] val : values){
			String uri_command = Utils.removePrefix(SSAP_sparql_response.getCellValue(val));
			ICommand c = SIBFactory.getInstance().getHit(uri_command);
			if (observer != null){
				Debug.print(2, c.getIssuer().getURIToString() + " has played " + c.getCommandType());
				observer.updateHit(c);
			}
			else{
				System.out.println("Hit received:");
				System.out.println("  " + c.getCommandType());
			}
		}
	}
}

