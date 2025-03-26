package paxos;

public class GatherMsg {
	
	public int ballot;
	public int estballot;
	public Integer est;
	public int pjId;
	
	public GatherMsg(int ballot,int estballot, Integer est,int pjId) {
		this.ballot = ballot;
		this.estballot = estballot;
		this.est = est;
		this.pjId = pjId;
	}
}
