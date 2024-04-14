import com.sun.source.tree.Tree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DataFlowPDG {

    static DataFlowControl dataFlowControl = new DataFlowControl();
    static DataFlowRdef dataFlowRdef = new DataFlowRdef();

    static Map<String, List<ProgramPoint.Instruction>> blocksInstructions = new TreeMap<>();
    private static Map<ProgramPoint.Instruction, List<ProgramPoint.Instruction>> graph = new TreeMap<>();

    //    static TreeMap
    static TreeMap<String, TreeSet<String>> ctrlDefinitions = new TreeMap<>();
    static Map<String, TreeSet<ProgramPoint.Instruction>> reachDefinitions = new TreeMap<>(new Comparator<String>() {
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

    public static void generatePDG(String filePath, String slice) {
        String functionName = slice.split("#")[0];
        reachDefinitions = dataFlowRdef.reachingDefinitionAnalysis(filePath, functionName);
        ctrlDefinitions = dataFlowControl.controlDominanceAnalysis(filePath, functionName);
        blocksInstructions = dataFlowRdef.getBlockInstructions();
        buildGraph();
        ProgramPoint.Instruction targetIns = findInstructionByCriterion(slice);
        TreeSet<ProgramPoint.Instruction> relevants = dfs(targetIns);
        printRelevants(relevants);
    }

    public static void printRelevants(TreeSet<ProgramPoint.Instruction> relevants) {
        // Use a TreeMap to automatically sort by block names (if block names have a natural order that reflects their sequence in the code)
        Map<String, List<ProgramPoint.Instruction>> blockMap = new TreeMap<>();

        for (ProgramPoint.Instruction insn : relevants) {
            blockMap.computeIfAbsent(insn.getBb(), k -> new ArrayList<>()).add(insn);
        }

        // Print each block and its instructions
        for (Map.Entry<String, List<ProgramPoint.Instruction>> entry : blockMap.entrySet()) {
            System.out.println(entry.getKey() + ":");
            for (ProgramPoint.Instruction insn : entry.getValue()) {
                System.out.println("  " + insn.getInstructure());
            }
        }
    }

    public static void addNode(ProgramPoint.Instruction instruction) {
        graph.putIfAbsent(instruction, new ArrayList<>());
    }

    public static void addEdge(ProgramPoint.Instruction from, ProgramPoint.Instruction to) {
        graph.get(from).add(to);
    }

    public static void buildGraph() {
        // Add nodes
        for (List<ProgramPoint.Instruction> block : blocksInstructions.values()) {
            for (ProgramPoint.Instruction instruction : block) {
                addNode(instruction);
            }
        }

        for (Map.Entry<String, TreeSet<ProgramPoint.Instruction>> entry : reachDefinitions.entrySet()) {
            String key = entry.getKey();
            TreeSet<ProgramPoint.Instruction> defInstructions = entry.getValue();
            if(defInstructions.size() > 0) {

                // Extract the block name from the key which includes both block name and instruction index
                String blockName = key.split("\\.")[0];
                List<ProgramPoint.Instruction> useInstructions = blocksInstructions.getOrDefault(blockName, Collections.emptyList());
                ProgramPoint.Instruction useIns = findInstruction(key);

                for (ProgramPoint.Instruction defInsn : defInstructions) {
                    addEdge(useIns, defInsn);
                }
            }
        }

        // Add control dependency edges
        for (Map.Entry<String, TreeSet<String>> ctrlEntry : ctrlDefinitions.entrySet()) {
            String fromBlock = ctrlEntry.getKey();
            TreeSet<String> toBlocks = ctrlEntry.getValue();
            List<ProgramPoint.Instruction> fromInstructions = blocksInstructions.getOrDefault(fromBlock, Collections.emptyList());
            for (ProgramPoint.Instruction fromInsn : fromInstructions) {
                for (String toBlock : toBlocks) {
                    List<ProgramPoint.Instruction> toInstructions = blocksInstructions.getOrDefault(toBlock, Collections.emptyList());
                    for (ProgramPoint.Instruction toInsn : toInstructions) {
                        if (toInsn instanceof ProgramPoint.Terminal) {
                            addEdge(fromInsn, toInsn);
                        }
                    }
                }
            }
        }
    }

    private static ProgramPoint.Instruction findInstruction(String instructName) {
        String blockName = instructName.split("\\.")[0];
        for(ProgramPoint.Instruction instruction : blocksInstructions.get(blockName)){
            if(instruction.toString().equals(instructName)){
                return instruction;
            }
        }
        return null;
    }
    public static ProgramPoint.Instruction findInstructionByCriterion(String criterion) {
        String[] parts = criterion.split("#");
        if (parts.length < 3) return null;

        String funcName = parts[0];
        String blockName = parts[1];
        String instructionIndex = parts[2];

        // Find the block and then the specific instruction
        List<ProgramPoint.Instruction> instructions = blocksInstructions.get(blockName);
        if (instructions != null) {
            for (ProgramPoint.Instruction insn : instructions) {
                if (insn.getName().equals(funcName + "." + blockName + "." + instructionIndex)) {
                    return insn;
                }
            }
        }
        return null;
    }

    public static TreeSet<ProgramPoint.Instruction> dfs(ProgramPoint.Instruction start) {
        TreeSet<ProgramPoint.Instruction> visited = new TreeSet<>();
        Stack<ProgramPoint.Instruction> stack = new Stack<>();
        stack.push(start);

        while (!stack.isEmpty()) {
            ProgramPoint.Instruction current = stack.pop();
            if (!visited.contains(current)) {
                visited.add(current);
                List<ProgramPoint.Instruction> successors = graph.getOrDefault(current, Collections.emptyList());
                for (ProgramPoint.Instruction successor : successors) {
                    if (!visited.contains(successor)) {
                        stack.push(successor);
                    }
                }
            }
        }
        return visited;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java DataFlowConstants <lir_file_path> <json_file_path> <{f.name}#{bb.id}#{i}> <>");
            System.exit(1);
        }
        String lirFilePath = args[0];
        String slice = "test";
        if(args.length > 2 && args[2].length()!=0){
            slice = args[2];
        }
        generatePDG(lirFilePath, slice);
    }
}