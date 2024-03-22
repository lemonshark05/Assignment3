# ASSIGNMENT 3

See Gradescope for the due date.

This assignment is about implementing an Andersen-style pointer analysis. I've
broken the assignment into two independent pieces so that you can get full marks
for one even if you have problems with the other. If you put them together then
you get the full Andersen-style pointer analysis.

## PART 1: Andersen-Style Pointer Analysis Constraint Generator

Implement constraint generation for a field-insensitive Andersen-style pointer
analysis. The generator will take a LIR program as input and output a list of
constraints in the format described below.

Each line of the output will be a constraint of the form `<exp> <= <exp>`, where
`<exp>` can be:

- `<var>` for set variables, in the form of:
    - `<variable name>` (for globals and `$alloc` identifiers)
    - `<function name>.<variable name>` (for function parameters and locals)

- `ref(<var>,<var>)` for `ref` calls (we'll use the program variable name as
  both the constant and set variable arguments)

- `proj(ref,1,<var>)` for projections (we'll only ever be projecting on `ref`
  constructor calls, and always on position 1)

- `lam_[<type>](<arg>,...)` for `lam` calls (where `<type>` is the type of the
  function)

The constraints should be listed in alphabetical order. For example, the input:

```
foo: &(&int,int,&int)->&int

fn foo(p1:&int, p2:int, p3:&int) -> &int {
let r:&int
entry:
    r = $copy p3
    $ret r
}

fn main() -> int {
let a:&int, b:&int, c:&int, d:&&int, e:&int, f:&(&int,int,&int)->&int, g:int
entry:
    a = $alloc 1 [_alloc1]
    b = $alloc 1 [_alloc2]
    f = $copy foo
    c = $call_idr f(a, 42, b) then exit

exit:
    d = $alloc 1 [_alloc3]
    $store d a
    e = $load d
    g = $load e
    $ret g
}
```

Will output the following:

```
foo <= main.f
foo.p3 <= foo.r
lam_[(&int,int,&int)->&int](foo,foo.r,foo.p1,foo.p3) <= foo
main.a <= proj(ref,1,main.d)
main.f <= lam_[(&int,int,&int)->&int](_DUMMY,main.c,main.a,main.b)
proj(ref,1,main.d) <= main.e
ref(_alloc1,_alloc1) <= main.a
ref(_alloc2,_alloc2) <= main.b
ref(_alloc3,_alloc3) <= main.d
```

## PART 2: Andersen-Style Pointer Analysis Constraint Solver

Implement a constraint solver for a field-insensitive Andersen-style pointer
analysis. The solver will take a file containing a list of constraints in the
same format as the output of Part 1. It should output a solution mapping program
variables to their points-to sets (where all variable are listed in alphabetical
order).

Note: all lam constructor calls have the first argument as covariant; whether
the second argument is covariant or contravariant depends on whether the
function returns a pointer or not. You can easily determine this information
based on the type information given as part of the lam name (`lam_[<type>]`): if
the type contains `->&` then the function returns a pointer, in which case the
second argument is covariant, otherwise it is contravariant. All other arguments
are always contravariant.

For example, the input:

```
foo <= main.f
foo.p3 <= foo.r
lam_[(&int,int,&int)->&int](foo,foo.r,foo.p1,foo.p3) <= foo
main.a <= proj(ref,1,main.d)
main.f <= lam_[(&int,int,&int)->&int](_DUMMY,main.c,main.a,main.b)
proj(ref,1,main.d) <= main.e
ref(_alloc1,_alloc1) <= main.a
ref(_alloc2,_alloc2) <= main.b
ref(_alloc3,_alloc3) <= main.d
```

Will output the following:

```
_alloc3 -> {_alloc1}
foo -> {foo}
foo.p1 -> {_alloc1}
foo.p3 -> {_alloc2}
foo.r -> {_alloc2}
main.a -> {_alloc1}
main.b -> {_alloc2}
main.c -> {_alloc2}
main.d -> {_alloc3}
main.e -> {_alloc1}
main.f -> {foo}
```

## REFERENCE SOLUTIONS

I have placed executables of my own solutions to these analyses on vlabs in
`~memre/686/{constraint_gen, constraint_solve}`. You can use these reference
solutions to test your analyses before submitting. If you have any questions
about the output formats, you can answer them using these reference solutions as
well; these are the solutions that Gradescope will use to test your submissions.

**IMPORTANT NOTE:** My `constraint-solve` solution assumes for its input that
`<exp>` does not contain any whitespaces (which is true for the examples above
and for what is output by my `constraint-generator` solution). Your solutions
don't need to make this assumption or enforce it (the autograder will work
regardless), but:

  - You can rely on this assumption for the inputs to your solution when tested
    by the autograder.

  - If you want to test something using my `constraint-solver` solution it will
    only work if this assumption is true.
	
## BUNDLED TESTS

This zip file has a small number of tests for each category to get you started.
After passing the tests for a category, you should write your own tests for that
category and/or submit your assignment to Gradescope to get feedback/tests from
the autograder.

## STRUCTURING YOUR CODE

Because of the assignment structure, you don't need to link the solver and the
generator *for this assignment*, but the results of this analysis are going to
be used in further assignments.  For now, you can maintain two separate programs
to implement the two parts.

### Constraint generator

For the generator, you need to implement the following loop:

```
constraints = ∅
for f in program.functions:
  constraints += genFnConstraint(f)  // this adds the lam constructor
  for bb in f.blocks:
    for i in bb.instructions:
	  constraints += genConstraints(i)
	  
constraints.map(_.toString).sort().for_each(print)
```

`genFnConstraint(f)` generates a constraint like `lam_[type](...) <= f` for the
function definition (see lecture notes).

`genConstraints(i)` generates the constraints for an instruction (a normal or
terminal instruction).  See the lecture notes for this too.

#### Things to be aware of

- `main` is not callable from other functions, so you shouldn't generate a `lam`
  constructor for for `main`.
- the constraints in the output should be ordered lexicographically.
- you can fetch the type for `lam` from the global variable associated with the
  function.
  
### Constraint solver

You need to implement the following stages:

1. Constraint parser: you need to parse constraints in the format in this
   assignment description.  This can be a simple loop like the following:
   ```
   for line in nonempty lines in input:
     let lhs, rhs = line.split("<=")
     add_edge(parse_expr(lhs), parse_expr(rhs)
   ```
   
   You can implement `parse_expr` as a simple recursive-descent parser.  If you
   don't know how to implement this, come to my office hours.
   
   When reading `lam_[...]`, you can just match the regex `lam\[.*?\]` and put
   that as the name of the constructor.
   
2. Graph building: You need to implement an `add_edge(lhs, rhs)` function that
   takes a constraint `lhs ⊆ rhs`, and inserts the appropriate edge(s) to the
   graph.  If both sides are matching constructors, `add_edge` needs to
   recursively add edges because we don't store constructor-constructor edges in
   the graph.  I recommend returning modified nodes from `add_edge` so that the
   worklist algorithm can add those nodes to the worklist.
   
3. Constraint solving: We are computing a fixpoint (transitive closure with
   projections), so a worklist algorithm is pretty natural.  Here is a sketch:
   
   ```
   worklist = <all nodes with predecessors>
   while let Some(n) = worklist.pop():
     propagate predecessors to successors
	 - for each added edge, add the modified node to the worklist.
	 
	 if n is a variable node:
	   for p in projections from n:
	     propagate predecessors of p to successors of p
		 - for each added edge, add the modified node to the worklist.
   ```

4. Printing the output.  You just need to project out the first (0th) argument
   of `ref` for each variable:
   
   ```
   for x in var node in the graph in lexicographical ordering:
     if pred(x) contains a ref:
       ptsto = {c.args[0] for c in x.pred if c is a ref constructor}
	   print("$x -> $ptsto")
   ```
   
#### Variance

When handling constructor-constructor constraints in `add_edge`, you need to
take variance into account.  For example, if `f/2` is covariant on the 0th
argument and contravariant on the 1st argument, then `add_edge(f(X, Y), f(A,
B))` should recursively call `add_edge(X, A)` and `add_edge(B, Y)`.
   
#### Representing the constraint graph

Recall that:
- constructor nodes never keep track of edges
- projection nodes know their predecessors

Basically, you want the following hierarchy:

```
class Node;
class NodeWithEdges extends Node {
  predecessors: Set<Node>
  successors: Set<Node>
}
class Var extends NodeWithEdges {
  name: String
  projs: Vec<Proj>
}
class Proj extends NodeWithEdges {
  ctor: Ctor
  index: Int
  arg: Var
}
class Ctor extends Node {
  ctor: Ctor
  args: Vec<Var>
}
```

You can store some of this data (edges, projections) in hashmaps if you want.

## SUBMITTING TO GRADESCOPE

Your submission must meet the following requirements:

- There must be a `build-analyses.sh` bash script that does whatever is needed
  to build or setup both analyses (e.g., compile the code if necessary). If
  nothing is necessary the script must still exist, it can just to nothing.

- There must be `run-solver.sh` and `run-generator.sh` bash scripts;
  `run-solver.sh` should take one argument: a file containing a list of
  constraints; `run-generator.sh` should take two arguments: a file containing
  the LIR program to generate constraints for and a file containing the JSON
  representation of that program. Each script must print the result of the
  respective analysis to standard out.

- If your solution contains sub-directories then the entire submission must be
  given as a zip file (this is required by Gradescope for submissions that
  contain a directory structure). The scripts should all be at the root
  directory of the solution. I recommend submitting via GitHub as this takes
  care of the issue of uploading a zip file.

- The submitted files are not allowed to contain any binaries, and your
  solutions are not allowed to use the network.  Your build scripts _are_
  allowed to use the network if they need to install anything, but be wary of
  how much time they take (build time is part of the Gradescope timeout).

If you want to submit one analysis before you've implemented the other that's
fine, but you still need all the scripts I mentioned (otherwise the grader will
barf). The script for the missing analysis can just do nothing.

## GRADING

Here's how the grading will be broken down so that you can focus your work accordingly:

PART 1:

- Programs with no calls: 1.0

- Programs with direct calls: 0.1

- Programs with direct and indirect calls: 0.2

PART 2:

- No non-ref constructor calls and no projections: 0.1

- No non-ref constructor calls: 1.0

- With lam constructor calls: 0.2

Each of these categories will have a test suite that will be used to test your
submission on that category for the given analysis. You must get all tests in a
given test suite correct in order to receive points for the corresponding
category.  You are encouraged to focus on one category at a time and get it
fully correct before moving on to the next.  Remember that you can also create
your own test programs and use my solution on vlabs to see what my solution for
that program would be.

### Timeliness extra credit

If you get 2/3 test categories passed for either part by April 1, you will get
0.5 points of extra credit.
