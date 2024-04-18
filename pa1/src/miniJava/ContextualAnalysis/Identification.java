package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenType;

public class Identification implements Visitor<Object,Object> {
    private ErrorReporter _errors;
    private ScopedIdentification si;
    private ClassDecl currentClass;

    private Declaration getRefDecl(Reference ref) {
        if (ref instanceof ThisRef) {
            return currentClass;
        } else if (ref instanceof QualRef) {
            return ((QualRef)ref).id.getDeclaration();
        } else if (ref instanceof IdRef) {
            return ((IdRef)ref).id.getDeclaration();
        } else {
            return null;
        }
    }

    public Identification(ErrorReporter errors, Package _package) {
        this._errors = errors;
        si = new ScopedIdentification(_errors, _package.classDeclList);
        currentClass = _package.classDeclList.get(0);
        try {
            addPredefinedClasses();
        } catch (Exception e) {
            _errors.reportError("IdentificationError: \"" + e + "\" when adding predefined declarations");
        }
    }

    private void addPredefinedClasses() {
        // Make sure we are adding correct class names? Should it only be singular or keep double like rest of convention?
        // Manually add _PrintStream, and it's method (_println)
        MethodDeclList mdl = new MethodDeclList();
        FieldDecl md = new FieldDecl(false, false, new BaseType(TypeKind.VOID, null),"println", null);
        ParameterDeclList pdl = new ParameterDeclList();
        ParameterDecl pd = new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null);
        pdl.add(pd);
        MethodDecl printStreamMethod = new MethodDecl(md, pdl, new StatementList(), null);
        mdl.add(printStreamMethod);
        ClassDecl printStream = new ClassDecl("_PrintStream", new FieldDeclList(), mdl, null);
        TypeDenoter printStreamClassType = new ClassType(new Identifier(new Token(TokenType.IDENTIFIER, "_PrintStream")), null);
        printStream.type = printStreamClassType;
        si.addClassDeclaration(printStream.name + printStream.name, printStream);
        si.addDeclaration(printStream.name + "println", printStreamMethod);

        // Manually add System and its field (out)
        FieldDeclList systemFdl = new FieldDeclList();
        FieldDecl systemField = new FieldDecl(false, true, new ClassType(new Identifier(new Token(TokenType.CLASS, "_PrintStream"), null), null), "out", null);
        systemFdl.add(systemField);
        ClassDecl system = new ClassDecl("System", systemFdl, new MethodDeclList(), null);
        TypeDenoter systemClassType = new ClassType(new Identifier(new Token(TokenType.IDENTIFIER, "System")), null);
        system.type = systemClassType;
        si.addClassDeclaration(system.name + system.name, system);
        si.addDeclaration(system.name + "out", systemField);


        // Manually add String class
        ClassDecl stringCls = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null);
        TypeDenoter stringClassType = new ClassType(new Identifier(new Token(TokenType.IDENTIFIER, "String")), null);
        system.type = stringClassType;
        si.addClassDeclaration(stringCls.name + stringCls.name, stringCls);
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
            si.addClassDeclaration(c.name+c.name, c); // add all classes to level 0
        }
//        si.openScope();
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
          currentClass = c;
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
                String tempfName = f.name;
                if (!tempfName.contains(className)) {
                    tempfName = className + tempfName;
                } else if (tempfName.contains("-")) {
                    tempfName = tempfName.replace("-", "");
                }
                si.delDeclaration(tempfName, f); // rem access to private fields
            }
        }
        for (MethodDecl m : clas.methodDeclList) {
            if (m.isPrivate) {
                String tempmName = m.name;
                if (!tempmName.contains(className)) {
                    tempmName = className + tempmName;
                } else if (tempmName.contains("-")) {
                    tempmName = tempmName.replace("-", "");
                }
                si.delDeclaration(tempmName, m); // rem access to private methods
            }
        }
        return null;
    }
    @Override
    public Object visitFieldDecl(FieldDecl f, Object arg){
        f.type.visit(this, f);
        return null;
    }
    @Override
    public Object visitMethodDecl(MethodDecl m, Object arg){
        m.type.visit(this, arg);
        ParameterDeclList pdl = m.parameterDeclList;
        si.openScope();

        for (ParameterDecl pd: pdl) {
            if (pd.type.typeKind == TypeKind.CLASS) {
                pd.type.visit(this, m);
                assert pd.type instanceof ClassType;
                si.addDeclaration(((ClassType) pd.type).className.getName() + pd.name, pd);
            } else {
                pd.visit(this, m);
            }
        }
        StatementList sl = m.statementList;
        for (Statement s: sl) {
            s.visit(this, m);
        }
        si.closeScope();
        return null;
    }
    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg){
//        assert arg instanceof ClassDecl;
//        si.addDeclaration(((ClassDecl) arg).name + pd.name, pd);
        si.addDeclaration(currentClass.name + pd.name, pd);
        pd.type.visit(this, arg);
        return null;
    }
    @Override
    public Object visitVarDecl(VarDecl vd, Object arg){
        si.addDeclaration(vd.name, vd);
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
    public ClassDecl visitClassType(ClassType ct, Object arg){
//        ct.className.visit(this, arg);
        Declaration classDecl = si.findClassDeclaration(ct.className);
        if (classDecl == null || !(classDecl instanceof ClassDecl)) {
            _errors.reportError("IdentifierError: ID \"" + ct.className.getName() + "\" requires type CLASS and cannot be found.");
        } //else {
//            id.setDeclaration(si.findDeclaration(id));
//        }
        return (ClassDecl) classDecl;
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
        stmt.initExp.visit(this, arg);
        stmt.varDecl.visit(this, arg);
        return null;
    }
    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg){
        stmt.ref.visit(this, arg);
        stmt.val.visit(this, arg);
        if(stmt.val instanceof RefExpr && ((RefExpr) stmt.val).ref instanceof IdRef && ((IdRef)((RefExpr)stmt.val).ref).id.getDeclaration() instanceof ClassDecl) {
            _errors.reportError("IdentificationError: Class Literals cannot be assigned to assignment statements");
        }
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
        if (getRefDecl(expr.ref) instanceof MethodDecl) {
            _errors.reportError("IdentificationError: refExpr: \"" + getRefDecl(expr.ref).name + "\" cannot be a Method");
        }
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
        if (!(getRefDecl(expr.functionRef) instanceof MethodDecl)) {
            _errors.reportError("IdentificationError: CallExpr: \"" + getRefDecl(expr.functionRef).name + "\" must be a Method");
        }
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
//        ref.declaration = (ClassDecl)arg;
        ref.declaration = currentClass;
        if (arg instanceof MethodDecl && ((MethodDecl)arg).isStatic) {
            _errors.reportError("IdentificationError: Cannot reference \"this\" within a static context");
        }
        return currentClass;
    }
    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.id.visit(this, currentClass);
//        if (si.findClassDeclaration(ref.id) == null) {
//            _errors.reportError("IdentifierError: No declaration made for id \"" + ref.id.getName() + "\"");
//        }
        //check for static
        ref.declaration = ref.id.getDeclaration();
        if (arg instanceof MethodDecl && ((MethodDecl)arg).isStatic) {
            if (ref.declaration instanceof MemberDecl && !((MemberDecl)ref.declaration).isStatic) {
                _errors.reportError("IdentificationError: Members of static methods must also be static");
            }
        }
        return null;
    }
    @Override
    public Object visitQRef(QualRef qr, Object arg) {
        Reference ref = qr.ref;
        ref.visit(this, arg);

        if (ref instanceof IdRef) {
            ClassDecl tempClassDecl = currentClass;
            if (((IdRef)ref).id.getDeclaration() == null) {
                _errors.reportError("IdentificationError: Base IdRef in QRef has not been declared");
                return null;
            }
            TypeDenoter tempType = ((IdRef)ref).id.getDeclaration().type;
            if (tempType instanceof ArrayType) {
                tempType = ((ArrayType) tempType).eltType;
            }
            if (!(tempType instanceof ClassType)) {
                _errors.reportError("IdentificationError: Only classes references can be qualified");
                return null;
            }
            currentClass = visitClassType((ClassType) tempType, arg);
            ref.visit(this, arg);
            currentClass = tempClassDecl;
        }
        Declaration refDecl = getRefDecl(ref);
        if (refDecl == null) {
            _errors.reportError("IdentificationError: Cannot find declaration for LHS of QRef");
            return null;
        }
        TypeDenoter LHSType = refDecl.type;
        if (LHSType.typeKind != TypeKind.CLASS) {
            if (!(ref instanceof ThisRef)) {
                _errors.reportError("IdentificationError: Left hand side of QRef must be a ClassType");
                return null;
            }
        }

        Object idDecl = null; //Object but technically will always resolve to a declaration?
        // Resolve RHS based on the context of the LHS Declaration
        if (refDecl instanceof ClassDecl || refDecl instanceof LocalDecl) {
            if (ref instanceof ThisRef) {
                idDecl = si.findlevel1Declaration(qr.id, currentClass.name);

            } else {
                //check for static vs nonstatic
                String refContext = refDecl.name.split("-")[0];
                if (refDecl.type instanceof ClassType) {
                    refContext = ((ClassType) refDecl.type).className.getName();
                }
                idDecl = si.findlevel1Declaration(qr.id, refContext);
            }
        } else if (refDecl instanceof MemberDecl) {
            Declaration LHSClassDecl = visitClassType((ClassType) LHSType, arg);
            idDecl = si.findlevel1Declaration(qr.id, LHSClassDecl.name);
        } else {
            _errors.reportError("IdentificationError: Left hand side of QRef cannot be a \"" + refDecl + "\"");
            return null;
        }

        if (idDecl == null) {
            _errors.reportError("IdentificationError: No declaration found for \"" + qr.id.getName() + "\" in QRef");
            return null;
        }

        if (!(idDecl instanceof MemberDecl)) {
            _errors.reportError("IdentificationError: RHS of QRef must be a MemberDecl but is instead \"" + idDecl + "\"");
            return null;
        }

        // Static check if LHS is ClassDecl

        if (!(ref instanceof ThisRef) && refDecl instanceof ClassDecl && idDecl instanceof MemberDecl && !((MemberDecl) idDecl).isStatic) {
            _errors.reportError("IdentificationError: Trying to reference non-static \"" + ((MemberDecl) idDecl).name + "\" in a static context");
            return null;
        }




        // Qref mark3
//        if (qr.ref instanceof QualRef) {
//            qr.ref.visit(this, arg);
//        } else if (qr.ref instanceof IdRef) {
//            // check if the left hand side of the qualified reference is a local var
//            // if the left hand side is not a local variable it HAS to be `this` or a Class???
//            if (si.findDeclaration(((IdRef) qr.ref).id, (ClassDecl) arg) == null) { // checks for local var or class type
//                _errors.reportError("IdentifierError: Qualified Reference Error 0");
//            }
//
//        } else if (qr.ref instanceof ThisRef) {
//            //DO nothing??
//        } else {
//            _errors.reportError("IdentifierError: Qualified Reference Error ");
//        }
//
//        qr.id.visit(this, arg);


        // Qref mark 2
////        assert qr.ref instanceof IdRef;
////        if (((IdRef)qr.ref).id.declaration.type != ClassType) {
////            kill yourself
////        }
////        assert (((IdRef)qr.ref).id.getDeclaration().type instanceof ClassDecl;
//
////        qr.id.visit(this, arg);
//        try {
//            if (si.findlevel1Declaration(qr.id, ((ClassType)((IdRef)qr.ref).id.getDeclaration().type).className.getName()) == null) {
//                _errors.reportError("IdentifierError: No declaration made for id \"" + qr.id.getName() + "\"");
//            }
//        } catch (Exception e) {
//            try {
//                if (si.findlevel1Declaration(qr.id, ((IdRef)qr.ref).id.getName()) == null) {
//                    _errors.reportError("IdentifierError: No declaration made for id \"" + qr.id.getName() + "\"");
//                }
//            } catch (Exception ex) {
//                if (si.findlevel1Declaration(qr.id, ((IdRef)((QualRef)qr.ref).ref).id.getName()) == null) {
//                    _errors.reportError("IdentifierError: No declaration made for id \"" + qr.id.getName() + "\"");
//                }
//            }
//        }
//        try {
//            if (si.findlevel1Declaration(qr.id, ((ClassType)((IdRef)qr.ref).id.getDeclaration().type).className.getName()) == null) {
//                _errors.reportError("IdentifierError: No declaration made for id \"" + qr.id.getName() + "\"");
//            }
//        } catch (Exception e) {
////            qr.id.visit(this, arg);
//            si.findlevel1Declaration(qr.id, "");
//        }
        qr.declaration = qr.id.getDeclaration();
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////
    //
    // TERMINALS
    //
    ///////////////////////////////////////////////////////////////////////////////
    @Override
    public Declaration visitIdentifier(Identifier id, Object arg) { // wherever visitIdentifier is called pass Class context as arg and use that in findDeclaration
        Declaration decl = si.findDeclaration(id, currentClass);
        if (decl == null) {
            decl = si.findClassDeclaration(id);
            if (decl == null) {
                _errors.reportError("IdentifierError: No declaration made for id \"" + id.getName() + "\"");
            } // else {
          //            id.setDeclaration(si.findDeclaration(id));
          //        }
        }
        return decl;
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