# Mutual-Exclusion-Test
Java Server/Client Program exploring Mutex
This program was collaborated by Yu Feng and Gen Zhou.

To run this program:
1. Extract the zip in a desired location
2. Give the script files authority to run using chmod +x *.sh
3. Ensure that the test directory and the results directory has no .txt files. If there are, they could be overwritten.
4. Change the NETID and the PROJECT DIRECTORY to reflect user's environment.
5. Use javac *.java to create class files
6. Run the run_xxxx.sh according to the configuration.
7. When there are no activities spotted in each spawned terminal, execution has finished. Call the clean_xxxx.sh to close all terminal windows.

To test mutex validity:
1. Navigate to the test directory.
2. After execution of program, some files are generated in this directory automatically.
3. Use javac *.java to create class files.
4. Give the verify.sh authority to run using chmod.
5. Run verify.sh in the terminal. It will then report whether the program was mutex.

Program Main Idea:
Every node will have keys and timestamp initially generated according to their node number, to satisfy Roucairol and Carvalho's Definition.
As every node searches for keys, it will find the maximum between the current timestamp + 1 and maximum timestamp found during key acquisition, and use the maximum for the timestamp of next cs request.
If a node has a smaller timestamp, it has a higher priority and will be able to get into critical section earlier. 
Keys of a node will not be granted to another node with larger timestamp value.

Testing Mutex:
When each node enters and leaves Critical section, it will also mark the system real time and store to its file.
The verification process is done by checking whether nodes enterring critical section has a overlapping time period.
