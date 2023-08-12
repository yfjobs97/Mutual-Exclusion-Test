import com.sun.nio.sctp.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.io.*;
import java.util.Scanner;
/*Keyclass handles with key related operation.
keyInit function assigns keys to each node according to the node number. node 0 will have all keys it needs at the beginning. It will also generate keys required to enter critical section.
keyAdd and KeyAddNoOutput functions adds keys into myKeys 
keyRemove function removes key from my keys
keyFind function tells whether a key exist in myKeys
keyFindBatch function finds what key it still needs to get into critical section.*/
class Keyclass {
    static String myKeys = "";
    static String requiredKeys = "";
    static String keysToRequest = "";
  public static void keyInit(int nodeNum, int totalNodes){ 
      for(int i = nodeNum + 1; i < totalNodes; i++){    //Generating keys on hand
            myKeys = myKeys.concat(nodeNum + "," + i + "|");   //Here. keypairs are generated. If nodeNum=1, totalNodes =10 myKeys="1,2|1,3|1,4| ... |1,9|"
      }
      /*New procedure to generate keys needed*/
      requiredKeys="";
      for(int i = 0; i < nodeNum; i++){
          requiredKeys = requiredKeys.concat(i + "," + nodeNum + "|");
    }
      requiredKeys=requiredKeys.concat(myKeys);
       System.out.println("After initialisation, my keys are:\t" + myKeys);
       System.out.println("After initialisation, my keys required to get into Critical Section are:\t" + requiredKeys);
    }
  public static void keyAdd(String keys){
        myKeys = myKeys.concat(keys + "|");  //Depend on implementation of server to see whether correct.
        System.out.println("After adding, my keys are:\t" + myKeys);
    }
  public static void keyAddNoOutput(String keys){
        myKeys = myKeys.concat(keys + "|");  //Depend on implementation of server to see whether correct.
    }
  public static void keyRemove(String keys){
    	String[] keylist=myKeys.split("\\|");    //Depend on implementation of server to see whether correct. Beaware of the input, may need to remove the "|" afterwards.
    	String[] removelist=keys.split("\\|");
    	System.out.println("There are " + removelist.length + " keys to be removed.");
    	for(int i=0;i<removelist.length;i++) {
    		if(keyFind(removelist[i])) {
    			for(int j=0;j<keylist.length;j++) {
    				if(keylist[j].equals(removelist[i])) {
    					keylist[j]="";
    					System.out.println("Just removed key "+ removelist[i]);
    					break;
    				}
    			}
    		}
    	}
    	myKeys="";
    	for(int i=0;i<keylist.length;i++) {
    		if(!keylist[i].equals("")) {
    		keyAddNoOutput(keylist[i]);
    		}
    	}
   
        System.out.println("After removal, my keys are:\t" + myKeys);
        
    }
  public static boolean keyFind(String key){
        if(myKeys.contains(key)){//Depend on implementation of server to see whether correct. Can only find one key at a time
            System.out.println("Key " + key +" was found." );
            return true;
            
        }else{
            System.out.println("Key " + key +" was not found." );
            return false;
        }
    }
    /*keyFindBatch compares with requiredKeys*/
  public static int keyFindBatch(String keys){
        
        int numKeysToRequest = 0;
        keysToRequest="";
        String[] havekeys=keys.split("\\|");
        String[] requestkeys = requiredKeys.split("\\|");
        
        for(int i=0;i<requestkeys.length;i++) {
        	boolean doIhave=false;
        	for(int j=0;j<havekeys.length;j++) {
        		if(requestkeys[i].equals(havekeys[j])) {
        			doIhave=true;
        			break;
        		}
        	}
        	if(!doIhave) {
        		keysToRequest=keysToRequest.concat(requestkeys[i]+"|");
        		numKeysToRequest++;
        	}
        }
        
        if(keysToRequest.isEmpty()){
            System.out.println("No extra keys needed.");
            //numKeysToRequest = 0;
        }else{                
            System.out.println("This node still needs " + numKeysToRequest + " keys.");
            System.out.println("The key(s) needed to be acquired from other machines include(s):\t" + keysToRequest);
        }
        
        
        return numKeysToRequest;
    }
}

public class Node {
    static int GLOBAL_PARAMETER;
    // Size of ByteBuffer to accept incoming messages
    static int MAX_MSG_SIZE = 4096;
    static int inter_request_delay;
    static int cs_exe_time;
    static int request_to_generate;
    static NodeInfo[] overall;
    static int currentTimestamp;
    
    public static void main(String[] args) throws Exception{
        int nodeNum = Integer.parseInt(args[0]);//First argument is the node number
        String myName = args[1];//second argument is the host name
        String configLocation = args[2];
        String projectDirectory = args[3];
        Semaphore keySem = new Semaphore(1);//Creating lock for keyClass, use between server and clients
        Semaphore roundSem = new Semaphore (1); //Creating lock for rounds, use between server and clients
        
        overall = config(configLocation);//Call config function to populate overall array with all node information.
        if(overall == null){
            System.out.println("Configuration failed");
            throw new Exception();
        }
        int myPORT = overall[nodeNum].listenPort;
        int clientsCount = overall[nodeNum].neighbours.length; //number of neighbours determines number of clients
        
        Round.currentRound = nodeNum;//currentRound is the scalar timestamp. Initial value of the timestamp is their nodeNum. So P_j must ask P_i for permission for the first request if j > i.
        long[] responseTimePerRequest = new long[request_to_generate];//array to store response time
        
        Keyclass.keyInit(nodeNum,GLOBAL_PARAMETER);   //generate keys according to nodeNum
        /*Starting Server*/
        Server nodeServer = new Server(myName + "(node" + nodeNum + ") Server", myPORT, MAX_MSG_SIZE, roundSem, keySem, request_to_generate, projectDirectory);
        nodeServer.start();
        long timeProgramStart = System.nanoTime();//Metric to calculate throughput later
                
        /*Generate CS Requests*/
        for(int i = 0; i < request_to_generate; i++){
            long timeProcessGen;
            long timeProcessEnd;
            long timeDifference;//metrics to calculate response time later
            /*Compare which timestamp to use. Pick the largest timestamp found when acquiring keys from other nodes.*/
            roundSem.acquire();
            if(Round.currentRound < Round.nextRound){
                Round.currentRound = Round.nextRound;
                currentTimestamp = Round.nextRound;
            }
            else{
                currentTimestamp = Round.currentRound;
            }
            roundSem.release();
            
            timeProcessGen = System.nanoTime();
            
            cs_enter(keySem, roundSem, nodeNum, myName, clientsCount, i, currentTimestamp, projectDirectory);//Critical sesction called at the end of cs_enter
            
            timeProcessEnd = System.nanoTime();
            timeDifference = timeProcessEnd - timeProcessGen;
            responseTimePerRequest[i] = timeDifference;//store response time in array
            cs_leave(keySem, roundSem, currentTimestamp);
            Thread.sleep(inter_request_delay);
        }
        long timeProgramEnd = System.nanoTime();
        /*Statistic of response time*/
        double totalResTimeDifference = 0;
        for(int i = 0; i < responseTimePerRequest.length; i++){
            totalResTimeDifference += responseTimePerRequest[i];
        }
        /*Statistic of Throughput*/
        long overallTimeElapsed = timeProgramEnd - timeProgramStart;
        /*Write response time and throughput metrics*/ 
        roundSem.acquire();
        Round.avgResponseTime = totalResTimeDifference / responseTimePerRequest.length;
        Round.throughput = request_to_generate / (double)overallTimeElapsed;
        roundSem.release();
        
        System.out.print("\033[H\033[2J");//clean screen   
        System.out.flush();
        System.out.println("All requests for node " + nodeNum + " have been generated.");
        nodeServer.outputResult();
        nodeServer.join();
        
          
    }
    public static NodeInfo[] config(String configLocation){
        NodeInfo[] overall = null;
        try{
            File config = new File(configLocation);
            Scanner reader = new Scanner(config);
                /*Ignoring emptyLines*/
            while(!reader.hasNextInt()){
                reader.nextLine();
            }

            /*Reading file to find out total number of nodes (n), inter request delay (d), cs exec time (c)*/
            GLOBAL_PARAMETER = reader.nextInt();
            System.out.println("Global Parameter is " + GLOBAL_PARAMETER);
            overall = new NodeInfo[GLOBAL_PARAMETER];
            inter_request_delay = reader.nextInt();//d
            System.out.println("Inter Request dealy is " + inter_request_delay);
            cs_exe_time = reader.nextInt();//c
            System.out.println("Critical section execution time is " + cs_exe_time);
            request_to_generate = reader.nextInt();//request to generate
            System.out.println("CS requests to generate is " + request_to_generate);
            
            /*Ignoring emptyLines*/
            while(!reader.hasNextInt()){
                reader.nextLine();
            }
            
            /*Populate the node information.*/
            for(int i = 0; i < GLOBAL_PARAMETER; i++){
                System.out.println("Current i is " + i);
                String raw = reader.nextLine();
                while(raw.contentEquals("")){
                    raw = reader.nextLine();
                }
                System.out.println(raw);
                String noCommentInfo = raw.split("#")[0].trim();//trim white space and ignore comment
                String[] info = noCommentInfo.split(" ");
                int nodeID = Integer.parseInt(info[0]); //nodeID must strictly less than number of total nodes
                String hostName = info[1];
                int listenPort = Integer.parseInt(info[2]);
                NodeInfo n = new NodeInfo(hostName, listenPort);
                overall[nodeID] = n;
                System.out.println("Node[" + nodeID + "].hostname = " + hostName);
                System.out.println("Node[" + nodeID + "].listenPort = " + listenPort);
                
                /*Populate neighbourList*/
                String[] neighbourList = new String[GLOBAL_PARAMETER];
                for(int j = 0, k = 0; j < GLOBAL_PARAMETER; j++){//All other nodes are in the neighbour list
                    if(j != nodeID){
                        neighbourList[k] = Integer.toString(k);
                        k++;
                    }
                }
                overall[nodeID].setNeighbour(neighbourList);
            }
            reader.close();
        }catch(Exception e){
            System.out.println("There is an Exception during configuration of Node.");
            e.printStackTrace();
            System.out.println(e); 
        }
        return overall;
    }    
    public static void cs_enter(Semaphore keySem, Semaphore roundSem, int nodeNum, String myName, int clientsCount, int currentRequest, int currentTimestamp, String projectDirectory){
        try{
            keySem.acquire(); //Block Server's access to the keys. All key requests will be deferred.
            Semaphore keySemBetweenClients = new Semaphore(1);
            int numKeysToRequest = Keyclass.keyFindBatch(Keyclass.myKeys);
            System.out.println("This node ( " + nodeNum + " ) is requesting CS permission for request "+ currentRequest + " with timestamp " + currentTimestamp +" .");
            /*If there are enough keys, skip into critical section
            If not enough keys, release keys first so other nodes has the opportunity to grasp keys*/
            while( numKeysToRequest != 0){//Not enough keys
                
                if(currentTimestamp != nodeNum || numKeysToRequest > 1){//The node can be more greed before finishing its first request.
                    keySem.release();//Since it does not have enough keys anyway, it can be a bit generous to give away what it already has.
                    Thread.sleep(4000);
                    keySem.acquire();
                    numKeysToRequest = Keyclass.keyFindBatch(Keyclass.myKeys);//Do the statistics again to find what keys to acquire
                }

                /*Multithreading*/
                //Spawn Clients to acquire keys according to which keys it needs.
                Client[] clientThreads = new Client[numKeysToRequest];
                String[] requestKeysArray = Keyclass.keysToRequest.split("\\|");
                for(int i = 0; i < numKeysToRequest; i++){
                    int neighbourNodeID = Integer.parseInt(requestKeysArray[i].substring(0, 1));
                    if( neighbourNodeID == nodeNum ){ //Assume keys in the array has a format of "num1,num2". If the node is missing this key, the num that is not equal to nodeNum must be the server to connect. 
                        neighbourNodeID = Integer.parseInt(requestKeysArray[i].substring(2, 3));
                    }
                    
                    String neighbourName = overall[neighbourNodeID].hostName;
                    int neighbourPORT = overall[neighbourNodeID].listenPort;
                    System.out.println("At slot " + i + " of requestKeysArray, Node to acquire key "+ requestKeysArray[i] +" is " + neighbourNodeID);
                    System.out.println("Hostname of this node is " + neighbourName);
                    System.out.println("Port of this node is " + neighbourPORT);
                    Client c = new Client(myName + "(node" + nodeNum + ") Client " + i, neighbourName, neighbourPORT, MAX_MSG_SIZE, keySemBetweenClients, roundSem, nodeNum, requestKeysArray[i], currentTimestamp, numKeysToRequest);
                    clientThreads[i] = c;
                }
                /*spawning client threads*/
                for (int i = 0; i < requestKeysArray.length; i++){
                    clientThreads[i].start();
                    try{
                        Thread.sleep(3000);
                    }catch(Exception e){
                        System.out.println("There is an Exception during Joining of Nodes Clients for node " + nodeNum + " .");
                        e.printStackTrace();
                        System.out.println(e); 
                    }
                }
                /*Joining client threads*/
                for (int i = 0; i < requestKeysArray.length; i++){
                    clientThreads[i].join();
                }
                /*Find out what keys the node has and what keys it still needs to get*/
                numKeysToRequest = Keyclass.keyFindBatch(Keyclass.myKeys);
                System.out.println("My keys are: " + Keyclass.myKeys);
            }
        }catch (Exception e){
            System.out.println("There is an Exception during entrant of critical section.");
            e.printStackTrace();
            System.out.println(e); 
        }
        /*Enter critical section*/
        criticalSection(nodeNum, currentRequest, currentTimestamp, projectDirectory); 
    }
    /*Critical section records RTC timestamp for mutex verification*/
    public static void criticalSection(int nodeNum, int currentRequest, int currentTimestamp, String projectDirectory){
        try{
            System.out.print("\033[H\033[2J");//clean screen   
            System.out.flush(); 
            System.out.println("Node " + nodeNum + " has been in critical section " + currentRequest + " time(s). Current Timestamp is " + currentTimestamp + ".");
            BufferedWriter out = null;  
            try {  
                String filenameString = projectDirectory + "/test/node"+nodeNum+".txt";
                out = new BufferedWriter( new OutputStreamWriter(  
                                new FileOutputStream(filenameString, true)));  
                int size = 15;
                long starttimes;
                long stoptimes;
                starttimes = System.nanoTime();
                String startString="Nodenum:"+nodeNum+" start "+currentRequest+": "+String.valueOf(starttimes)+'\n';
                out.write(startString);


                Thread.sleep(cs_exe_time);


                stoptimes = System.nanoTime();
                String endString="Nodenum:"+nodeNum+" end "+currentRequest+": "+String.valueOf(stoptimes)+'\n';
                out.write(endString);
                
                out.close();

            } catch (IOException e) {  
                            e.printStackTrace();  

            }  
        }catch (Exception e){
            System.out.println("There is an Exception in critical section.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
    public static void cs_leave(Semaphore keySem, Semaphore roundSem, int currentTimestamp){
        keySem.release();
        try{
            roundSem.acquire();
            Round.currentRound++;//Round is the scalar timestamp. It will increase as one request completes.
            currentTimestamp++;
            roundSem.release();
            System.out.println("Finishing Leaving Critical section. Current Timestamp is " + currentTimestamp + ".");
        }catch (Exception e){
            System.out.println("There is an Exception when leaving critical section.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
    
}
class NodeInfo{
    String hostName;
    int listenPort;
    String[] neighbours;
        
    NodeInfo(String name, int port){
        this.hostName = name;
        this.listenPort = port;
    }
    public void setNeighbour(String[] neighbourList){
        this.neighbours = neighbourList;
    }
}
class Round {
    public static int currentRound = 0;//Current Timestamp
    public static int nextRound = 0;//Potential timestamp to use
    public static int msgCount = 0;//Send message count
    public static double avgResponseTime;
    public static double throughput;
}