package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;

import java.util.HashMap;
import java.util.Map;

public class IDTable {
    private Map<String, Declaration> table;

    public IDTable() {
        this.table = new HashMap<>();
    }

    public void addDeclaration(String identifier, Declaration declaration) throws Exception { //throws Identification.IdentificationError {
        if (table.containsKey(identifier) ){ //&& declaration.getClass() == table.get(identifier).getClass()) {
//            throw new Identification.IdentificationError();
            throw new Exception();
        }
        table.put(identifier, declaration);
    }
    public void delDeclaration(String identifier, Declaration declaration) throws Exception { //throws Identification.IdentificationError {
        if (!table.containsKey(identifier)) {
//            throw new Identification.IdentificationError();
            throw new Exception();
        }
        table.remove(identifier, declaration);
    }

    public Declaration findDeclaration(String identifier) {
        return table.get(identifier);
    }
}
