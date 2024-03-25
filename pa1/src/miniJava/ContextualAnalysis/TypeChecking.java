package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

import java.lang.reflect.Type;

public class TypeChecking implements Visitor<Object, TypeDenoter> {
  private ErrorReporter _errors;

  public TypeChecking(ErrorReporter errors) {
    this._errors = errors;
  }

  public void parse(Package prog) {
    prog.visit(this, null);
  }

  private void reportTypeError(AST ast, String errMsg) {
    _errors.reportError(
            ast.posn == null ? "*** " + errMsg : "*** " + ast.posn.toString() + ": " + errMsg);
  }

  public TypeDenoter visitPackage(Package prog, Object o){
    for (ClassDecl c: prog.classDeclList){
      c.visit(this, o);
    }
    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // DECLARATIONS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitClassDecl(ClassDecl clas, Object o){
    for (FieldDecl f: clas.fieldDeclList)
      f.visit(this, clas);
    for (MethodDecl m: clas.methodDeclList)
      m.visit(this, clas);
    return null;
  }

  public TypeDenoter visitFieldDecl(FieldDecl f, Object o){
    f.type.visit(this, o);
    return null;
  }

  public TypeDenoter visitMethodDecl(MethodDecl m, Object o){
    m.type.visit(this, o);
    for (ParameterDecl pd: m.parameterDeclList) {
      pd.visit(this, o);
    }
    StatementList sl = m.statementList;
    for (Statement s: sl) {
      s.visit(this, o);
    }
    return null;
  }

  public TypeDenoter visitParameterDecl(ParameterDecl pd, Object o){
    if (pd.type.typeKind == TypeKind.VOID) {
//      if (pd.type.typeKind == TypeKind.VOID || pd.type.typeKind == TypeKind.CLASS) {
      //TODO: Make a better error sign
      _errors.reportError("TypeChecking Error: visitParameterDecl");
    }
    return pd.type.visit(this, o);
  }

  public TypeDenoter visitVarDecl(VarDecl vd, Object o){ //TODO: Need to check for void var decl??
    return vd.type.visit(this, o);
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // TYPES
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitBaseType(BaseType type, Object o){
    return type;
  }

  public TypeDenoter visitClassType(ClassType ct, Object o){
    ct.className.visit(this, o); // TODO: Don't need this line??
    return ct;
  }

  public TypeDenoter visitArrayType(ArrayType type, Object o){
    type.eltType.visit(this, o); // TODO: Check if don't need this line??
    return type;
  }

  // TODO: visit error and unsupported and void type??


  ///////////////////////////////////////////////////////////////////////////////
  //
  // STATEMENTS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitBlockStmt(BlockStmt stmt, Object o){
    for (Statement s: stmt.sl) {
      s.visit(this, o);
    }
    return null;
  }

  public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, Object o){
    TypeDenoter varTD = stmt.varDecl.visit(this, o);
    if (stmt.initExp != null) {
      TypeDenoter exprTD = stmt.initExp.visit(this, o);
      if (varTD.typeKind != exprTD.typeKind) {
        _errors.reportError("Type Checking Error: Attempting to assign \"" + exprTD.typeKind + "\" to \"" + varTD.typeKind + "\" var");
      }
    }
    return null;
  }

  public TypeDenoter visitAssignStmt(AssignStmt stmt, Object o){
    TypeDenoter refTD = stmt.ref.visit(this, o);
    TypeDenoter valTD = stmt.val.visit(this, o);
    if (refTD.typeKind != valTD.typeKind) {
      _errors.reportError("TypeChecking Error: Attempting to assign \"" + valTD.typeKind + "\" to reference of type \"" + refTD.typeKind + "\"");
    }
    return refTD;
  }

  public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, Object o){
    TypeDenoter refTD = stmt.ref.visit(this, o);
    TypeDenoter ixTD = stmt.ix.visit(this, o);
    TypeDenoter exprTD = stmt.exp.visit(this, o);
    if (refTD.typeKind != TypeKind.ARRAY) {
      _errors.reportError("TypeChecking Error: Indexed Assignment should be Array type but is instead \"" + exprTD.typeKind + "\"");
    }
    if (ixTD.typeKind != TypeKind.INT) {
      // TODO: Report error
      _errors.reportError("TypeChecking Error: visitIxAssignStmt");

    }
    assert refTD instanceof ArrayType;
    if (((ArrayType) refTD).eltType.typeKind != exprTD.typeKind) {
      //TODO: Report error
      _errors.reportError("TypeChecking Error: visitIxAssignStmt");
    }
    return refTD;
  }
  // TODO: check parameters to what is required by the method being called
  public TypeDenoter visitCallStmt(CallStmt stmt, Object o){
    if (stmt.methodRef instanceof ThisRef) {
      _errors.reportError("\"thisRef\" cannot be a method reference");
    }
    TypeDenoter td = stmt.methodRef.visit(this, o);


    // TODO: Fix Error Reporting
    Reference refExpr = stmt.methodRef;
    refExpr.visit(this, o);
    if (refExpr.declaration instanceof MethodDecl) {
      MethodDecl md = (MethodDecl) refExpr.declaration;
      ParameterDeclList paramDeclList = md.parameterDeclList;

      if (paramDeclList.size() != stmt.argList.size()) {
        _errors.reportError("TypeChecking Error: visitCallStmt");
      }
      int i = 0;
      for (Expression e: stmt.argList) {
        TypeDenoter argTD = e.visit(this, o);
        if (argTD.typeKind != paramDeclList.get(i).type.typeKind) {
          _errors.reportError("TypeChecking Error: visitCallStmt");
        }
        i++;
      }

    }
    return td;
  }

  public TypeDenoter visitReturnStmt(ReturnStmt stmt, Object o){
    if (stmt.returnExpr != null)
      return stmt.returnExpr.visit(this, o);
    return null;
  }

  public TypeDenoter visitIfStmt(IfStmt stmt, Object o){
    TypeDenoter ifCondTD = stmt.cond.visit(this, o);
    stmt.thenStmt.visit(this, o);
    if (ifCondTD == null || ifCondTD.typeKind != TypeKind.BOOLEAN) {
      //TODO: Change error to reflect actual error better
      _errors.reportError("TypeChecking Error: visistIfStmt");
    }
    if (stmt.elseStmt != null)
      stmt.elseStmt.visit(this, o);
    return null;
  }

  public TypeDenoter visitWhileStmt(WhileStmt stmt, Object o){
    TypeDenoter condTD = stmt.cond.visit(this, o);
    if (condTD == null || condTD.typeKind != TypeKind.BOOLEAN) {
      //TODO: Change error to reflect actual error better
      _errors.reportError("TypeChecking Error: visistWhileStmt");
    }
    stmt.body.visit(this, o);
    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // EXPRESSIONS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitUnaryExpr(UnaryExpr expr, Object o){
    expr.operator.visit(this, o);
    TypeDenoter tdExpr = expr.expr.visit(this, o);
    TypeDenoter td;
    if (tdExpr.typeKind == TypeKind.ERROR
            || (tdExpr.typeKind == TypeKind.INT && expr.operator.spelling.equals("-"))
            || (tdExpr.typeKind == TypeKind.BOOLEAN && expr.operator.spelling.equals("!"))) {
      return tdExpr;
//    } else if (tdExpr.typeKind == TypeKind.INT && expr.operator.spelling.equals("-")) {
//      return tdExpr;
//    } else if (tdExpr.typeKind == TypeKind.BOOLEAN && expr.operator.spelling.equals("!")) {
//      return tdExpr;
//    } else if (tdExpr.typeKind == TypeKind.NULL) { // Done need null?
//      return tdExpr;
    } else {
      _errors.reportError("TypeError: Can't compute \"" + expr.operator.spelling + tdExpr.typeKind + "\"");
      return new BaseType(TypeKind.ERROR, null);
    }
  }

  public TypeDenoter visitBinaryExpr(BinaryExpr expr, Object o){
    expr.operator.visit(this, o);
    TypeDenoter tdLeft = expr.left.visit(this, o);
    TypeDenoter tdRight = expr.right.visit(this, o);
    String operator = expr.operator.spelling;
    if (tdLeft.typeKind == TypeKind.BOOLEAN && tdRight.typeKind == TypeKind.BOOLEAN
            && (operator.equals("&&") || operator.equals("||"))) {
      return new BaseType(TypeKind.BOOLEAN, null);

    } else if (tdLeft.typeKind == TypeKind.INT && tdRight.typeKind == TypeKind.INT) {
      if (operator.equals(">") || operator.equals(">=") || operator.equals("<") || operator.equals("<=")) {
        return new BaseType(TypeKind.BOOLEAN, null);

      } else if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/")) {
        return new BaseType(TypeKind.INT, null);

      } else {
        return new BaseType(TypeKind.ERROR, null);
      }
    } else if (tdLeft.typeKind == TypeKind.CLASS && tdRight.typeKind == TypeKind.CLASS
            && (operator.equals("==") || operator.equals("!="))) {
      return new BaseType(TypeKind.BOOLEAN, null);

    } else if (tdLeft.typeKind == TypeKind.ERROR || tdRight.typeKind == TypeKind.ERROR) {
      // MARK: Don't need to report an error tho because it is already reported??
      return new BaseType(TypeKind.ERROR, null);
    } else {
      _errors.reportError("TypeError: Can't compute \"" + tdLeft.typeKind + operator + tdRight.typeKind + "\"");
      return new BaseType(TypeKind.ERROR, null);
    }
  }

  public TypeDenoter visitRefExpr(RefExpr expr, Object o){
    return expr.ref.visit(this, o);
//    return null;
  }

  public TypeDenoter visitIxExpr(IxExpr ie, Object o){
    //TODO: Change errors to reflect actual errors better
    TypeDenoter refTD = ie.ref.visit(this, o);
    if (refTD.typeKind != TypeKind.ARRAY) {
      _errors.reportError("TypeChecking Error: visitIxExpr");
    }
    TypeDenoter ixTD = ie.ixExpr.visit(this, o);
    if (refTD.typeKind != TypeKind.INT) {
      _errors.reportError("TypeChecking Error: visitIxExpr");
    }
    return refTD;
  }
  public TypeDenoter visitCallExpr(CallExpr expr, Object o) {
    // TODO: Fix Error Reporting
    Reference refExpr = expr.functionRef;
    ExprList exprList = expr.argList;
    TypeDenoter td = refExpr.visit(this, o);
    if (refExpr.declaration instanceof MethodDecl) {
      MethodDecl md = (MethodDecl) refExpr.declaration;
      ParameterDeclList paramDeclList = md.parameterDeclList;

      if (paramDeclList.size() != exprList.size()) {
        _errors.reportError("TypeChecking Error: visitCallExpr");
      }
      int i = 0;
      for (Expression e: exprList) {
        TypeDenoter argTD = e.visit(this, o);
        if (argTD.typeKind != paramDeclList.get(i).type.typeKind) {
          _errors.reportError("TypeChecking Error: visitCallExpr");
        }
        i++;
      }

    }

    return td;
  }

  public TypeDenoter visitLiteralExpr(LiteralExpr expr, Object o){
    return expr.lit.visit(this, o);
  }

  //TODO: Check below method out
  public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, Object o){
    TypeDenoter eltTD = expr.eltType.visit(this, o);
    expr.sizeExpr.visit(this, o);
    return eltTD;
  }

  public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, Object o){
    return expr.classtype.visit(this, o);
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // REFERENCES
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitThisRef(ThisRef ref, Object o) {
    // TODO: THIS SUCKS
//    assert o instanceof ClassDecl;
//    return (TypeDenoter) o;
    return null;
  }

  public TypeDenoter visitIdRef(IdRef ref, Object o) {
    return ref.id.visit(this, o);
//    return null;
  }

  public TypeDenoter visitQRef(QualRef qr, Object o) {
    TypeDenoter qualTD = qr.ref.visit(this, o);
    if (qr.ref instanceof ThisRef|| qr.ref.declaration instanceof MethodDecl) {
      _errors.reportError("TypeChecking Error: visitQRef");
    } else if (qualTD.typeKind == TypeKind.CLASS) {
      // TODO: keep workin here bud
    }
    qr.id.visit(this, o);
    return qualTD;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // TERMINALS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitIdentifier(Identifier id, Object o){
    return id.getDeclaration().type;
  }

  public TypeDenoter visitOperator(Operator op, Object o){
    return null;
  }

  public TypeDenoter visitIntLiteral(IntLiteral num, Object o){
    return new BaseType(TypeKind.INT, null);
  }

  public TypeDenoter visitBooleanLiteral(BooleanLiteral bool, Object o){
    return new BaseType(TypeKind.BOOLEAN, null);
  }

  public TypeDenoter visitNullLiteral(NullLiteral nl, Object o) {
    return new BaseType(TypeKind.NULL, null);
  }
}