package miniJava.ContextualAnalysis;

import java.util.ArrayDeque;
import java.util.Deque;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.ErrorReporter;

public class ScopedIdentification {
    private Deque<IDTable> stack;
    private int level;
    private ErrorReporter _errors;
    private ClassDeclList classList;

    public ScopedIdentification(ErrorReporter errors, ClassDeclList classList) {
        this._errors = errors;
        this.classList = classList;
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
            _errors.reportError("IdentificationError: Identifier \"" + identifier + "\" already exists at level: " + level);
        }
    }

    private void check2PlusLevel(String identifier) throws Exception{
        if (level >= 2) {
            Deque<IDTable> temp = new ArrayDeque<>();
            temp.push(stack.pop());
            level--;
            while (level > 1) {
                level--;
                if (stack.peek().findDeclaration(identifier) != null) {
                    level ++;
                    throw new Exception();
                }
                temp.push(stack.pop());
            }
            for (IDTable idt : temp) {
                stack.push(temp.pop());
                level++;
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
    public Declaration findClassDeclaration(Identifier identifier) {
        Declaration declaration = stack.peekLast().findDeclaration(identifier.getName()+identifier.getName());
        if (declaration != null) {
            identifier.setDeclaration(declaration);
            return declaration;
        }
        return null;
    }
    public Declaration findlevel1Declaration(Identifier identifier, String className) {
        ArrayDeque<IDTable> tempStack = new ArrayDeque<>(stack);
        tempStack.pollLast();

        Declaration declaration = tempStack.peekLast().findDeclaration(className + identifier.getName());

        if (declaration != null) {
            identifier.setDeclaration(declaration);
            return declaration;
        }
        return null;
    }

//    public Declaration findDeclaration(Identifier identifier, ClassDecl clas) {
////        Declaration declaration = stack.peekLast().findDeclaration(identifier.getName());
//        Declaration declaration = findClassDeclaration(identifier);
//
//        //first check if identifier is a class
//        if (declaration != null && level < 2) {
//            identifier.setDeclaration(declaration);
//            return declaration;
//        }
//        for (IDTable idTable : stack) {
//
//            declaration = idTable.findDeclaration(clas.name + identifier.getName());
//            if (declaration != null) {
//                declaration.name = clas.name + "-" + identifier.getName();
//                identifier.setDeclaration(declaration);
//                return declaration;
//            } else if (identifier.getName().equals("String")) {
//                declaration = idTable.findDeclaration("StringString");
//                if (declaration != null) {
//                    identifier.setDeclaration(declaration);
//                    return declaration;
//                }
//            } else {
//                declaration = idTable.findDeclaration(clas.name + "-" + identifier.getName());
//                if(declaration != null) {
//                    return declaration;
//                }
//            }
//        }
//        return null;
//    }

    public Declaration findDeclaration(Identifier identifier, ClassDecl clas) {
        Declaration declaration;
        declaration = findClassDeclaration(identifier);

        //first check if identifier is a class
        if (declaration != null && level < 2) {
            identifier.setDeclaration(declaration);
            return declaration;
        }
        for (IDTable idTable : stack) {
            declaration = idTable.findDeclaration(identifier.getName());
            if (declaration != null) {
                declaration.name = clas.name + "-" + identifier.getName();
                identifier.setDeclaration(declaration);
                return declaration;
            }
            declaration = idTable.findDeclaration(clas.name + identifier.getName());
            if (declaration != null) {
                declaration.name = clas.name + "-" + identifier.getName();
                identifier.setDeclaration(declaration);
                return declaration;
            } else if (identifier.getName().equals("String")) {
                declaration = idTable.findDeclaration("StringString");
                if (declaration != null) {
                    identifier.setDeclaration(declaration);
                    return declaration;
                }
            } else {
                declaration = idTable.findDeclaration(clas.name + "-" + identifier.getName());
                if(declaration != null) {
                    return declaration;
                }
            }
        }
//        declaration = findClassDeclaration(identifier);
//
//        //first check if identifier is a class
//        if (declaration != null && level < 2) {
//            identifier.setDeclaration(declaration);
//            return declaration;
//        }
        return null;
    }

    public void delDeclaration(String identifier, Declaration declaration) {
        try {
            stack.peek().delDeclaration(identifier, declaration);
        } catch (Exception e) {
            _errors.reportError("IdentificationError: Identifier \"" + identifier + "\" doesn't exist at level: " + level);
        }
    }
}
