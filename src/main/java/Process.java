
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
    
    public Process(int ID, int nb) {
        N = nb;
        id = ID;
        this.ballot = ID-nb;
        this.proposal = null;
        this.readballot = 0;
        this.imposeballot = ID-nb;
        this.estimate = null;
        
        this.states = new States(nb);
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }
    
    
    private void ofconsProposeReceived(Integer v) {

        proposal = v;
        ballot = ballot + N;
        states.reset();
        
        for (ActorRef actor : processes.references) {
            actor.tell(new ReadMsg(ballot), this.getSelf());
            log.info("Read ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
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
            	
            	pj.tell(new GatherMsg(newBallot,imposeballot,estimate), getSelf());
            }
    }
    
    private void abortReceived
    
    
    public void onReceive(Object message) throws Throwable {
          if (message instanceof Members) {//save the system's info
              Members m = (Members) message;
              processes = m;
              log.info("p" + self().path().name() + " received processes info");
          }
          else if (message instanceof OfconsProposerMsg) {
              OfconsProposerMsg m = (OfconsProposerMsg) message;
              this.ofconsProposeReceived(m.v);
      
          }
          else if (message instanceof ReadMsg) {
              ReadMsg m = (ReadMsg) message;
              this.readReceived(m.ballot, getSender());
          }
}
}
