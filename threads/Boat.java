//
package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat {
    /*
      NOTE: island B := molokai, island A := oahu
      Our general algorithm is to ferry all the children from A to B by having two children row together from A to B and only one rowing back to A. After all of the children go, then one rows back to A, wakes up an adult. The adult rows to B, wakes up a child, and then is done. The child on B who woke up, rows to A, takes both children from A to B, and then one child rows back and repeats the process if there is an adult waiting to go. When it reaches to point where there are no more adults on A, the child rows back to B and finishes.
      Because adults never row at first (because there are always 2 children on A at the beginning)
     */

    static BoatGrader bg;

    //we keep a count of the number of children on each island,
    //the number of adults on each island, the number of children ready to go to B, and the number of
    //children (0, 1, or 2) who just rowed together from A to B


    // count variables
    private static int OahuChildCount = 0;
    private static int MolokiChildCount = 0;
    private static int OahuAdultCount = 0;
    private static int MolokiAdultCount = 0;
    private static int childWaitingForBoatCount = 0;
    private static int ChildOnBoat = 0;
    private static int OahuPopulation = 0; 

    private static boolean boatIsAtOahu = false;

    //lock an island when we're trying to access it so concurrent threads don't access an island at the same time
    private static Lock Oahu = new Lock();
    private static Lock Moloki = new Lock();

    private static Condition2 adultOahu = new Condition2(Oahu);
    private static Condition2 childWaitingOnOahu = new Condition2(Moloki);
    private static Condition2 childWaitingOnMoloki = new Condition2(Oahu);
    private static Condition2 childWaitingForBoat = new Condition2(Oahu);
    
    private static Communicator arrivalMessage = new Communicator(); 

    //the main thread waits until our code says it's done
    private static Semaphore done = new Semaphore(0);

    private boolean currentLocation;

    public static void selfTest() {
        BoatGrader b = new BoatGrader();


        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
        begin(3, 3, b);
    }

    public static void begin( int adults, int children, BoatGrader b ) {
        // initialize global variables
        OahuChildCount = children;
        MolokiChildCount = 0;
        OahuAdultCount = adults;
        MolokiAdultCount = 0;
        childWaitingForBoatCount = 0;
        ChildOnBoat = 0;
        boatIsAtOahu = true;

        for(int i = 0; i < OahuChildCount; i++) {
        	 
        	KThread childThread = new KThread(new Runnable () {
        		public void run() {
        			ChildItinerary(); 
        		}	
        	}); 
        	
        	childThread.setName("child" + i); 
        	childThread.fork();   	
        }
        
        
        for(int i = 0; i < OahuAdultCount; i++) {
       	 
        	KThread adultThread = new KThread(new Runnable () {
        		public void run() {
        			AdultItinerary(); 
        		}	
        	}); 
        	
        	adultThread.setName("adult" + i); 
        	adultThread.fork();   	
        }
        
        
      

        int MolokiPopulation = arrivalMessage.listen();

        if(MolokiPopulation == (MolokiChildCount+ MolokiAdultCount)) { 
            done.V();
        }
        
    }

    static void AdultItinerary() {
        bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.

        Oahu.acquire();

        while (OahuChildCount > 1 || !boatIsAtOahu) {
            adultOahu.sleep(); /* sleep till no children or boat */
        }

        OahuAdultCount--;
        Oahu.release();
        bg.AdultRowToMolokai();
        Moloki.acquire();
        MolokiAdultCount++;
        childWaitingForBoat.wake();  /* wake child now */
        Moloki.release(); /* release this adult */

    }

    static void ChildItinerary()  {
        bg.initializeChild(); //Required for autograder interface. Must be the first thing called.

        while (OahuChildCount + OahuAdultCount > 1) {
                Oahu.acquire();

            if (OahuChildCount == 1) {
                adultOahu.wake();
            }

            while (childWaitingForBoatCount >= 2 || !boatIsAtOahu) {
                childWaitingOnOahu.sleep();
            }

            if (childWaitingForBoatCount == 0) {
                // no children waiting
                childWaitingForBoatCount++;
                childWaitingOnOahu.wake(); // get sum frends
                childWaitingForBoat.sleep();
                bg.ChildRideToMolokai();
                childWaitingForBoat.wake();
            } else {
                childWaitingForBoatCount++;
                childWaitingForBoat.wake();
                bg.ChildRowToMolokai();
                childWaitingForBoat.sleep();
            }

            // we left oahu
            
            childWaitingForBoatCount--;
            OahuChildCount--;
            OahuPopulation = (OahuChildCount) + (OahuAdultCount); 
            boatIsAtOahu = false;
            Oahu.release();

            
            
            // we arrive at Molokai
            arrivalMessage.speak(OahuPopulation);
            Moloki.acquire();
            MolokiChildCount++;

            if (ChildOnBoat == 1) {
                childWaitingOnMoloki.sleep();
            }

            ChildOnBoat = 0;
            Moloki.release();

            // we go back to oahu with one child to collect more
            bg.ChildRowToOahu();
            Oahu.acquire();
            OahuChildCount++;
            boatIsAtOahu = true;
            Oahu.release();
        }

        // check who’s on the original island
        Oahu.acquire();
        OahuChildCount--;
        Oahu.release();
        bg.ChildRowToMolokai();
        Moloki.acquire();
        MolokiChildCount++;
        Moloki.release();
        
        System.out.println("Children A/B: " + OahuChildCount + " / " + MolokiChildCount);
        System.out.println("Adults A/B: " + OahuAdultCount + " / " + MolokiAdultCount);
        done.V();
    }

    static void SampleItinerary() {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }

}