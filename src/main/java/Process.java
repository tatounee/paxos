
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
	        
	        for (ActorRef actor : processes.references) {
	            actor.tell(new ReadMsg(ballot), this.getSelf());
	            log.info("Read ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
	        }
    	}
    }
    
    private void readReceived(int newBallot, ActorRef pj) {
            log.info("read received " + self().path().name() );
            if ((readballot > newBallot) || (imposeballot > newBallot)) {
            	pj.tell(new AbortMsg(newBallot), getSelf());
            	log.info("Abort ballot " + newBallot + " msg: p" + self().path().name() + " -> p" + pj.path().name());
            }
            else {
            	readballot = newBallot;
            	
            	pj.tell(new GatherMsg(newBallot,imposeballot,estimate,id), getSelf());
            }
    }
    
    private void abortReceived(int ballot) {
    	if (ballot == this.ballot) {
    		log.info("Abort ballot " + ballot + "msg : p" + self().path().name());
    		ofconsProposeReceived(tryingValue); //retry
    	}
    }
    
    private void gatherReceived(int ballot,int estballot,Integer est,int pjId) {
    	if (ballot == this.ballot) {
    		log.info("Gather ballot");
    		states.set(pjId, est,estballot);
    		
    		if (states.getNbAnswer() > N/2 && states.getNbAnswer()-1 <= N/2) { //if the majority was reached for the first time
    			if (states.highestEstBallot>=0) {
    				State chosenState = states.highestState;
    				this.proposal = chosenState.est;
    			}
    			
    	        for (ActorRef actor : processes.references) {
    	            actor.tell(new ImposeMsg(ballot,proposal), this.getSelf());
    	            log.info("Impose ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
    	        }
    		}
    	}
    }
    
    private void imposeReceived(int newBallot,int v, ActorRef pj) {
    	if (readballot> newBallot || imposeballot > ballot) {
    		pj.tell(new AbortMsg(newBallot), getSelf());
    	}
    	else {
    		estimate = v;
    		imposeballot = newBallot;
    		pj.tell(new AckMsg(newBallot), getSelf());
    	}
    }
    
    private void ackReceived(int ballot) {
    	nbAck++;
    	if (nbAck > N/2 && nbAck-1 <= N/2) { //if the majority was reached for the first time
    		for (ActorRef actor : processes.references) {
	            actor.tell(new DecideMsg(proposal), this.getSelf());
	            log.info("Decide ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
	        }
    	}
    }
    
    private void decideReceived(int v) {
		for (ActorRef actor : processes.references) {
            actor.tell(new DecideMsg(proposal), this.getSelf());
            log.info("Decide ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
        }
		decidedValue = v;
    }
    
    public void onReceive(Object message) throws Throwable {
    	if (mightCrash && new Random().nextDouble() < alpha) {
    		this.silentMode = true;
    	}
    	if (decidedValue==null && !silentMode) {
	          if (message instanceof Members) {//save the system's info
	              Members m = (Members) message;
	              processes = m;
	              log.info("p" + self().path().name() + " received processes info");
	          }
	          else if (message instanceof OfconsProposerMsg) { // launch message
	              OfconsProposerMsg m = (OfconsProposerMsg) message;
	              this.tryingValue = m.v;
	              this.ofconsProposeReceived(m.v);
	      
	          }
	          
	          //Paxos messages
	          else if (message instanceof ReadMsg) {
	              ReadMsg m = (ReadMsg) message;
	              this.readReceived(m.ballot, getSender());
	          }
	          else if (message instanceof AbortMsg) {
	        	  AbortMsg m = (AbortMsg) message;
	        	  this.abortReceived(m.ballot);
	          }
	          else if (message instanceof GatherMsg) {
	        	  GatherMsg m = (GatherMsg) message;
	        	  this.gatherReceived(m.ballot,m.estballot,m.est,m.pjId);
	          }
	          else if (message instanceof ImposeMsg) {
	        	  ImposeMsg m = (ImposeMsg) message;
	        	  this.imposeReceived(m.ballot,m.v,getSender());
	          }
	          else if (message instanceof AckMsg) {
	        	  AckMsg m = (AckMsg) message;
	        	  this.ackReceived(m.ballot);
	          }
	          else if (message instanceof DecideMsg) {
	        	  DecideMsg m = (DecideMsg) message;
	        	  this.decideReceived(m.v);
	          }
	          else if (message instanceof CrashMsg) {
	        	  this.mightCrash = true;
	          }
	          else if (message instanceof HoldingMsg) {
	        	  this.isHolding = true;
	          }
    		}
    	}
}
