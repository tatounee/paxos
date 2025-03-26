package paxos;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;


public class Main {

    public static int N = 100;
    public static int f = 49;
    
    public static double alpha = 0.1;
    
    public static int timeout = 2000; // in ms


    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N + " , alpha = " + alpha +  " and " + "timeout = " + timeout + ". ");

        ArrayList<ActorRef> references = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N,alpha), "" + i);
            references.add(a);
        }

        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }
        
        Collections.shuffle(references);
        
        //giving the launch message for actors and crash message for the f faulty process
        for (int i = 0 ; i < f; i++) {
        	ActorRef actor = references.get(i);
        	OfconsProposerMsg opm = new OfconsProposerMsg(new Random().nextInt(2));
        	actor.tell(opm, ActorRef.noSender());
        	actor.tell(new CrashMsg(),ActorRef.noSender());
        }
        
        
        //giving the launch message for the other actors
        for (int i =f; i < N;i++) {
        	ActorRef actor = references.get(i);
        	OfconsProposerMsg opm = new OfconsProposerMsg(new Random().nextInt(2));
        	actor.tell(opm, ActorRef.noSender());
        }
        
        //electing a non faulty leader
        int leaderIndex = new Random().nextInt(f, N); 
        
        //waiting timeout before holding all the processus
        system.scheduler().scheduleOnce(
        		Duration.create(timeout, TimeUnit.MILLISECONDS),
        		() -> electLeader(references,system,leaderIndex),
        		system.dispatcher());
    }
    
    private static void electLeader (ArrayList<ActorRef> references, ActorSystem system, int leaderIndex) {
    	ActorRef leader = references.get(leaderIndex);
    	system.log().info("[LEADER] p" + leader.path().name()+" is now the leader!");
    	for(int i =0; i < Main.N; i++) {
    		if (i!=leaderIndex) {
    			ActorRef actor = references.get(i);
    			actor.tell(new HoldingMsg(),ActorRef.noSender());
    		}
    	}
    }
}
