package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

public class Identification implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si;

    public Identification(ErrorReporter errors, Package _package) {
        this._errors = errors;
        si = new ScopedIdentification(_errors, _package.classDeclList);
        try {
            addPredefinedClasses();
        } catch (Exception e) {
            _errors.reportError("IdentificationError: \"" + e + "\" when adding predefined declarations");
        }
    }

    private void addPredefinedClasses() {  // MARK: Make sure we are adding correct class names? Should it only be singular or keep double like rest of convention?
        // Manually add _PrintStream, and it's method (_println)
        MethodDeclList mdl = new MethodDeclList();
        FieldDecl md = new FieldDecl(false, false, new BaseType(TypeKind.VOID, null),"println", null);
        ParameterDeclList pdl = new ParameterDeclList();
        ParameterDecl pd = new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null);
        pdl.add(pd);
        MethodDecl printStreamMethod = new MethodDecl(md, pdl, new StatementList(), null);
        mdl.add(printStreamMethod);
        ClassDecl printStream = new ClassDecl("_PrintStream", new FieldDeclList(), mdl, null);
        si.addClassDeclaration(printStream.name, printStream);
        si.addDeclaration(printStream.name + "println", printStreamMethod);

        // Manually add System and its field (out)
        FieldDeclList systemFdl = new FieldDeclList();
        FieldDecl systemField = new FieldDecl(false, true, new ClassType(new Identifier(new Token(TokenType.CLASS, "_PrintStream"), null), null), "out", null);
        systemFdl.add(systemField);
        ClassDecl system = new ClassDecl("System", systemFdl, new MethodDeclList(), null);
        si.addClassDeclaration(system.name, system);
        si.addDeclaration(system.name + "out", systemField);


        // Manually add String class
        ClassDecl stringCls = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null);
        si.addClassDeclaration(stringCls.name, stringCls);
//        visitIdentifier(new Identifier(new Token(TokenType.IDENTIFIER, "String")), stringCls);

        // Visit all the classes real quick
        visitClassDecl(printStream, stringCls);
        visitClassDecl(system, system);
        visitClassDecl(stringCls, stringCls);

    }

    public void parse( Package prog ) {
        try {
            visitPackage(prog,null);
        } catch( IdentificationError e ) {
            _errors.reportError(e.toString());
        }
    }

    public Object visitPackage(Package prog, Object arg) throws IdentificationError {
        ClassDeclList cl = prog.classDeclList;
        for (ClassDecl c: prog.classDeclList) {
            si.addClassDeclaration(c.name, c); // add all classes to level 0
        }
        si.openScope();
        for (ClassDecl c : prog.classDeclList) {
            // add all public fields and methods to level 1
            for (FieldDecl fd : c.fieldDeclList) {
                if (!fd.isPrivate) {
                    si.addDeclaration(c.name + fd.name, fd);
                }
            }
            for (MethodDecl md : c.methodDeclList) {
                if (!md.isPrivate) {
                    si.addDeclaration(c.name + md.name, md);
                }
            }
        }
      // Starting actually visiting each class
      for (ClassDecl c : prog.classDeclList) {
            c.visit(this, arg);
      }

//        throw new IdentificationError("Not yet implemented!");
        return null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    //
    // DECLARATIONS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitClassDecl(ClassDecl clas, Object arg){
        String className = clas.name;
        for (FieldDecl f : clas.fieldDeclList) {
            if (f.isPrivate) {
                si.addDeclaration(className + f.name, f); // add private fields
            }
        }
        for (MethodDecl m : clas.methodDeclList) {
            if (m.isPrivate) {
                si.addDeclaration(className + m.name, m); // add private methods
            }
        }
        si.openScope(); // open level 2
        for (FieldDecl f : clas.fieldDeclList) {
            f.visit(this, clas);
        }
        for (MethodDecl m : clas.methodDeclList) {
            m.visit(this, clas);
        }
        si.closeScope(); // close level 2
        for (FieldDecl f : clas.fieldDeclList) {
            if (f.isPrivate) {
                si.delDeclaration(className + f.name, f); // rem access to private fields
            }
        }
        for (MethodDecl m : clas.methodDeclList) {
            if (m.isPrivate) {
                si.delDeclaration(className + m.name, m); // rem access to private methods
            }
        }
        return null;
    }
    @Override
    public Object visitFieldDecl(FieldDecl f, Object arg){
        f.type.visit(this, arg);
        return null;
    }
    @Override
    public Object visitMethodDecl(MethodDecl m, Object arg){
        m.type.visit(this, arg);
        ParameterDeclList pdl = m.parameterDeclList;
//        si.openScope();

        for (ParameterDecl pd: pdl) {
            if (pd.type.typeKind == TypeKind.CLASS) {
                pd.type.visit(this, arg);
                assert pd.type instanceof ClassType;
                si.addDeclaration(((ClassType) pd.type).className.getName() + pd.name, pd);
            } else {
                pd.visit(this, arg);
            }
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            s.visit(this, arg);
        }
//        si.closeScope();
        return null;
    }
    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg){
        assert arg instanceof ClassDecl;
        si.addDeclaration(((ClassDecl) arg).name + pd.name, pd);
        pd.type.visit(this, arg);
        return null;
    }
    @Override
    public Object visitVarDecl(VarDecl vd, Object arg){
        assert arg instanceof ClassDecl;
        si.addDeclaration(((ClassDecl) arg).name + vd.name, vd);
        vd.type.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TYPES
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitBaseType(BaseType type, Object arg){
        return null;
    }
    @Override
    public Object visitClassType(ClassType ct, Object arg){
//        ct.className.visit(this, arg);
        if (si.findClassDeclaration(ct.className) == null) {
            _errors.reportError("IdentifierError: ID \"" + ct.className.getName() + "\" requires type CLASS and cannot be found.");
        } //else {
//            id.setDeclaration(si.findDeclaration(id));
//        }
        return null;
    }
    @Override
    public Object visitArrayType(ArrayType type, Object arg){
        type.eltType.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // STATEMENTS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg){
        si.openScope();
        for (Statement s : stmt.sl) {
            s.visit(this, arg);
        }
        si.closeScope();
        return null;
    }
    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg){
        stmt.varDecl.visit(this, arg);
        stmt.initExp.visit(this, arg);
        return null;
    }
    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.val.visit(this, arg);
        return null;
    }
    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.ix.visit(this, arg);
        stmt.exp.visit(this, arg);
        return null;
    }
    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, arg);
        ExprList al = stmt.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }
    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg){
        if (stmt.returnExpr != null)
            stmt.returnExpr.visit(this, arg);
        return null;
    }
    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg){
        stmt.cond.visit(this, arg);
        stmt.thenStmt.visit(this, arg);
        if (stmt.elseStmt != null)
            stmt.elseStmt.visit(this, arg);
        return null;
    }
    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg){
        stmt.cond.visit(this, arg);
        stmt.body.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // EXPRESSIONS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.expr.visit(this, arg);
        return null;
    }
    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg){
        expr.operator.visit(this, arg);
        expr.left.visit(this, arg);
        expr.right.visit(this, arg);
        return null;
    }
    @Override
    public Object visitRefExpr(RefExpr expr, Object arg){
        expr.ref.visit(this, arg);
        return null;
    }
    @Override
    public Object visitIxExpr(IxExpr ie, Object arg){
        ie.ref.visit(this, arg);
        ie.ixExpr.visit(this, arg);
        return null;
    }
    @Override
    public Object visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, arg);
        ExprList al = expr.argList;
        for (Expression e: al) {
            e.visit(this, arg);
        }
        return null;
    }
    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg){
        expr.lit.visit(this, arg);
        return null;
    }
    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg){
        expr.eltType.visit(this, arg);
        expr.sizeExpr.visit(this, arg);
        return null;
    }
    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg){
        expr.classtype.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // REFERENCES
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        return null;
    }
    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.id.visit(this, arg);
        return null;
    }
    @Override
    public Object visitQRef(QualRef qr, Object arg) {
        qr.ref.visit(this, arg);
        qr.id.visit(this, arg);
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitIdentifier(Identifier id, Object arg) { // wherever visitIdentifier is called pass Class context as arg and use that in findDeclaration
        if (si.findDeclaration(id, (ClassDecl) arg) == null) {
            _errors.reportError("IdentifierError: No declaration made for id \"" + id.getName() + "\"");
        } //else {
//            id.setDeclaration(si.findDeclaration(id));
//        }
        return null;
    }
    @Override
    public Object visitOperator(Operator op, Object arg){
        return null;
    }
    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg){
        return null;
    }
    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg){
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral nl, Object arg) {
        return null;
    }
    class IdentificationError extends Error {
        private static final long serialVersionUID = -441346906191470192L;
        private String _errMsg;

        public IdentificationError(AST ast, String errMsg) {
            super();
            this._errMsg = ast.posn == null
                    ? "*** " + errMsg
                    : "*** " + ast.posn.toString() + ": " + errMsg;
        }

        @Override
        public String toString() {
            return _errMsg;
        }
    }
}