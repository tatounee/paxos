package paxos;

public class ImposeMsg {
	
	public int ballot;
	public int v;
	
	public ImposeMsg(int ballot,int v) {
		this.ballot = ballot;
		this.v = v;
	}
}
