# ASSIGNMENT 4

**See Gradescope for the due date.**

**IMPORTANT:** Note that this assignment is due in 10 days after the previous
assignment's deadline, instead of the usual 14.

## Program Slicing

Implement a backwards program slicer. Your slicer implementation should use (1)
a reaching definitions analysis (using points-to and mod/ref information) to
compute data dependencies; and (2) a control analysis (using dominance
frontiers) to compute control dependencies. This information should be combined
to create a program dependence graph (PDG), from which a slice can be computed.

Your slicer will take as input (1) the program to analyze; (2) the starting
instruction for the slice specified as a string `<function>#<basic
block>#{<index> | term}`; and (3) a flow- and field-insensitive pointer analysis
solution for the given program. As an example of the starting instruction
format, `foo#bb1#2` specifies the third instruction of the `bb1` basic block in
function `foo`, while `foo#bb1#term` specifies the terminal of that same basic
block. The points-to solution will be given in same format as the output of the
constraint solver in assignment 3.

The output of the slicer should be the desired slice printed to standard out as
a set of basic blocks containing relevant instructions. Note that the output is
for the specific function we computed the slice in, not the entire program. See
my reference solution for exactly what the output should look like (ignoring
whitespaces, since the autograder uses `diff -Bw` to compare solutions).

## Reference Solutions

I have placed an executable of my own solution to this analysis on vlabs in
`~memre/686/slice`. In addition, I've placed an executable for computing
points-to information in `~memre/686/andersen` and an executable for the version
of reaching defs that uses pointer and mod/ref information in
`~memre/686/rdef_ptrs` (note that `slice` and `rdef_ptrs` don't take pointer
information as input, but instead compute that information themselves). You will
not be turning in a reaching defs implementation for this assignment; this is
just for you to use to help test your own implementations if you want. You can
use these reference solutions to test your analyses before submitting. If you
have any questions about the output formats, you can answer them using these
reference solutions as well; these are the solutions that Gradescope will use to
test your submissions.

## Testing

I did not include any tests because tests depend on both the program and the
slicing criterion.  For this assignment, you need to write your own tests.  You
can use the test I gave you for earlier assignments, and try different slicing
criteria.

## Designing Your Solution

You need the following parts:
- Reaching definitions analysis (you'll reuse the version from assignment 2 with
  modifications).
- Control dependency analysis (you'll reuse the version from assignment 2
  verbatim).
- Mod/ref analysis
- Call graph analysis
- PDG building
- Slicing

You also need points-to information but I will give that to your programs when I
run them.  So, you don't need to have assignment 3 finished for this assignment.

Here are the steps I recommend when building this assignment:

1. Reuse assignment 2 solution and build the PDG.
2. Build program slicing: you just need to calculate a reachability analysis on
   the PDG and print only the reachable instructions.
   
   *After you're done with these, you should pass the first two test suites.*
3. Modify reaching definitions analysis to use the points-to analysis results.
   You need to modify only the $load and $store instructions' implementations
   for this.
   
4. Build the following analyses in order:
   - Call graph analysis: you'll need points-to analysis results to resolve
     indirect calls.  This analysis has 2 steps:
     1. add edges for each call instruction to build the call graph.
     2. compute the transitive closure of the call graph.
   - Mod/ref analysis: you need to do this in two stages:
     1. Calculate mod/ref info for each function body using points-to analysis.
     2. Propagate mod/ref info along the call graph.  You just collect the info
        for all transitive callees for each function (see tablet notes).
   - Modify how you handle $call instructions in reaching definitions to use the
     results of the mod/ref analysis.
     
### Building the PDG

You need to take your assignment 2 solution, and do the following to build the graph:

- Nodes are the instructions in the given function's body.
- Edges are constructed as follows:

For each use insn1 -> insn2 reported by reaching definitions:
- add the edge insn1 -> insn2

For each control dependency bb1 -> bb2 reported by the control analysis:
- For each instruction i in bb1:
  - add the edge i -> bb2.term
  
### Slicing the PDG

1. Start from the slicing criterion, collect all instructions that are reachable.
   These are the relevant instructions.
2. Print the program, only containing the instructions that are relevant.
   See the reference solution's output.
  
### Call graph analysis
Nodes are the functions in the program.

For each function f:
  For each call instruction ... = $call g(...):
    if this is an indirect call, use pointer analysis to get callees.
    add the edge f -> g
    
Calculate transitive closure.

### Mod/ref analysis
For each function f:
  for each instruction i in f that accesses address-taken values (load/store/call):
    use pointer analysis results to get the *pointee* (address-taken values) that instruction can read/write.
    mod(f) += values that can be read from
    ref(f) += values that can be written to
    
Then, propagate this information along the call graph.  See tablet notes.
  
### Modifying Reaching Defs
- See tablet notes.

## Submitting to Gradescope

Your submission must meet the following requirements:

- There must be a `build-analyses.sh` bash script that does whatever is needed
  to build or setup the analysis (e.g., compile the code if necessary). If
  nothing is necessary the script must still exist, it can just to nothing.

- There must be a `run-slice.sh` bash script that takes four arguments: (1) a
  file containing the LIR program to analyze, (2) a file containing the JSON
  representation of that program, (3) a starting instruction for the slice, and
  (4) a file containing a flow- and field-insensitive pointer analysis
  solution. The script must print the result of the analysis to standard out.

- If your solution contains sub-directories then the entire submission must be
  given as a zip file (this is required by Gradescope for submissions that
  contain a directory structure). The scripts should all be at the root
  directory of the solution. I recommend setting up your repository once, then
  submitting via GitHub to avoid any issues with this.

- The submitted files are not allowed to contain any binaries, and your
  solutions are not allowed to use the network. Your build scripts _are_ allowed
  to use the network if they need to install anything, but be wary of how much
  time they take (build time is part of the Gradescope timeout).

## Grading

**Because this is a shorter assignment, there is no extra credit for this one.**

Here's how the grading will be broken down so that you can focus your work
accordingly:

- No pointers, calls, or loops: 0.75
- No pointers or calls, with loops: 0.75
- No calls, with pointers and loops: 0.75
- Pointers, calls, and loops: 0.75

Note that the first two categories don't require pointer or modref information;
the third category uses pointer information but no modref information, and the
final category uses both pointer and modref information.

Each of these categories will have a test suite that will be used to test your
submission on that category for the given analysis. You must get all tests in a
given test suite correct in order to receive points for the corresponding
category. You are encouraged to focus on one category at a time and get it fully
correct before moving on to the next. Remember that you can also create your own
test programs and use my solution to see what my solution for that program would
be.
