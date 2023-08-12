import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/*Validation Method: base on the output timestamp of outputs from every node, 
If there are no nodes overlap in the same duration of RTC time, then the original function has mutex.*/
public class testfile1 {

    public static boolean checkME=true;
    public static void main(String[] args) {
        /*Setup buffer reader*/
        BufferedReader br1=null;
        BufferedReader br2=null;
        String file1;
        String file2;
        String testDirectory = args[0];
        String configpath= testDirectory + "/config.txt";	
        int nodenum=readnodenum(configpath);

        for(int i=0;i<nodenum;i++) {
            for(int j=i+1;j<nodenum;j++) {
                file1 = testDirectory + "/node" + i + ".txt";
                file2 = testDirectory + "/node" + j + ".txt";
                ArrayList<String> readfile1=new ArrayList<>();
                ArrayList<String> readfile2=new ArrayList<>();
                try {
                        br1=new BufferedReader(new InputStreamReader(new FileInputStream(file1)));
                        br2=new BufferedReader(new InputStreamReader(new FileInputStream(file2)));
                }catch (FileNotFoundException e) {
                        // TODO: handle exception
                        e.printStackTrace();
                }

                String data1=null;
                try {
                        while((data1=br1.readLine())!= null) {
                                readfile1.add(data1);
                        }
                        br1.close();
                }catch (IOException e) {
                        // TODO: handle exception
                        e.printStackTrace();
                }

                String data2=null;
                try {
                        while((data2=br2.readLine())!= null) {
                                readfile2.add(data2);
                        }
                        br2.close();
                }catch (IOException e) {
                        // TODO: handle exception
                        e.printStackTrace();
                }

                checkME=checkdatalist(readfile1, readfile2);
                System.out.println("Mutex relationship between Node "+i+" and Node "+j+" is "+checkME);
                if(!checkME) {
                        break;
                }
            }
            if(!checkME) {
                    break;
            }
        }
        System.out.println("Overall correctness : "+checkME);
		
    }
	
    public static int readnodenum(String path) {
        BufferedReader bReader;
        String[] firstlineString = null;
        try {
        bReader=new BufferedReader(new InputStreamReader(new FileInputStream(path)));
        firstlineString=bReader.readLine().split("\\s+");
        bReader.close();
        } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
        } 
        return Integer.parseInt(firstlineString[0]);
    }

/*Checking across data lists across each node and report problem if found*/
    public static boolean checkdatalist(ArrayList<String> list1,ArrayList<String> list2) {
        int sizeof1=list1.size();
        int sizeof2=list2.size();
        int index1=0;
        int index2=0;
        while(true) {
            if(index1==sizeof1 || index2==sizeof2) {
                    break;
            }
            String[] list1start=list1.get(index1).split(" ");
            String[] list2start=list2.get(index2).split(" ");
            String[] list1end=list1.get(index1+1).split(" ");
            String[] list2end=list2.get(index2+1).split(" ");
            String timestart1=list1start[3];
            String timeend1=list1end[3];
            String timestart2=list2start[3];
            String timeend2=list2end[3];
            if(timeearly(timeend1, timestart2)) {
                    index1=index1+2;
                    continue;
            }

            if(timeearly(timeend2, timestart1)) {
                    index2=index2+2;
                    continue;
            }

            if(timeearly(timestart2, timestart1)&&timeearly(timestart1, timeend2)&&timeearly(timeend2, timeend1)) {
                    System.out.println("case1 "+list1.get(index1)+" "+list2.get(index2));

                    return false;
            }

            if(timeearly(timestart1, timestart2)&&timeearly(timeend2, timeend1)) {
                    System.out.println("case2 "+list1.get(index1)+" "+list2.get(index2));

                    return false;
            }

            if(timeearly(timestart2, timestart1)&&timeearly(timeend1, timeend2)) {
                    System.out.println("case3 "+list1.get(index1)+" "+list2.get(index2));

                    return false;
            }

            if(timeearly(timestart1, timestart2) &&timeearly(timestart2, timeend1) &&timeearly(timeend1, timeend2)) {
                    System.out.println("case4 "+list1.get(index1)+" "+list2.get(index2));

                    return false;
            }

        }
        return true;
    }
/*Compare two time entries*/
    public static boolean timeearly(String time1,String time2) {
        boolean trueorfalse=true;
        for(int j=0;j<time1.length();j++) {
                int first=(int)(time1.charAt(j)-'0');
                int second=(int)(time2.charAt(j)-'0');
                if(first<=second) {
                        trueorfalse=true;
                }else if(second>first){
                        trueorfalse=false;
                        return trueorfalse;
                }
        }
        return trueorfalse;

    }

}
