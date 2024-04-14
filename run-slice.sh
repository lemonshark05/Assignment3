#!/bin/bash

#
#if [ "$#" -lt 2 ]; then
#    echo "Usage: ./run-rdef.sh <LIR file> <JSON file> <function name>"
#    exit 1
#fi

LIR_FILE="$1"
JSON_FILE="$2"
SLICE_NAME="$3"
POINTER_ANALYSIS_FILE="$4"

javac DataFlowPDG.java DataFlowRdef.java DataFlowControl.java State.java ProgramPoint.java VariableState.java
java DataFlowPDG "$LIR_FILE" "$JSON_FILE" "$SLICE_NAME" "$POINTER_ANALYSIS_FILE"
