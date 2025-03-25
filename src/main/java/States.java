import java.util.ArrayList;

public class States extends ArrayList<State>{
	private static final long serialVersionUID = 1L;
	
	private int size;
	private int nbAnswer;
	
	public States(int size) {
		super(size);
		this.size = size;
		reset();
	}
	
	public void set(int index, Integer est, int estballot ) {
		set(index,new State(est,estballot));
		nbAnswer++;
	}
	
	
	public int getNbAnswer() {
		return nbAnswer;
	}
	
	public void reset() {
		clear();
		for (int i =0; i < size; i++) {
			add(new State());
		}
		
		this.nbAnswer = 0;
	}
}
