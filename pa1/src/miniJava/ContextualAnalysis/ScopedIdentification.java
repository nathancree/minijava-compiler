package miniJava.ContextualAnalysis;

import java.util.ArrayDeque;
import java.util.Deque;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;

public class ScopedIdentification {
    private Deque<IDTable> stack;
    private int level;
    private ErrorReporter _errors;

    public ScopedIdentification(ErrorReporter errors) {
        this._errors = errors;
        this.stack = new ArrayDeque<>();
        this.stack.push(new IDTable()); // Level 0
        this.level = 1; // Might not actually need this
        this.stack.push(new IDTable()); // Level 1
    }

    public void openScope() {
        stack.push(new IDTable());
        level++;
    }

    public void closeScope() {
        stack.pop();
        level--;
    }

    public void addDeclaration(String identifier, Declaration declaration) {
        try {
            stack.peek().addDeclaration(identifier, declaration);
            check2PlusLevel(identifier);
        } catch (Exception e) { // TODO: Change Exception to IdentificationError
            _errors.reportError("IdentificationError: Identifier already exists at level: " + level);
        }
    }

    private void check2PlusLevel(String identifier) throws Exception{
        if (level > 2) {
            Deque<IDTable> temp = new ArrayDeque<>();
            temp.push(stack.pop());
            while (level > 1) {
                level--;
                if (stack.peek().findDeclaration(identifier) != null) {
                    throw new Exception();
                }
                temp.push(stack.pop());
            }
            for (IDTable idt : temp) {
                stack.push(temp.pop());
            }
        }
    }
    public void addClassDeclaration(String identifier, Declaration declaration) {
        try {
            stack.peekLast().addDeclaration(identifier, declaration);
        } catch (Exception e) {
            _errors.reportError("IdentificationError: Identifier already exists at level: " + level);
        }
    }

    public Declaration findDeclaration(Identifier identifier) {
        for (IDTable idTable : stack) {
            Declaration declaration = idTable.findDeclaration(identifier.getClass().getName() + identifier.getName());
//            if (declaration != null) {
//                identifier.declaration = declaration;
//                return declaration;
//            }
            return declaration;
        }
        return null;
    }
    public void delDeclaration(String identifier, Declaration declaration) {
        try {
            stack.peek().delDeclaration(identifier, declaration);
        } catch (Exception e) {
            _errors.reportError("IdentificationError: Identifier already exists at level: " + level);
        }
    }
}
