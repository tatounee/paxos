package paxos;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references

    private int ballot;
    private Integer proposal;
    private int readballot;
    private int imposeballot;
    private Integer estimate;
    private States states;
    private int nbAck;
    private Integer decidedValue;
    private boolean silentMode;
    private double alpha;
    private boolean mightCrash;
    private long startingTime;
    
    private boolean isHolding;
    
    private int tryingValue;
    
    public Process(int ID, int nb,double alpha) {
        N = nb;
        id = ID;
        this.alpha = alpha;
        this.ballot = ID-nb;
        this.proposal = null;
        this.readballot = 0;
        this.imposeballot = ID-nb;
        this.estimate = null;
        
        this.states = new States(nb);
        this.decidedValue = null;
        this.silentMode = false;
        this.mightCrash = false;
        this.isHolding = false;
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb,double alpha) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb,alpha);
        });
    }
    
    
    private void ofconsProposeReceived(Integer v) {
    	if (!isHolding) {
	    	nbAck = 0;
	        proposal = v;
	        ballot = ballot + N;
	        states.reset();
	        
	        log.debug("[" + startingTime + "] [START] p" + self().path().name() + " propose with ballot :" + ballot + " and value: " + v);
	        
	        for (ActorRef actor : processes.references) {
	            actor.tell(new ReadMsg(ballot), this.getSelf());
	            log.debug(System.currentTimeMillis() + " :[SENDING] Read (ballot = " + ballot + ") msg: p" + self().path().name() + " -> p" + actor.path().name());
	        }
    	}
    }
    
    private void readReceived(int newBallot, ActorRef pj) {
            if ((readballot > newBallot) || (imposeballot > newBallot)) {
            	pj.tell(new AbortMsg(newBallot), getSelf());
            	log.debug(System.currentTimeMillis() +"[SENDING] Abort (ballot = " + newBallot + ") msg: p" + self().path().name() + " -> p" + pj.path().name());
            }
            else {
            	readballot = newBallot;
            	
            	pj.tell(new GatherMsg(newBallot,imposeballot,estimate,id), getSelf());
            	log.debug(System.currentTimeMillis() +
            		"[SENDING] Gather (ballot = " + newBallot + 
            						", imposeballot = " + imposeballot + 
            						", estimate = " + estimate +
            		") msg: p" + self().path().name() + " -> p" + pj.path().name()
            	);
            }
    }
    
    private void abortReceived(int ballot) {
    	if (ballot == this.ballot) {
    		ofconsProposeReceived(tryingValue); //retry
    	}
    }
    
    private void gatherReceived(int ballot,int estballot,Integer est,int pjId) {
    	if (ballot == this.ballot) {
    		states.set(pjId-1, est,estballot);
    		
    		if (states.getNbAnswer() > N/2 && states.getNbAnswer()-1 <= N/2) { //if the majority was reached for the first time
    			if (states.highestEstBallot>0) {
    				State chosenState = states.highestState;
    				this.proposal = chosenState.est;
    			}
    			
    	        for (ActorRef actor : processes.references) {
    	            actor.tell(new ImposeMsg(ballot,proposal), this.getSelf());
    	            log.debug(System.currentTimeMillis() + 
    	            	":[SENDING] Impose (ballot = " + ballot + ", proposal = " + proposal + ") msg: p" + self().path().name() + " -> p" + actor.path().name()
    	            );
    	        }
    		}
    	}
    }
    
    private void imposeReceived(int newBallot,int v, ActorRef pj) {
    	if (readballot> newBallot || imposeballot > ballot) {
    		pj.tell(new AbortMsg(newBallot), getSelf());
    		log.debug(System.currentTimeMillis() +"[SENDING] Abort (ballot = " + newBallot + ") msg: p" + self().path().name() + " -> p" + pj.path().name());
    	}
    	else {
    		estimate = v;
    		imposeballot = newBallot;
    		pj.tell(new AckMsg(newBallot), getSelf());
    		log.debug(System.currentTimeMillis() +"[SENDING] Ack (ballot = " + newBallot + ") msg: p" + self().path().name() + " -> p" + pj.path().name());
    	}
    }
    
    private void ackReceived(int ballot) {
    	nbAck++;
    	if (nbAck > N/2 && nbAck-1 <= N/2) { //if the majority was reached for the first time
    		for (ActorRef actor : processes.references) {
	            actor.tell(new DecideMsg(proposal), this.getSelf());
	            log.debug(System.currentTimeMillis() +"[SENDING] Decide (v = " + proposal + ") msg: p" + self().path().name() + " -> p" + actor.path().name());
	        }
    	}
    }
    
    private void decideReceived(int v) {
		for (ActorRef actor : processes.references) {
            actor.tell(new DecideMsg(proposal), this.getSelf());
            log.debug(System.currentTimeMillis() +"[SENDING] Decide (v = " + proposal + ") msg: p" + self().path().name() + " -> p" + actor.path().name());
        }
		decidedValue = v;
		long actualTime = System.currentTimeMillis();
		log.info(actualTime + "[FINISHED] process p" + self().path().name() + " has decided on value " + v + " in " + (actualTime - startingTime) + "ms!");
    }
    
	public void onReceive(Object message) throws Throwable {
	    long timestamp = System.currentTimeMillis();
	    if (mightCrash && new Random().nextDouble() < alpha && !silentMode) {
	        this.silentMode = true;
	        log.warning("[" + timestamp + "] [CRASH] p" + self().path().name() + " has entered silent mode due to crash probability.");
	    }
	    if (decidedValue == null && !silentMode) {
	        if (message instanceof Members) {
	            Members m = (Members) message;
	            processes = m;
	            log.debug("[" + timestamp + "] [INIT] p" + self().path().name() + " received system debugrmation.");
	        } 
	        else if (message instanceof OfconsProposerMsg) {
	            OfconsProposerMsg m = (OfconsProposerMsg) message;
	    		
	    		startingTime = timestamp;
	            this.tryingValue = m.v;
	            this.ofconsProposeReceived(m.v);
	        } 
	        else if (message instanceof ReadMsg) {
	            ReadMsg m = (ReadMsg) message;
	            log.debug("[" + timestamp + "] [RECEIVE] p" + self().path().name() + " received ReadMsg(ballot=" + m.ballot + ") from p" + getSender().path().name());
	            this.readReceived(m.ballot, getSender());
	        } 
	        else if (message instanceof AbortMsg) {
	            AbortMsg m = (AbortMsg) message;
	            log.debug("[" + timestamp + "] [ABORT] p" + self().path().name() + " received AbortMsg(ballot=" + m.ballot + ")");
	            this.abortReceived(m.ballot);
	        } 
	        else if (message instanceof GatherMsg) {
	            GatherMsg m = (GatherMsg) message;
	            log.debug("[" + timestamp + "] [GATHER] p" + self().path().name() + " received GatherMsg(ballot=" + m.ballot + ", estBallot=" + m.estballot + ", est=" + m.est + ", pjId=" + m.pjId + ")");
	            this.gatherReceived(m.ballot, m.estballot, m.est, m.pjId);
	        } 
	        else if (message instanceof ImposeMsg) {
	            ImposeMsg m = (ImposeMsg) message;
	            log.debug("[" + timestamp + "] [IMPOSE] p" + self().path().name() + " received ImposeMsg(ballot=" + m.ballot + ", value=" + m.v + ") from p" + getSender().path().name());
	            this.imposeReceived(m.ballot, m.v, getSender());
	        } 
	        else if (message instanceof AckMsg) {
	            AckMsg m = (AckMsg) message;
	            log.debug("[" + timestamp + "] [ACK] p" + self().path().name() + " received AckMsg(ballot=" + m.ballot + ")");
	            this.ackReceived(m.ballot);
	        } 
	        else if (message instanceof DecideMsg) {
	            DecideMsg m = (DecideMsg) message;
	            log.debug("[" + timestamp + "] [DECISION] p" + self().path().name() + " received DecideMsg(value=" + m.v + ") -> Decision made!");
	            this.decideReceived(m.v);
	        } 
	        else if (message instanceof CrashMsg) {
	            this.mightCrash = true;
	            log.debug("[" + timestamp + "] [CRASH] p" + self().path().name() + " might crash now.");
	        } 
	        else if (message instanceof HoldingMsg) {
	            this.isHolding = true;
	            log.debug("[" + timestamp + "] [HOLDING] p" + self().path().name() + " is now in holding mode and will not respond.");
	        }
	    }
	}

}
