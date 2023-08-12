import com.sun.nio.sctp.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.io.*;
import java.util.Scanner;

class Server extends Thread{
    private InetSocketAddress addr;
    private SctpServerChannel ssc;
    private int PORT;
    private int MAX_MSG_SIZE;
    private Semaphore roundSem;//roundLock for timestamp
    private Semaphore keySem;
    private String threadName;
    private int currentTimestamp;
    private int request_to_generate;
    private int msgCount = 0;
    String projectDirectory;
    
    Server(String threadName, int PORT, int MAX_MSG_SIZE, Semaphore roundSem, Semaphore keySem, int request_to_generate, String projectDirectory){
        super(threadName);
        this.threadName = threadName;
        this.PORT = PORT;
        this.MAX_MSG_SIZE = MAX_MSG_SIZE;
        this.roundSem = roundSem;
        this.keySem = keySem;
        this.request_to_generate = request_to_generate;
        this.projectDirectory = projectDirectory;
        try{
            this.addr = new InetSocketAddress(this.PORT); // Get address from port number
            this.ssc = SctpServerChannel.open();//Open server channel
            ssc.bind(addr);//Bind server channel to address
        }catch(Exception e){
            System.out.println("There is an Exception in Server Creation.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
    
    @Override
    public void run(){
        try{
            while(true){                
                MessageInfo messageInfo;
                Message msg;

                
                SctpChannel sc = ssc.accept(); // Wait for incoming connection from client
                System.out.println(threadName + ": Client connected");
                Thread.sleep(3000);
                /*First, verify the round number sent from the client*/
                ByteBuffer buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE); 
                sc.receive(buf, null, null);//Receive the message from client
                
                String message = Message.fromByteBuffer(buf).message;
                int originNode = Integer.parseInt(message.split(",")[0]);
                int originTimestamp = Integer.parseInt(message.split(",")[1]);
                int originNumKeysToGet = Integer.parseInt(message.split(",")[2]);
                System.out.println(threadName + ": My client is node " + originNode + ". It is requesting with timestamp "+originTimestamp+" . It still has "+originNumKeysToGet+" key(s) to get. ( " + message + " )");
                
                updateTimestamp();
                
                if(this.currentTimestamp < originTimestamp){ //If this node has a smaller timestamp, it defers sending REPLY message to clent node. 
                    messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                    msg = new Message("My timestamp is smaller. Your request is deferred.");
                    sc.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                    System.out.println(threadName + ": Message sent to client: " + msg.message);
                    msgCount++;
                    updateTimestamp();
                    continue; //Go to next iternation 
                }
                if(!keySem.tryAcquire(10, TimeUnit.SECONDS)){//Cannot acquire the key in 10 seconds. Drop the client.
                    messageInfo = MessageInfo.createOutgoing(null, 0); 
                    msg = new Message("Key acquisition Timed Out. Please wait!");
                    sc.send(msg.toByteBuffer(), messageInfo); 
                    System.out.println(threadName + ": Message sent to client: " + msg.message);
                    msgCount++;
                    updateTimestamp();
                    continue; //Go to next iternation 
                }
                
                /*REPLY messages starts*/
                messageInfo = MessageInfo.createOutgoing(null, 0); 
                msg = new Message("You are eligible to exchange keys with me. Tell me your the key you want.");
                sc.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                System.out.println("Message sent to client: " + msg.message);
                msgCount++;
               
                
                buf = ByteBuffer.allocateDirect(MAX_MSG_SIZE); 
                sc.receive(buf, null, null); // Messages are received over SCTP using ByteBuffer
                message = Message.fromByteBuffer(buf).message;
                System.out.println(threadName + ": Keys client wants are: " + message);
                if(Keyclass.keyFind(message)){
                    messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                    updateTimestamp();
                    msg = new Message(this.currentTimestamp+"|Key is in!");
                    sc.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                    System.out.println(threadName + ": Message sent to client: " + msg.message);
                    Keyclass.keyRemove(message);
                    msgCount++;
                    updateTimestamp();
                }else{
                    messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
                    msg = new Message("The key you want is not found.");
                    sc.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
                    System.out.println(threadName + ": Message sent to client: " + msg.message);
                    msgCount++;
                    updateTimestamp();
                }
                keySem.release();//Release the keySem
                outputResult();
                // Loop to allow all clients to connect
            }
        }catch(Exception e){
            System.out.println("There is an Exception in Server Routine.");
            e.printStackTrace();
            System.out.println(e);
        }
            
    }
    public void updateTimestamp(){
        try{
            /*Update Timestamp*/
            System.out.println(threadName + " is waiting for a round permit.");
            roundSem.acquire();
            this.currentTimestamp = Round.currentRound;//Round is current Timestamp
            // Release the permit. 
            System.out.println(threadName + " releases the round permit.");
            Round.msgCount += this.msgCount;
            this.msgCount = 0;
            roundSem.release(); 

            System.out.println(threadName + ": Update server timestamp to " + this.currentTimestamp + " .");
        }catch(Exception e){
            System.out.println("There is an Exception when updating timestamp.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
    public void outputResult(){
        try{
            String resultFile = projectDirectory + "/results/" + threadName + ".txt";
            BufferedWriter out = new BufferedWriter(  new OutputStreamWriter(  new FileOutputStream(resultFile, false) )  );//only keep the last copy.
            String[] itemNames = {"Message Complexity", "Response Time", "System Throughput"};
            this.roundSem.acquire();
            int totalMsgCount = Round.msgCount;
            double avgResponseTime = Round.avgResponseTime;
            double throughput = Round.throughput;
            double[] itemCount = {totalMsgCount, avgResponseTime, throughput};
            this.roundSem.release();
            for (int i = 0; i < 3; i++){
                String phrase = itemNames[i] + "\t" + itemCount[i]+'\n';
                System.out.print(phrase);
                out.write(phrase);
            }
            out.close();
        }catch (Exception e){
            System.out.println("There is an Exception when outputing result.");
            e.printStackTrace();
            System.out.println(e);
        }
    }
}
