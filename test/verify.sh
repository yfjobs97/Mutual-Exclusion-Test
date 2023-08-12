#!/bin/bash


TEST_DIRECTORY="$HOME/CS6378/P2/test"
PROGRAM_FILE="testfile1"


java -classpath $TEST_DIRECTORY $PROGRAM_FILE $TEST_DIRECTORY 2> log.test