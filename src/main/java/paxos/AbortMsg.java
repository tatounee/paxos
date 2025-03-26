package paxos;

public class AbortMsg {
	
	public int ballot;
	
	public AbortMsg(int ballot) {
		this.ballot = ballot;
	}
}
