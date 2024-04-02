import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConstraintGenerator {

    //    Make addr_taken a map like Map<Type, Set<VarId>>.
    static Set<VariableState> globalVars = new TreeSet<>(new Comparator<VariableState>() {
        @Override
        public int compare(VariableState o1, VariableState o2) {
            return o1.getVarName().compareTo(o2.getVarName());
        }
    });
    static Set<VariableState> allocVars = new TreeSet<>(new Comparator<VariableState>() {
        @Override
        public int compare(VariableState o1, VariableState o2) {
            return o1.getVarName().compareTo(o2.getVarName());
        }
    });
    static Set<String> localParams = new HashSet<>();
    static Map<String, TreeSet<VariableState>> variableStates = new TreeMap<>();

    static Map<String, TreeSet<String>> allocations = new TreeMap<>();
    static Set<String> constraints = new TreeSet<>();
    static List<Function> functions = new ArrayList<>();
    public static void constraintGenerator(String filePath, String functionName) {

        parseLirFile(filePath, functionName);

        for (Function f : functions) {
            List<String> fnConstraints = genFnConstraint(f, functionName);
            for (String instruction : f.instructions) {
                String constraint = genConstraints(instruction, f.name);
                if(constraint!=null) {
                    constraints.add(constraint);
                }
            }
        }

        printAnalysisResults();
    }

    private static List<String> genFnConstraint(Function f,String functionName) {
        List<String> fnConstraints = new ArrayList<>();
        if (!functionName.equals(f.name)) {
            TreeSet<VariableState> vars = variableStates.get(f.name);
            if (vars != null) {
                for (VariableState var : vars) {
                    fnConstraints.add("lam_[" +  "](" + f.name + ",...) <= " + f.name);
                }
            }
        }
        return fnConstraints;
    }

    private static String genConstraints(String instruction, String funcName) {
        String constraints = null;
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|ret|call_dir|call_idr|jump|branch)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String defVar = parts[0];
        VariableState defState = findFuncVar(funcName, defVar);
        if(defState == null){
            defState = findGobalVar(defVar);
        }
        if (matcher.find()) {
            String opera = matcher.group(1);
            switch (opera) {
                case "store":
                    String useVar = parts[1];
                    String storeVar = parts[2];
                    VariableState storeState = findFuncVar(funcName, storeVar);
                    if(storeState == null){
                        storeState = findGobalVar(storeVar);
                    }
                    if(storeState != null && storeState.type.startsWith("&")) {
                        VariableState useState = findFuncVar(funcName, useVar);
                        if (useState == null) {
                            useState = findGobalVar(useVar);
                        }
                        constraints = generateVarString(storeState) + " <= proj(ref,1," + useState.toString() + ")";
                    }
                    break;
                case "load":
//                    x marks all address-taken variables as potentially depending on this instruction.
                    if(defState.type.startsWith("&")) {
                        String loadVar = parts[3];
                        VariableState loadState = findFuncVar(funcName, loadVar);
                        if (loadState == null) {
                            loadState = findGobalVar(loadVar);
                        }
                        constraints = "proj(ref,1," + loadState.toString() + ") <= " + generateVarString(defState);
                    }
                    break;
                case "alloc":
                    if(defState.type.startsWith("&")) {
                        String allocVar = parts[4].replace("[", "").replace("]", "");
                        VariableState allocState = new VariableState(allocVar, "&int");
                        globalVars.add(allocState);
                        constraints = "ref(" + allocVar + "," + allocVar + ") <= " + generateVarString(defState);
                    }
                    break;
                case "gep":
                    if(defState.type.startsWith("&")) {
                        String gepVar = parts[3];
                        VariableState gepState = findFuncVar(funcName, gepVar);
                        if (gepState == null) {
                            gepState = findGobalVar(gepVar);
                        }
                        constraints = generateCon(gepState, defState);
                    }
                    break;
                case "gfp":
                    if(defState.type.startsWith("&")) {
                        String gfpVar = parts[3];
                        VariableState gfpState = findFuncVar(funcName, gfpVar);
                        if (gfpState == null) {
                            gfpState = findGobalVar(gfpVar);
                        }
                        constraints = generateCon(gfpState, defState);
                    }
                    break;
                case "copy":
                    if(defState.type.startsWith("&")) {
                        String copyVar = parts[3];
                        VariableState copyState = findFuncVar(funcName, copyVar);
                        if (copyState == null) {
                            copyState = findGobalVar(copyVar);
                        }
                        constraints = generateCon(copyState, defState);
                    }
                    break;
                case "call_ext":
                    // ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName1 = null;
                    Set<String> typeSet1 = new HashSet<>();
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName1 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){

                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.

                    break;
                case "call_dir":
//                    WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.
//                    ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName2 = null;
                    Set<String> typeSet2 = new HashSet<>();
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName2 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){

                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.

                    break;
                case "call_idr":
                    // ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName3 = null;
                    Set<String> typeSet3 = new HashSet<>();
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName3 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){

                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.

                    break;
                case "addrof":
                    if(defState.type.startsWith("&")) {
                        String addrofVar = parts[3];
                        VariableState addState = findFuncVar(funcName, addrofVar);
                        if(addState == null){
                            addState = findGobalVar(addrofVar);
                        }
                        globalVars.add(addState);
                        constraints = "ref(" + addState.toString() + "," + addState.toString() + ") <= " + generateVarString(defState);
                        break;
                    }
                    break;
                default:
                    break;
            }
        }
        return constraints;
    }

    public static VariableState findFuncVar(String funcName, String varName) {
        TreeSet<VariableState> states = variableStates.get(funcName);
        if (states == null) { return null;}

        for (VariableState state : states) {
            if (state.getVarName().equals(varName)) {
                return state;
            }
        }
        return null;
    }

    public static String generateCon(VariableState left, VariableState right){
        if(left == null || right == null) {return null;}
        return generateVarString(left) + " <= " + generateVarString(right);
    }

    public static String generateVarString(VariableState var) {
        if(var == null){ return null;}
        String type = var.getType();
//        if(type.startsWith("&&")){
//            return "proj(ref,1," + var.toString() + ")";
//        }else {
            return var.toString();
//        }
    }
    public static VariableState findGobalVar(String varName) {
        if (globalVars.size() == 0) { return null;}

        for (VariableState state : globalVars) {
            if (state.getVarName().equals(varName)) {
                return state;
            }
        }
        return null;
    }

    private static void printAnalysisResults() {
        for (String result : constraints) {
            System.out.println(result);
        }
    }

    private static void parseLirFile(String filePath, String functionName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            boolean isMainFunction = false;
            boolean isOtherFunction = false;
            boolean isStruct = false;
            String structName = null;
            Function currentFunction = null;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if(line.length() == 0) continue;
                if (line.startsWith("fn "+functionName)) {
                    isMainFunction = true;
                    String returnType = line.split("->")[1].trim();
                    if(line.contains("->") && line.contains("(")) {
                        String paramSubstring = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
                        StringBuilder transformedPart = new StringBuilder();
                        int parenthesisLevel = 0;
                        for (char c : paramSubstring.toCharArray()) {
                            if (c == '(') {
                                parenthesisLevel++;
                            }else if (c == ')'){
                                parenthesisLevel--;
                            } else if (c == ',' && parenthesisLevel > 0){
                                c = '|';
                            }
                            transformedPart.append(c);
                        }
                        List<String> parameters = new ArrayList<>();
                        if(paramSubstring.length()>0) {
                            String[] variables = paramSubstring.split(",\\s*");
                            for (String varDeclaration : variables) {
                                String[] parts = varDeclaration.split(":");
                                String varName = parts[0].trim();
                                // just get int type
                                String type = parts[1].replace("|", ",").trim();
                                VariableState newState = new VariableState(varName, functionName ,type);
                                variableStates.computeIfAbsent(functionName, k -> new TreeSet<>()).add(newState);
                                if (type.startsWith("&")) {
                                    newState.setPointsTo(type.substring(1));
                                }
                                localParams.add(varName);
                                parameters.add(varName);
                            }
                        }
                        // Create a new function object
                        currentFunction = new Function();
                        currentFunction.name = functionName;
                        currentFunction.vars = new ArrayList<>();
                        currentFunction.type = returnType;
                        currentFunction.parameters = parameters;
                        currentFunction.instructions = new ArrayList<>();
                    }
                } else if (line.startsWith("fn ") && !line.contains(functionName)) {
                    isOtherFunction = true;
                    String fnName = null;
                    Pattern pattern = Pattern.compile("fn (\\w+)\\s*\\(");
                    Matcher matcher = pattern.matcher(line);
                    String returnType = line.split("->")[1].trim();
                    List<String> parameters = new ArrayList<>();
                    if (matcher.find()) {
                        fnName = matcher.group(1);
                    }
                    if(line.contains("->") && line.contains("(")) {
                        String paramSubstring = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
                        StringBuilder transformedPart = new StringBuilder();
                        int parenthesisLevel = 0;
                        for (char c : paramSubstring.toCharArray()) {
                            if (c == '(') {
                                parenthesisLevel++;
                            }else if (c == ')'){
                                parenthesisLevel--;
                            } else if (c == ',' && parenthesisLevel > 0){
                                c = '|';
                            }
                            transformedPart.append(c);
                        }
                        if(paramSubstring.length()>0) {
                            String[] variables = paramSubstring.split(",\\s*");
                            for (String varDeclaration : variables) {
                                String[] parts = varDeclaration.split(":");
                                String type = parts[1].replace("|", ",").trim();
                                VariableState newState = new VariableState(parts[0], fnName ,type);
                                variableStates.computeIfAbsent(fnName, k -> new TreeSet<>()).add(newState);
                                VariableState fakeState = new VariableState();
                                fakeState.setType(type);
                                parameters.add(parts[0]);
                            }
                        }
                    }
                    // Create a new function object
                    currentFunction = new Function();
                    currentFunction.name = fnName;
                    currentFunction.vars = new ArrayList<>();
                    currentFunction.type = returnType;
                    currentFunction.parameters = parameters;
                    currentFunction.instructions = new ArrayList<>();
                }else if (isOtherFunction && line.startsWith("}")) {
                    isOtherFunction = false;
                    currentBlock = null;
                    functions.add(currentFunction);
                    currentFunction = null;
                }else if(isOtherFunction){
                    if (line.startsWith("let ")) {
                        String variablesPart = line.substring("let ".length());
                        StringBuilder transformedPart = new StringBuilder();
                        int parenthesisLevel = 0;
                        for (char c : variablesPart.toCharArray()) {
                            if (c == '(') {
                                parenthesisLevel++;
                            } else if (c == ')') {
                                parenthesisLevel--;
                            } else if (c == ',' && parenthesisLevel > 0) {
                                c = '|';
                            }
                            transformedPart.append(c);
                        }
                        String[] variables = transformedPart.toString().split(",\\s*");
                        for (String varDeclaration : variables) {
                            String[] parts = varDeclaration.split(":");
                            String varName = parts[0].trim();
                            String type = parts[1].replace("|", ",").trim();
                            currentFunction.vars.add(varName);
                            VariableState newState = new VariableState(varName ,currentFunction.name, type);
                            variableStates.computeIfAbsent(currentFunction.name, k -> new TreeSet<>()).add(newState);
                        }
                    } else if (line.matches("^\\w+:")) {
                        //There is a new block

                    }else{
                        currentFunction.instructions.add(line);
                    }
                } else if (isMainFunction && line.startsWith("}")) {
                    isMainFunction = false;
                    currentBlock = null;
                    functions.add(currentFunction);
                    currentFunction = null;
                } else if(line.startsWith("struct ")){
                    isStruct = true;
                    Pattern pattern = Pattern.compile("struct\\s+(\\w+)\\s*\\{");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        structName = matcher.group(1);
                    }
                } else if(isStruct && line.startsWith("}")) {
                    isStruct = false;
                    structName = null;
                } else if(isStruct){
                    Pattern fieldPattern = Pattern.compile("\\s*(\\w+):\\s*(.+)");
                    Matcher fieldMatcher = fieldPattern.matcher(line);
                    if (fieldMatcher.find()) {
                        String varType = fieldMatcher.group(2);
                    }
                }else if (!isMainFunction && !isOtherFunction && !isStruct && line.matches("^\\w+:.*$")) {
                    Pattern pattern = Pattern.compile("^(\\w+):\\s*(.+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String varName = matcher.group(1);
                        String varType = matcher.group(2);
                        globalVars.add(new VariableState(varName, varType));
                        VariableState globalState = new VariableState(varName, varType);
                        variableStates.computeIfAbsent("global", k -> new TreeSet<>()).add(globalState);
                    }
                } else if (isMainFunction) {
                    if (line.matches("^\\w+:")) {
                        //There is a new block
                        currentBlock = line.replace(":", "");
                    } else {
                        if (line.startsWith("let ")) {
                            String variablesPart = line.substring("let ".length());
                            StringBuilder transformedPart = new StringBuilder();
                            int parenthesisLevel = 0;
                            for (char c : variablesPart.toCharArray()) {
                                if (c == '(') {
                                    parenthesisLevel++;
                                } else if (c == ')') {
                                    parenthesisLevel--;
                                } else if (c == ',' && parenthesisLevel > 0) {
                                    c = '|';
                                }
                                transformedPart.append(c);
                            }
                            String[] variables = transformedPart.toString().split(",\\s*");
                            for (String varDeclaration : variables) {
                                String[] parts = varDeclaration.split(":");
                                String varName = parts[0].trim();
                                // just get int type
                                String type = parts[1].replace("|", ",").trim();
                                VariableState newState = new VariableState(varName, currentFunction.name, type);
                                if (type.startsWith("&")) {
                                    newState.setPointsTo(type.substring(1));
                                }
                                variableStates.computeIfAbsent(currentFunction.name, k -> new TreeSet<>()).add(newState);
                            }
                        } else {
                            currentFunction.instructions.add(line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String extractTargetBlock(String instruction) {
        Pattern pattern = Pattern.compile("\\$(branch|jump)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(instruction);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java DataFlowConstants <lir_file_path> <json_file_path> <function_name>");
            System.exit(1);
        }
        String lirFilePath = args[0];
        String functionName = "test";
        if (args.length > 2 && args[2].length() != 0) {
            functionName = args[2];
        }
        constraintGenerator(lirFilePath, functionName);
    }
}