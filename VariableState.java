import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class VariableState implements Comparable<VariableState> {

    String varName = null;

    String funcName = null;

    String pointsTo = null;
    String type = null;

    public VariableState() {
    }

    public VariableState(String varName, String type) {
        this.varName = varName;
        this.type = type;
    }

    public VariableState(String varName, String funcName, String type) {
        this.varName = varName;
        this.funcName = funcName;
        this.type = type;
    }

    public void setPointsTo(String pointsTo) {
        this.pointsTo = pointsTo;
    }

    public String getType() {
        return this.type;
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }

    public String getFuncName() {
        return funcName;
    }

    public void setFuncName(String funcName) {
        this.funcName = funcName;
    }

    public String getPointsTo() {
        return pointsTo;
    }
    @Override
    public int compareTo(VariableState other) {
        return this.toString().compareTo(other.toString());
    }

    @Override
    public VariableState clone() {
        VariableState newState = new VariableState();
        newState.pointsTo = this.pointsTo;
        newState.type = this.type;
        return newState;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VariableState other = (VariableState) obj;
        return Objects.equals(pointsTo, other.pointsTo) &&
                type == other.type;
    }

    public VariableState join(VariableState other) {
        VariableState result = this.clone();

        return result;
    }

    public void setType(String type) {
        this.type = type;
    }



    @Override
    public String toString() {
        if(funcName != null){
            return funcName + "." + varName;
        } else if(varName !=null) {
            return varName;
        }
        return null;
    }
}

