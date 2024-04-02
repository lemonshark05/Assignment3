import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

abstract class Node implements Comparable<Node> {
    public abstract String getName();

    @Override
    public int compareTo(Node other) {
        if (this.getName() == null) return other.getName() == null ? 0 : -1;
        if (other.getName() == null) return 1;

        int result = this.getName().compareTo(other.getName());
        if (result != 0) return result;

        return this.getClass().getSimpleName().compareTo(other.getClass().getSimpleName());
    }
}

class NodeWithEdges extends Node {
    TreeSet<Node> predecessors;
    TreeSet<Node> successors;
    public NodeWithEdges() {
        this.predecessors = new TreeSet<>();
        this.successors = new TreeSet<>();
    }
    @Override
    public String getName() {
        return "NodeWithEdges";
    }
}

class Var extends NodeWithEdges {
    String name;
    Vector<Proj> projs;

    String pointsTo = null;
    String type = null;

    public Var() {

    }

    public Var(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getPointsTo() {
        return pointsTo;
    }

    public void setPointsTo(String pointsTo) {
        this.pointsTo = pointsTo;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return  name +": " + type;
    }
}

class Proj extends NodeWithEdges {
    Ctor ctor;
    int index;
    Var arg;
    @Override
    public String getName() {
        return arg.getName();
    }
}

class Ctor extends Node {
    Ctor ctor;
    String ctorName;
    Vector<Var> args;

    @Override
    public String getName() {
        return ctorName;
    }
}
