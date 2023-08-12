import com.sun.nio.sctp.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.io.*;
import java.util.Scanner;

class Client implements Runnable {
   private Thread t;
   private String threadName;
   private String neighbourName;
   private int PORT;
   private int MAX_MSG_SIZE;
   private int currentTimestamp;// Scalar Timestamp
   private Semaphore sem;
   private Semaphore roundSem;
   private int nodeNum;
   String delegatedKeyToAcquire;
   private int numKeysToRequest;
   private int msgCount = 0; //Accumulating send message count
   Client(String threadName, String neighbourName, int PORT, int MAX_MSG_SIZE, Semaphore sem, Semaphore roundSem, int nodeNum, String keyToGet, int currentTimestamp, int numKeysToRequest) {
      this.threadName = threadName;
      this.neighbourName = neighbourName;
      System.out.println("Creating " +  threadName );
      this.PORT = PORT;
      this.MAX_MSG_SIZE = MAX_MSG_SIZE;
      this.sem = sem;
      this.nodeNum = nodeNum;
      this.delegatedKeyToAcquire = keyToGet;
      this.roundSem = roundSem;
      this.numKeysToRequest = numKeysToRequest;
      this.currentTimestamp = currentTimestamp;// Scalar Timestamp


   }
   
   @Override
   public void run(){
      System.out.println("Running " +  threadName );
      try {
           realWork();
      } catch (Exception e) {
         System.out.println("There is an Exception relating to Client routine.");
         e.printStackTrace();
         System.out.println(e);
      }
      System.out.println("Tasks for Thread " +  threadName + " has finished.");
   }
   
   public void start () {
      System.out.println("Starting " +  threadName );
      if (t == null) {
         t = new Thread (this, threadName);
         t.start ();
      }
   }
   public void join(){
       System.out.println("Joining " +  threadName );
       try {
           t.join();
       }catch (Exception e){
           System.out.println("There is an Exception in joining.");
           e.printStackTrace();
           System.out.println(e);
       }
       
   }
   public void realWork() throws Exception{
            //Real work as SCTPClient
        // Get address of server using name and port number
        InetSocketAddress addr = new InetSocketAddress(neighbourName, PORT);//String must be machine name
        SctpChannel sc = null;
        MessageInfo messageInfo;
        Message msg;
        String returnMessage;
        System.out.println(threadName + ": Connecting to server");

        
        Thread.sleep(3000);

        sc = SctpChannel.open(addr, 0, 0); // Connect to server using the address
        System.out.println(threadName + ": Connected to Server");
        /*Once connected to server, send the server the round number.*/
        messageInfo = MessageInfo.createOutgoing(null, 0);
        msg = new Message(Integer.toString(this.nodeNum)+","+Integer.toString(this.currentTimestamp)+","+Integer.toString(this.numKeysToRequest));
        sc.send(msg.toByteBuffer(), messageInfo); 
        msgCount++;
        System.out.println(threadName + ": Node "+nodeNum+" is requesting CS using timestamp of "+this.currentTimestamp+". It still has "+numKeysToRequest+" keys to get. ( " + msg.message+" )");

        ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE); // Messages are received over SCTP using ByteBuffer
        sc.receive(buf, null, null);
        returnMessage = Message.fromByteBuffer(buf).message;

        Thread.sleep(3000);
        System.out.println(threadName + ": Message received from server: " + returnMessage);
        if(returnMessage.matches("My timestamp is smaller. Your request is deferred.") | returnMessage.matches("Key acquisition Timed Out. Please wait!")){
            roundSem.acquire();
            Round.msgCount += this.msgCount;
            this.msgCount = 0;
            roundSem.release();
            return;//Terminate client thread if unable to get the key.
        }

        /*From here, client is eligible to exchange keys with the server*/
        messageInfo = MessageInfo.createOutgoing(null, 0);
        msg = new Message(this.delegatedKeyToAcquire);/* Sending key it is looking for */
        sc.send(msg.toByteBuffer(), messageInfo);
        msgCount++;
        System.out.println(threadName + ": Message sent to server: " + msg.message);
        
        buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE); // Receive whether the message is at this neighbour
        sc.receive(buf, null, null);
        returnMessage = Message.fromByteBuffer(buf).message;
        
        Thread.sleep(3000);
        System.out.println(threadName + ": Message received from server: " + returnMessage);
        
        if(returnMessage.matches("The key you want is not found.")){
            return;
        }
        sem.acquire();
        Thread.sleep(1000);
        Keyclass.keyAdd(delegatedKeyToAcquire);
        sem.release();
        //If the REPLY message is received, and the key is acquired update my timestamp to the maximum of my timestamp and the server's timestamp.
        roundSem.acquire();
        Thread.sleep(1000);
        int serverTimestamp = Integer.parseInt(returnMessage.split("\\|")[0]);
        if(this.currentTimestamp < serverTimestamp){//
            if(Round.nextRound < serverTimestamp){
                Round.nextRound = serverTimestamp;
            }
        }
        System.out.println("The potential timestamp for executing the next CS for this node is updated by "+ threadName + " to " + Round.nextRound);
        System.out.println("It will be used if the it is the maximum among the current timestamp and the maximum nextRound timestamp found until now.");
        Round.msgCount += this.msgCount;
        this.msgCount = 0;
        roundSem.release();
        

   }
}
