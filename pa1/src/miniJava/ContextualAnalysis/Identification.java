package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

public class Identification implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si;

    public Identification(ErrorReporter errors) {
        this._errors = errors;
        si = new ScopedIdentification(_errors);
        try {
//            si.addDeclaration("System", new ClassDecl());
        } catch (Exception e) {
            _errors.reportError("Error \"" + e + "\" when adding predefined declarations");
        }
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
            si.addDeclaration(c.name + c.name, c); // add classes to level 0
        }
        si.openScope(); // add level 1
        for (ClassDecl c: prog.classDeclList){
            for (FieldDecl fd : c.fieldDeclList) {
                // TODO: add all public
            }
            for (MethodDecl md : c.methodDeclList) {
                // TODO: add all public
            }
            c.visit(this, "");
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
            f.visit(this, "");
        }
        for (MethodDecl m : clas.methodDeclList) {
            m.visit(this, "");
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
        f.type.visit(this, "");
        return null;
    }
    @Override
    public Object visitMethodDecl(MethodDecl m, Object arg){
        m.type.visit(this, "");
        ParameterDeclList pdl = m.parameterDeclList;
        for (ParameterDecl pd: pdl) {
            pd.visit(this, "");
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            s.visit(this, "");
        }
        return null;
    }
    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg){
        si.addDeclaration(pd.getClass().getName() + pd.name, pd);
        pd.type.visit(this, "");
        return null;
    }
    @Override
    public Object visitVarDecl(VarDecl vd, Object arg){
        si.addDeclaration(vd.getClass().getName() + vd.name, vd);
        vd.type.visit(this, "");
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
        ct.className.visit(this, "");
        return null;
    }
    @Override
    public Object visitArrayType(ArrayType type, Object arg){
        type.eltType.visit(this, "");
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
            s.visit(this, "");
        }
        si.closeScope();
        return null;
    }
    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg){
        stmt.varDecl.visit(this, "");
        stmt.initExp.visit(this, "");
        return null;
    }
    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        stmt.ref.visit(this, "");
        stmt.val.visit(this, "");
        return null;
    }
    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg){
        stmt.ref.visit(this, "");
        stmt.ix.visit(this, "");
        stmt.exp.visit(this, "");
        return null;
    }
    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg){
        stmt.methodRef.visit(this, "");
        ExprList al = stmt.argList;
        for (Expression e: al) {
            e.visit(this, "");
        }
        return null;
    }
    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg){
        if (stmt.returnExpr != null)
            stmt.returnExpr.visit(this, "");
        return null;
    }
    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg){
        stmt.cond.visit(this, "");
        // TODO: Check if need these scopes because block statement takes care of is?
//        si.openScope(); // level 2+?
        stmt.thenStmt.visit(this, "");
//        si.closeScope();
        if (stmt.elseStmt != null)
//            si.openScope();
            stmt.elseStmt.visit(this, "");
//            si.closeScope();
        return null;
    }
    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg){
        stmt.cond.visit(this, "");
        stmt.body.visit(this, "");
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // EXPRESSIONS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg){
        expr.operator.visit(this, "");
        expr.expr.visit(this, "");
        return null;
    }
    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg){
        expr.operator.visit(this, "");
        expr.left.visit(this, "");
        expr.right.visit(this, "");
        return null;
    }
    @Override
    public Object visitRefExpr(RefExpr expr, Object arg){
        expr.ref.visit(this, "");
        return null;
    }
    @Override
    public Object visitIxExpr(IxExpr ie, Object arg){
        ie.ref.visit(this, "");
        ie.ixExpr.visit(this, "");
        return null;
    }
    @Override
    public Object visitCallExpr(CallExpr expr, Object arg){
        expr.functionRef.visit(this, "");
        ExprList al = expr.argList;
        for (Expression e: al) {
            e.visit(this, "");
        }
        return null;
    }
    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg){
        expr.lit.visit(this, "");
        return null;
    }
    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg){
        expr.eltType.visit(this, "");
        expr.sizeExpr.visit(this, "");
        return null;
    }
    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg){
        expr.classtype.visit(this, "");
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
        ref.id.visit(this, "");
        return null;
    }
    @Override
    public Object visitQRef(QualRef qr, Object arg) {

        qr.id.visit(this, "");
        qr.ref.visit(this, "");
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Object visitIdentifier(Identifier id, Object arg){
        if (si.findDeclaration(id) == null) {
            // TODO: throw iderror
        } else {
            id.setDeclaration(si.findDeclaration(id));
        }
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