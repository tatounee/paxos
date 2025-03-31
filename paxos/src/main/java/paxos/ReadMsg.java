package paxos;

public class ReadMsg {
	
	public int ballot;
	
	public ReadMsg(int ballot) {
		this.ballot = ballot;
	}
}
