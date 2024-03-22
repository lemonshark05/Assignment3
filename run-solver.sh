#!/bin/bash

#
#if [ "$#" -lt 2 ]; then
#    echo "Usage: ./run-solver.sh <LIR file> <JSON file> <function name>"
#    exit 1
#fi

LIR_FILE="$1"
JSON_FILE="$2"
FUNCTION_NAME="$3"

javac ConstraintSolver.java State.java ProgramPoint.java VariableState.java
java ConstraintSolver "$LIR_FILE" "$JSON_FILE" "$FUNCTION_NAME"
