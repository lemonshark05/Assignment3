import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataFlowRdef {

//    Make addr_taken a map like Map<Type, Set<VarId>>.
    static Map<String, Set<String>> addressTakenVariables = new TreeMap<>();

    static Set<String> allAddressTakenVars = new HashSet<>();
//    all pointer-typed globals, parameters, and locals of the function being analyzed,
    static Set<String> PTRS = new HashSet<>();
    static Map<String, String> globalVars = new HashMap<>();
    static Set<String> localParams = new HashSet<>();

    static Map<String, List<String>> blockSuccessors = new HashMap<>();

    static Map<String, Set<String>> blockVars = new HashMap<>();
    static Map<String, VariableState> variableStates = new TreeMap<>();

    static Map<String, VariableState> fakeHeapStates = new TreeMap<>();

    static Map<String, Set<String>> fnParamsGlobalsTypes = new TreeMap<>();
    //Map save varName to type
    static Map<String, String> fnVarsMap = new TreeMap<>();
    static Set<String> processedBlocks = new HashSet<>();

    static Queue<String> worklist = new PriorityQueue<>();
    static Map<String, Set<String>> reachableTypesMap = new TreeMap<>();
    static Map<String, List<ProgramPoint.Instruction>> basicBlocksInstructions = new TreeMap<>();

    static Map<String, TreeSet<ProgramPoint.Instruction>> reachingDefinitions = new TreeMap<>(new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            // Split keys into blockName and Index parts
            String[] parts1 = o1.split("\\.");
            String[] parts2 = o2.split("\\.");

            String blockName1 = parts1[0];
            String blockName2 = parts2[0];

            int blockNameCompare = blockName1.compareTo(blockName2);
            if (blockNameCompare != 0) {
                return blockNameCompare;
            }

            boolean isTerm1 = parts1[1].equalsIgnoreCase("term");
            boolean isTerm2 = parts2[1].equalsIgnoreCase("term");

            if (isTerm1 && isTerm2) {
                return 0;
            } else if (isTerm1) {
                return 1;
            } else if (isTerm2) {
                return -1;
            }

            int index1 = Integer.parseInt(parts1[1]);
            int index2 = Integer.parseInt(parts2[1]);

            return Integer.compare(index1, index2);
        }
    });

    public static Map<String, TreeSet<ProgramPoint.Instruction>> reachingDefinitionAnalysis(String filePath, String functionName) {
        TreeMap<String, TreeMap<String, VariableState>> preStates = new TreeMap<>();
        parseLirFile(filePath, functionName);
        calculateReachableTypes();

        for (String blockName : blockVars.keySet()) {
            TreeMap<String, VariableState> initialStates = new TreeMap<>();
            Set<String> varsInBlock = blockVars.get(blockName);
            for (String varName : varsInBlock) {
                VariableState newState = variableStates.get(varName).clone();
                initialStates.put(varName, newState);
            }

            for(String globalVar : globalVars.keySet()){
                VariableState newState = new VariableState();
                newState.setType(globalVars.get(globalVar));
                initialStates.put(globalVar, newState);
            }

            for(String addName: allAddressTakenVars){
                VariableState newState = variableStates.get(addName).clone();
                initialStates.putIfAbsent(addName, newState);
            }
            preStates.put(blockName, initialStates);
        }

        TreeMap<String, VariableState> entryStates = preStates.get("entry");
        for (String param : localParams) {
            VariableState newState = variableStates.get(param).clone();
            entryStates.put(param, newState);
        }

        //Initial State ⊥ for all program points
        initializeVarsDefinitions(preStates);
        //Fake Heap Variables
        //Add fake heap variables to addressTakenVariables based on the analysis of pointer types (PTRSτ)

        worklist.add("entry");
        processedBlocks.add("entry");

        while (!worklist.isEmpty()) {
            String block = worklist.poll();
            if(block.equals("bb7")){
                String a = "";
            }
            TreeMap<String, VariableState> currentState = preStates.get(block);
            TreeMap<String, VariableState> initialStates = new TreeMap<>();
            for (Map.Entry<String, VariableState> entry : currentState.entrySet()) {
                String varName = entry.getKey();
                VariableState newState = currentState.get(varName).clone();
                initialStates.put(varName, newState);
            }
            initialStates = analyzeBlock(block, initialStates);

            for (String successor : blockSuccessors.getOrDefault(block, new LinkedList<>())) {
                TreeMap<String, VariableState> successorPreState = preStates.get(successor);
                TreeMap<String, VariableState> joinedState = joinMaps(successorPreState, initialStates);
                if (!joinedState.equals(successorPreState) || initialStates.isEmpty()) {
                    preStates.put(successor, joinedState);
                    if (!worklist.contains(successor)) {
                        processedBlocks.add(successor);
                        worklist.add(successor);
//                        System.out.println("Add to Worklist: " + worklist.toString());
                    }
                }
                if (!processedBlocks.contains(successor)) {
                    processedBlocks.add(successor);
                    worklist.add(successor);
                }
            }
        }
//        printAnalysisResults();
        return reachingDefinitions;
    }

    public static Map<String, List<ProgramPoint.Instruction>> getBlockInstructions(){
        return basicBlocksInstructions;
    }
    private static TreeMap<String, VariableState> analyzeBlock(String block, TreeMap<String, VariableState> preState) {
        for (ProgramPoint.Instruction operation : basicBlocksInstructions.get(block)) {
            analyzeInstruction(preState ,operation);
        }
        return preState;
    }

    private static TreeMap<String, VariableState> joinMaps(TreeMap<String, VariableState> map1, TreeMap<String, VariableState> map2) {
        TreeMap<String, VariableState> safeMap1 = (map1 != null) ? new TreeMap<>(map1) : new TreeMap<>();

        TreeMap<String, VariableState> result = new TreeMap<>(safeMap1);

        for (Map.Entry<String, VariableState> entry : map2.entrySet()) {
            String varName = entry.getKey();
            VariableState stateFromMap2 = entry.getValue();
            if (result.containsKey(varName)) {
                VariableState stateFromMap1 = result.get(varName);
                VariableState mergedState = stateFromMap1.join(stateFromMap2);
                result.put(varName, mergedState);
//                System.out.println("Merging state for variable '" + varName + "': " + stateFromMap1 + " ⊔ " + stateFromMap2 + " = " + mergedState);
            } else {
                result.put(varName, stateFromMap2);
//                System.out.println("Adding new state for variable '" + varName + "': " + stateFromMap2);
            }
        }

        return result;
    }

    static void calculateReachableTypes() {
        for(String ptype: PTRS){
            VariableState newState = new VariableState();
            newState.setType(ptype);
            ReachableTypes(ptype);
            fakeHeapStates.putIfAbsent("fake_" + ptype, newState);
        }
        boolean updated;
        do {
            updated = false;
            TreeMap<String, Set<String>> expandedMap = new TreeMap<>();

            for (Map.Entry<String, Set<String>> entry : reachableTypesMap.entrySet()) {
                String type = entry.getKey();
                Set<String> directReachableTypes = new HashSet<>(entry.getValue());

                for (String subtype : new HashSet<>(entry.getValue())) {
                    Set<String> indirectReachableTypes = reachableTypesMap.get(subtype);
                    if (indirectReachableTypes != null && !indirectReachableTypes.isEmpty()) {
                        boolean changed = directReachableTypes.addAll(indirectReachableTypes.stream().filter(s -> !s.equals(type)).collect(Collectors.toSet()));
                        if (changed) {
                            updated = true;
                        }
                    }
                }
                expandedMap.put(type, directReachableTypes);
            }

            reachableTypesMap = expandedMap;
        } while (updated);
    }

    private static void initializeVarsDefinitions(TreeMap<String, TreeMap<String, VariableState>> preStates){
        TreeMap<String, VariableState> entryStates = preStates.get("entry");
        for(String ptype: PTRS){
            //For each type τ ∈ ReachableTypes(PTRS τ ), create a fake variable
            for(String subtype: reachableTypesMap.get(ptype)){
                VariableState newState = new VariableState();
                newState.setType(subtype);
                fakeHeapStates.putIfAbsent("fake_" + subtype, newState);
            }
        }
        //alloc fake heap vars
        for (Map.Entry<String, VariableState> entry : fakeHeapStates.entrySet()) {
            String fakeVarName = entry.getKey();
            String type = entry.getValue().getType();
            entryStates.put(fakeVarName, entry.getValue());
            allAddressTakenVars.add(fakeVarName);
            addressTakenVariables.computeIfAbsent(type, k -> new HashSet<>()).add(fakeVarName);
        }
    }
    static void ReachableTypes(String type) {
        if(type.length() == 0){
            return;
        }
        if (reachableTypesMap.containsKey(type)) {
            return;
        }

        reachableTypesMap.putIfAbsent(type, new HashSet<>());

        // Base case: Simple types
        if (!type.contains("->") && !type.contains("(") && !type.startsWith("&")) {
            return;
        }
        if (type.contains("->")) {
//            String[] parts = type.split("\\s*->\\s*");
//            for (String part : parts) {
//                if (part.contains("(")) {
//                    String parameters = part.substring(part.indexOf("(") + 1, part.lastIndexOf(")"));
//                    for (String parameter : parameters.split("\\s*,\\s*")) {
//                        if(parameter.length()>0) {
//                            ReachableTypes(parameter);
//                            reachableTypesMap.get(type).add(parameter);
//                            reachableTypesMap.get(type).addAll(reachableTypesMap.getOrDefault(parameter, new HashSet<>()));
//                        }
//                    }
//                } else {
//                    ReachableTypes(part);
//                    reachableTypesMap.get(type).addAll(reachableTypesMap.getOrDefault(part, new HashSet<>()));
//                }
//            }
            return;
        }else if (type.startsWith("&")) {
            String pointedType = type.substring(1);
            ReachableTypes(pointedType);
            reachableTypesMap.get(type).add(pointedType);
            reachableTypesMap.get(type).addAll(reachableTypesMap.getOrDefault(pointedType, new HashSet<>())); // Add all reachable types from the pointed type
        }
    }


    private static void analyzeInstruction(TreeMap<String, VariableState> postState, ProgramPoint.Instruction input) {
        String instruction = input.getInstructure();
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|ret|call_dir|call_idr|jump|branch)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String defVar = parts[0];
        if(defVar.equals("_t20")|| defVar.equals("_t19")){
            String a = "";
        }
        VariableState defState = postState.get(defVar);
        if (matcher.find()) {
            String opera = matcher.group(1);
            switch (opera) {
                case "store":
                    String useVar = parts[1];
                    String valueVar = parts[2];
                    if(useVar.equals("_t41")){
                        String a = "";
                    }
                    String typeOfvalueVar = "int";
                    if(postState.get(valueVar) != null){
                        typeOfvalueVar = postState.get(valueVar).getType();
                    }
                    VariableState useState = postState.get(useVar);
                    VariableState valueState = postState.get(valueVar);
                    //∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    if (useState!= null) {
                        reachingDefinitions.get(input.toString()).addAll(useState.getDefinitionPoints());
                    }
                    if (valueState!= null) {
                        reachingDefinitions.get(input.toString()).addAll(valueState.getDefinitionPoints());
                    }

                    //  ∀x∈DEF,σ[x] ← σ[x] ∪ {pp}
                    if(typeOfvalueVar!=null && addressTakenVariables.get(typeOfvalueVar) != null) {
                        for (String addTaken : addressTakenVariables.get(typeOfvalueVar)) {
                            if (postState.containsKey(addTaken)) {
                                postState.get(addTaken).addDefinitionPoint(input);
                            }
                        }
                    }
                    break;
                case "load":
//                    x marks all address-taken variables as potentially depending on this instruction.
                    String loadVar = parts[3];
                    VariableState loadState = postState.get(loadVar);
                    if(loadState != null) {
                        reachingDefinitions.get(input.toString()).addAll(loadState.definitionPoints);
                    }
                    //∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    if(defState.getType() != null && addressTakenVariables.get(defState.getType()) != null) {
                        for (String addTaken : addressTakenVariables.get(defState.getType())) {
                            VariableState takenState = postState.get(addTaken);
                            if (takenState != null) {
                                reachingDefinitions.get(input.toString()).addAll(takenState.getDefinitionPoints());
                            }
                        }
                    }
                    // σ[x] ← {pp}
                    if(defState != null){
                        defState.setDefinitionPoint(input);
                    }
                    break;
                case "alloc":
                    String usedVar0 = parts[3];
                    VariableState usedState0 = postState.get(usedVar0);

                    //soln[pp] ← soln[pp] ∪ σ[v]
                    if (usedState0 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState0.getDefinitionPoints());
                    }
                    //σ[x] ← {pp}
                    defState.setDefinitionPoint(input);
                    break;
                case "cmp":
                    String usedVar3 = parts[4];
                    String usedVar4 = parts[5];
                    VariableState usedState3 = postState.get(usedVar3);
                    VariableState usedState4 = postState.get(usedVar4);
                    //soln[pp] ← soln[pp] ∪ σ[v]
                    if (usedState3 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState3.getDefinitionPoints());
                    }
                    if (usedState4 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState4.getDefinitionPoints());
                    }
                    //σ[x] ← {pp}
                    defState.setDefinitionPoint(input);
                    break;
                case "arith":
                    String usedVar1 = parts[4];
                    String usedVar2 = parts[5];
                    VariableState usedState1 = postState.get(usedVar1);
                    VariableState usedState2 = postState.get(usedVar2);
                    //soln[pp] ← soln[pp] ∪ σ[v]
                    if (usedState1 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState1.getDefinitionPoints());
                    }
                    if (usedState2 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState2.getDefinitionPoints());
                    }
                    //σ[x] ← {pp}
                    defState.setDefinitionPoint(input);
                    break;
                case "gep":
                    String gepVar1 = parts[3];
                    String gepVar2 = parts[4];
                    if(gepVar1.equals("id4")){
                        String a = "";
                    }
                    VariableState gepState1 = postState.get(gepVar1);
                    VariableState gepState2 = postState.get(gepVar2);
                    //soln[pp] ← soln[pp] ∪ σ[v]
                    if (gepState1 != null) {
                        reachingDefinitions.get(input.toString()).addAll(gepState1.getDefinitionPoints());
                    }
                    if (gepState2 != null) {
                        reachingDefinitions.get(input.toString()).addAll(gepState2.getDefinitionPoints());
                    }
                    //σ[x] ← {pp}
                    defState.setDefinitionPoint(input);
                    break;
                case "gfp":
                //∀v∈USE,soln[pp]←soln[pp]∪σ[v] • σ[x] ← {pp}
                    String gfpVar1 = parts[3];
                    VariableState gfpState1 = postState.get(gfpVar1);
                    if(parts[3].equals("id16")){
                        String a = "";
                    }
                    //soln[pp] ← soln[pp] ∪ σ[v]
                    if (gfpState1 != null) {
                        reachingDefinitions.get(input.toString()).addAll(gfpState1.getDefinitionPoints());
                    }
                    //σ[x] ← {pp}
                    defState.setDefinitionPoint(input);
                    break;
                case "copy":
                    if (parts.length > 3) {
                        String usedVar = parts[3];
                        VariableState usedState = postState.get(usedVar);
                        if(defVar.equals("id4")){
                            String a ="";
                        }
                        if(usedState != null) {
                            //soln[pp] ← soln[pp] ∪ σ[v]
                            Set<ProgramPoint.Instruction> set = reachingDefinitions.get(input.toString());
                            set.addAll(usedState.definitionPoints);
                        }
                        if(defState != null){
                            defState.setDefinitionPoint(input);
                            postState.put(defVar, defState);
                        }
                    }
                    break;
                case "call_ext":
                    // ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName1 = null;
                    Set<String> typeSet1 = new HashSet<>();
                    if(defVar.equals("_t37")){
                        String a = "";
                    }
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName1 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){
                                    VariableState argState = postState.get(arg);
                                    if(argState!=null) {
                                        typeSet1.addAll(reachableTypesMap.get(argState.getType()));
                                        reachingDefinitions.get(input.toString()).addAll(argState.getDefinitionPoints());
                                    }
                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.
                    for(String globalVar : globalVars.keySet()){
                        String type1 = globalVars.get(globalVar);
                        if(reachableTypesMap.get(type1)!=null) {
                            typeSet1.addAll(reachableTypesMap.get(type1));
                        }
                    }
                    if(fnParamsGlobalsTypes.size() != 0 && fnParamsGlobalsTypes.get(varFnName1)!=null) {
                        for (String type1 : fnParamsGlobalsTypes.get(varFnName1)) {
                            if(reachableTypesMap.get(type1)!=null) {
                                typeSet1.addAll(reachableTypesMap.get(type1));
                            }
                        }
                    }
                    for (String setType : typeSet1) {
                        for (String addTaken : addressTakenVariables.get(setType)) {
                            if (postState.containsKey(addTaken)) {
                                reachingDefinitions.get(input.toString()).addAll(postState.get(addTaken).getDefinitionPoints());
                            }
                        }
                    }
                    for(String globalVar : globalVars.keySet()){
                        if (postState.containsKey(globalVar)) {
                            VariableState globalState = postState.get(globalVar);
                            reachingDefinitions.get(input.toString()).addAll(globalState.getDefinitionPoints());
                            globalState.addDefinitionPoint(input);
                        }
                    }
                    for (String type : typeSet1) {
                        if(addressTakenVariables.get(type) != null) {
                            for (String addTaken : addressTakenVariables.get(type)) {
                                if (postState.containsKey(addTaken)) {
                                    postState.get(addTaken).addDefinitionPoint(input);
                                }
                            }
                        }
                    }
                    if(defState != null) {
                        defState.setDefinitionPoint(input);
                    }
                    break;
                case "call_dir":
//                    WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.
//                    ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName2 = null;
                    Set<String> typeSet2 = new HashSet<>();
                    if(defVar.equals("_t21")){
                        String a = "";
                    }
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName2 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){
                                    VariableState argState = postState.get(arg);
                                    if(argState!=null) {
                                        typeSet2.addAll(reachableTypesMap.get(argState.getType()));
                                        reachingDefinitions.get(input.toString()).addAll(argState.getDefinitionPoints());
                                    }
                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.
                    for(String globalVar : globalVars.keySet()){
                        String type2 = globalVars.get(globalVar);
                        if(reachableTypesMap.get(type2)!=null) {
                            typeSet2.addAll(reachableTypesMap.get(type2));
                        }
                    }
                    if(fnParamsGlobalsTypes.size() != 0 && fnParamsGlobalsTypes.get(varFnName2)!=null) {
                        for (String type2 : fnParamsGlobalsTypes.get(varFnName2)) {
                            if(reachableTypesMap.get(type2)!=null) {
                                typeSet2.addAll(reachableTypesMap.get(type2));
                            }
                        }
                    }
                    for (String type : typeSet2) {
                        if(addressTakenVariables.get(type) != null) {
                            for (String addTaken : addressTakenVariables.get(type)) {
                                if (postState.containsKey(addTaken)) {
                                    reachingDefinitions.get(input.toString()).addAll(postState.get(addTaken).getDefinitionPoints());
                                }
                            }
                        }
                    }
                    for(String globalVar : globalVars.keySet()){
                        if (postState.containsKey(globalVar)) {
                            VariableState globalState = postState.get(globalVar);
                            reachingDefinitions.get(input.toString()).addAll(globalState.getDefinitionPoints());
                            globalState.addDefinitionPoint(input);
                        }
                    }
                    for (String type : typeSet2) {
                        if(addressTakenVariables.get(type) != null) {
                            for (String addTaken : addressTakenVariables.get(type)) {
                                if (postState.containsKey(addTaken)) {
                                    postState.get(addTaken).addDefinitionPoint(input);
                                }
                            }
                        }
                    }
                    if(defState != null){
                        defState.setDefinitionPoint(input);
                    }
                    break;
                case "call_idr":
                    // ∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    String varFnName3 = null;
                    Set<String> typeSet3 = new HashSet<>();
                    VariableState fnState = new VariableState();
                    if(instruction.contains("(") && instruction.contains(")")){
                        Pattern patternFn = Pattern.compile("(\\w+)\\((.*?)\\)");
                        Matcher matcherFn = patternFn.matcher(instruction);

                        if (matcherFn.find()) {
                            varFnName3 = matcherFn.group(1); // Function name
                            String functionArgs = matcherFn.group(2); // All arguments
                            if(varFnName3.equals("_t30")){
                                String a ="";
                            }
                            fnState = postState.get(varFnName3);
                            if(fnState != null) {
                                String fnType = fnState.getType();
                                String[] fnparts = fnType.split("\\s*->\\s*");
                                for (String part1 : fnparts) {
                                    if (!part1.contains("(")) {
                                        typeSet3.add(part1);
                                    }
                                }
                                reachingDefinitions.get(input.toString()).addAll(fnState.getDefinitionPoints());
                            }

                            if (!functionArgs.isEmpty()) {
                                String[] args = functionArgs.split("\\s*,\\s*");
                                for(String arg : args){
                                    VariableState argState = postState.get(arg);
                                    if(argState!=null) {
                                        typeSet3.addAll(reachableTypesMap.get(argState.getType()));
                                        reachingDefinitions.get(input.toString()).addAll(argState.getDefinitionPoints());
                                    }
                                }
                            }
                        }
                    }
                    // WDEF =[{addr_taken[τ]|τ ∈ ReachViaArgs ∪ ReachViaGlobals} ∪ Globals.
                    for(String globalVar : globalVars.keySet()){
                        String type3 = globalVars.get(globalVar);
                        if(reachableTypesMap.get(type3)!=null) {
                            typeSet3.addAll(reachableTypesMap.get(type3));
                        }
                    }
                    if(fnParamsGlobalsTypes.size() != 0 && fnParamsGlobalsTypes.get(varFnName3)!=null) {
                        for (String type3 : fnParamsGlobalsTypes.get(varFnName3)) {
                            if(reachableTypesMap.get(type3)!=null) {
                                typeSet3.addAll(reachableTypesMap.get(type3));
                            }
                        }
                    }
                    for (String setType : typeSet3) {
                        for (String addTaken : addressTakenVariables.get(setType)) {
                            if (postState.containsKey(addTaken)) {
                                reachingDefinitions.get(input.toString()).addAll(postState.get(addTaken).getDefinitionPoints());
                            }
                        }
                    }
                    for(String globalVar : globalVars.keySet()){
                        if (postState.containsKey(globalVar) && !globalVar.equals(varFnName3)) {
                            VariableState globalState = postState.get(globalVar);
                            reachingDefinitions.get(input.toString()).addAll(globalState.getDefinitionPoints());
                            globalState.addDefinitionPoint(input);
                        }
                    }
                    for (String type : typeSet3) {
                        if(addressTakenVariables.get(type) != null) {
                            for (String addTaken : addressTakenVariables.get(type)) {
                                if (postState.containsKey(addTaken)) {
                                    postState.get(addTaken).addDefinitionPoint(input);
                                }
                            }
                        }
                    }
                    if(defState != null) {
                        defState.setDefinitionPoint(input);
                    }
                    if(fnState != null) {
                        fnState.addDefinitionPoint(input);
                    }
                    break;
                case "addrof":
                    if (parts.length > 2) {
                        //USE is null
                        defState.setDefinitionPoint(input);
                    }
                    break;
                case "jump":
                    String targetBlockJump = extractTargetBlock(instruction);
                    break;
                case "branch":
                    String usedVar5 = parts[1];
                    VariableState usedState5 = postState.get(usedVar5);
                    // branch maybe integer
                    //∀v∈USE,soln[pp] ← soln[pp] ∪ σ[v]
                    if(usedState5 != null) {
                        reachingDefinitions.get(input.toString()).addAll(usedState5.getDefinitionPoints());
                    }
                    break;
                case "ret":
                    if(parts.length>0) {
                        String retVar = parts[1];
                        // ret could return integer
                        VariableState retState = postState.get(retVar);
                        if(retState != null) {
                            reachingDefinitions.get(input.toString()).addAll(retState.getDefinitionPoints());
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }


    private static void parseLirFile(String filePath, String functionName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            boolean isMainFunction = false;
            boolean isOtherFunction = false;
            boolean isStruct = false;
            String structName = null;
            int index = 0;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if(line.contains("$call_ext")){
                    String a = "";
                }
                if(line.length() == 0) continue;
                if (line.startsWith("fn "+functionName)) {
                    isMainFunction = true;
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
                        fnParamsGlobalsTypes.putIfAbsent(functionName, new HashSet<>());
                        if(paramSubstring.length()>0) {
                            String[] variables = paramSubstring.split(",\\s*");
                            for (String varDeclaration : variables) {
                                String[] parts = varDeclaration.split(":");
                                String varName = parts[0].trim();
                                // just get int type
                                String type = parts[1].replace("|", ",").trim();
                                VariableState newState = new VariableState();
                                fnParamsGlobalsTypes.computeIfAbsent(functionName, k -> new HashSet<>()).add(type);
                                newState.setType(type);
                                fakeHeapStates.putIfAbsent("fake_" + type, newState);
                                if (type.startsWith("&")) {
                                    newState.setPointsTo(type.substring(1));
                                    PTRS.add(type);
                                }
                                localParams.add(varName);
                                variableStates.put(varName, newState);
                            }
                        }
                    }
                } else if (line.startsWith("fn ") && !line.contains(functionName)) {
                    isOtherFunction = true;
                    String fnName = null;
                    Pattern pattern = Pattern.compile("fn (\\w+)\\s*\\(");
                    Matcher matcher = pattern.matcher(line);
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

                        fnParamsGlobalsTypes.putIfAbsent(fnName, new HashSet<>());
                        if(paramSubstring.length()>0) {
                            String[] variables = paramSubstring.split(",\\s*");
                            for (String varDeclaration : variables) {
                                String[] parts = varDeclaration.split(":");
                                String type = parts[1].replace("|", ",").trim();
                                fnParamsGlobalsTypes.computeIfAbsent(fnName, k -> new HashSet<>()).add(type);
                                VariableState fakeState = new VariableState();
                                fakeState.setType(type);
                                PTRS.add(type);
                                fakeHeapStates.putIfAbsent("fake_" + type, fakeState);
                            }
                        }
                    }
                }else if (isOtherFunction && line.startsWith("}")) {
                    isOtherFunction = false;
                    currentBlock = null;
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
                            fnVarsMap.put(varName, type);
                        }
                    } else if (line.contains("$addrof")) {
                        String[] parts = line.split(" ");
                        if (parts.length > 3) {
                            String address = parts[0];
                            String addressTakenVar = parts[3];
                            VariableState varState = variableStates.get(address);
                            varState.setPointsTo(addressTakenVar);
                            if (fnVarsMap.containsKey(addressTakenVar)) {
                                String type = fnVarsMap.get(addressTakenVar);
                                allAddressTakenVars.add(addressTakenVar);
                                addressTakenVariables.computeIfAbsent(type, k -> new HashSet<>()).add(addressTakenVar);
                            }
                        }
                    }
                } else if (isMainFunction && line.startsWith("}")) {
                    isMainFunction = false;
                    currentBlock = null;
                } else if(line.startsWith("struct ")){
                    isStruct = true;
                    Pattern pattern = Pattern.compile("struct\\s+(\\w+)\\s*\\{");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        structName = matcher.group(1);
                    }
                    reachableTypesMap.putIfAbsent(structName, new HashSet<>());
                } else if(isStruct && line.startsWith("}")) {
                    isStruct = false;
                    structName = null;
                } else if(isStruct){
                    Pattern fieldPattern = Pattern.compile("\\s*(\\w+):\\s*(.+)");
                    Matcher fieldMatcher = fieldPattern.matcher(line);
                    if (fieldMatcher.find()) {
                        String varType = fieldMatcher.group(2);
                        reachableTypesMap.computeIfAbsent(structName, k->new HashSet<>()).add(varType);
                        ReachableTypes(varType);
                    }
                }else if (!isMainFunction && !isOtherFunction && !isStruct && line.matches("^\\w+:.*$")) {
                    Pattern pattern = Pattern.compile("^(\\w+):\\s*(.+)");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String varName = matcher.group(1);
                        String varType = matcher.group(2);
                        ReachableTypes(varType);
                        addressTakenVariables.computeIfAbsent(varType, k -> new HashSet<>()).add(varName);
                        globalVars.putIfAbsent(varName, varType);
                        reachableTypesMap.computeIfAbsent(varType, k -> new HashSet<>());
                        ReachableTypes(varType);
                        VariableState globalState = new VariableState();
                        globalState.setType(varType);
                        variableStates.put(varName, globalState);
                        if(varType.contains("&")){
                            PTRS.add(varType);
                        }
                    }
                } else if (isMainFunction) {
                    if (line.matches("^\\w+:")) {
                        //There is a new block
                        currentBlock = line.replace(":", "");
                        blockVars.putIfAbsent(currentBlock, new HashSet<>());
                        basicBlocksInstructions.putIfAbsent(currentBlock, new ArrayList<>());
                        index = 0;
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
                                VariableState newState = new VariableState();
                                newState.setType(type);
                                ReachableTypes(type);
                                if (type.startsWith("&")) {
                                    PTRS.add(type);
                                    newState.setPointsTo(type.substring(1));
                                }
                                variableStates.put(varName, newState);
                            }
                        } else if (line.contains("$addrof")) {
                            ProgramPoint.NonTermInstruction instruction = new ProgramPoint.NonTermInstruction(functionName, currentBlock, index, line);
                            index++;
                            basicBlocksInstructions.get(currentBlock).add(instruction);
                            reachingDefinitions.put(instruction.toString(), new TreeSet<>());
                            String[] parts = line.split(" ");
                            Set<String> varsInBlock = blockVars.get(currentBlock);
                            for (int i = 0; i < parts.length; i++) {
                                String part = parts[i];
                                if (variableStates.containsKey(part)) {
                                    varsInBlock.add(part);
                                }
                            }
                            if (parts.length > 3) {
                                String address = parts[0];
                                String addressTakenVar = parts[3];
                                VariableState varState = variableStates.get(address);
                                varState.setPointsTo(addressTakenVar);
                                if (variableStates.containsKey(addressTakenVar)) {
                                    String type = variableStates.get(addressTakenVar).getType();
                                    allAddressTakenVars.add(addressTakenVar);
                                    addressTakenVariables.computeIfAbsent(type, k -> new HashSet<>()).add(addressTakenVar);
                                }
                            }
                        } else {
                            ProgramPoint.Instruction instruction;
                            Set<String> varsInBlock = blockVars.get(currentBlock);
                            String[] parts = line.split(" ");
                            for (int i = 0; i < parts.length; i++) {
                                String part = parts[i];
                                if(part.contains("(") && part.contains(")")){
                                    part = part.substring(part.indexOf('(') + 1, part.indexOf(')'));
                                }
                                if (!part.contains(",") && variableStates.containsKey(part)) {
                                    varsInBlock.add(part);
                                }else if(part.contains(",")){
                                    String[] subparts = part.split("\\s*,\\s*");
                                    for(String sub : subparts){
                                        if(variableStates.containsKey(sub)) {
                                            varsInBlock.add(sub);
                                        }
                                    }
                                }
                            }
                            if(line.contains("$alloc")){
                                VariableState allocState = variableStates.get(parts[3]);
                                if(allocState == null) {
                                    VariableState fakeState = new VariableState();
                                    fakeState.setType("int");
                                    fakeHeapStates.put("fake_" + fakeState.getType(), fakeState);
                                    reachableTypesMap.computeIfAbsent(fakeState.getType(), k -> new HashSet<>());
                                    ReachableTypes(fakeState.getType());
                                    variableStates.get(parts[0]).setPointsTo("fake_" + fakeState.getType());
                                }
                            }
                            if(line.contains("store")){
                                VariableState fakeState = variableStates.get(parts[2]);
                                if(fakeState == null) {
                                    fakeState = new VariableState();
                                    fakeState.setType("int");
                                    fakeHeapStates.put("fake_" + fakeState.getType(), fakeState);
                                    reachableTypesMap.computeIfAbsent(fakeState.getType(), k -> new HashSet<>());
                                    ReachableTypes(fakeState.getType());
                                }else{
                                    fakeHeapStates.put("fake_" + fakeState.getType(), fakeState);
                                    reachableTypesMap.computeIfAbsent(fakeState.getType(), k -> new HashSet<>());
                                    ReachableTypes(fakeState.getType());
                                }
                            }
                            if (currentBlock != null) {
                                if (line.startsWith("$jump")) {
                                    instruction = new ProgramPoint.Terminal(functionName, currentBlock, line);
                                    String targetBlock = extractTargetBlock(line);
                                    blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(targetBlock);
                                } else if (line.startsWith("$branch")) {
                                    instruction = new ProgramPoint.Terminal(functionName, currentBlock, line);
                                    blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[2]); // trueBlock
                                    blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[3]); // falseBlock
                                } else if (line.contains("then")) {
                                    instruction = new ProgramPoint.Terminal(functionName, currentBlock, line);
                                    String targetBlock = line.substring(line.lastIndexOf("then") + 5).trim();
                                    blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(targetBlock);
                                } else if (line.startsWith("$ret")) {
                                    instruction = new ProgramPoint.Terminal(functionName, currentBlock, line);
                                } else {
                                    instruction = new ProgramPoint.NonTermInstruction(functionName, currentBlock, index, line);
                                    index++;
                                }
                                basicBlocksInstructions.get(currentBlock).add(instruction);
                                reachingDefinitions.put(instruction.toString(), new TreeSet<>());
                            } else {

                            }
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

    private static void printAnalysisResults() {
        // Sort the basic block names alphabetically
        for (Map.Entry<String, TreeSet<ProgramPoint.Instruction>> entry : reachingDefinitions.entrySet()) {
            String instruction = entry.getKey();
            TreeSet<ProgramPoint.Instruction> definitions = entry.getValue();

            // Skip this entry if definitions set is empty
            if (definitions.isEmpty()) {
                continue;
            }

            StringBuilder result = new StringBuilder(instruction.toString() + " -> {");

            List<String> defsStrings = definitions.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());

            result.append(String.join(", ", defsStrings));
            result.append("}");

            System.out.println(result.toString());
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java DataFlowConstants <lir_file_path> <json_file_path> <function_name>");
            System.exit(1);
        }
        String lirFilePath = args[0];
        String functionName = "test";
        if(args.length > 2 && args[2].length()!=0){
            functionName = args[2];
        }
        reachingDefinitionAnalysis(lirFilePath, functionName);
    }
}