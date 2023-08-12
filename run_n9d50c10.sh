#!/bin/bash

# Your netid
NETID=yxf160330
# name of the directory when the project is located
PROJECT_DIRECTORY="$HOME/CS6378/P2"
# name of the configuration file
CONFIG_FILE="$PROJECT_DIRECTORY/n9d50c10.txt"
# name of the program to be run
PROGRAM_FILE="Node"

# initialize iteration variable
i=0
# copy the config file curently using for later testing
cp $CONFIG_FILE "$PROJECT_DIRECTORY/test/config.txt"
# read the configuration file
# replace any phrase starting with "#" with an empty string and then delete any empty lines
cat "$CONFIG_FILE" | sed -e "s/#.*//" | sed "/^$/d" |
{	
	# read the number of nodes
	read firstline
	m=$( echo $firstline | awk '{ print $1 }' )
	#m=`echo $m | sed -e 's/ //g'`
	echo "Config has" $m "nodes"


	# read the location of each node one by one
	while [ $i -lt $m ];
	do
		# read a line
		read line
		# echo $line
		# extract the node identifier
		IDENTIFIER=$( echo $line | awk '{ print $1 }' )
		# extract the machine name
		HOST=$( echo $line | awk '{ print $2 }' )
		echo "spawning node" $IDENTIFIER on "machine" $HOST
		# construct the string specifying the program to be run by the ssh command
		ARGUMENTS="java -classpath \"$PROJECT_DIRECTORY\" $PROGRAM_FILE $IDENTIFIER \"$HOST\" \"$CONFIG_FILE\" \"$PROJECT_DIRECTORY\""
		# spawn the node
		# any error message will be stored in the log files
		xterm -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $NETID@$HOST '$ARGUMENTS' 2> log.launcher.$IDENTIFIER" &
		i=$((i+1)) 
	done
}
