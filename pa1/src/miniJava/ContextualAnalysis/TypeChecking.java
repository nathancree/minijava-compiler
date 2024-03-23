package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

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
      c.visit(this, null);
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
      f.visit(this, null);
    for (MethodDecl m: clas.methodDeclList)
      m.visit(this, null);
    return null;
  }

  public TypeDenoter visitFieldDecl(FieldDecl f, Object o){
    f.type.visit(this, null);
    return null;
  }

  public TypeDenoter visitMethodDecl(MethodDecl m, Object o){
    m.type.visit(this, null);
    for (ParameterDecl pd: m.parameterDeclList) {
      pd.visit(this, null);
    }
    StatementList sl = m.statementList;
    for (Statement s: sl) {
      s.visit(this, null);
    }
    return null;
  }

  public TypeDenoter visitParameterDecl(ParameterDecl pd, Object o){
    pd.type.visit(this, null);
    return null;
  }

  public TypeDenoter visitVarDecl(VarDecl vd, Object o){
    vd.type.visit(this, null);
    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // TYPES
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitBaseType(BaseType type, Object o){
    return null;
  }

  public TypeDenoter visitClassType(ClassType ct, Object o){
    ct.className.visit(this, null);
    return null;
  }

  public TypeDenoter visitArrayType(ArrayType type, Object o){
    type.eltType.visit(this, null);
    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // STATEMENTS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitBlockStmt(BlockStmt stmt, Object o){
    for (Statement s: stmt.sl) {
      s.visit(this, null);
    }
    return null;
  }

  public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, Object o){
    stmt.varDecl.visit(this, null);
    stmt.initExp.visit(this, null);
    return null;
  }

  public TypeDenoter visitAssignStmt(AssignStmt stmt, Object o){
    stmt.ref.visit(this, null);
    stmt.val.visit(this, null);
    return null;
  }

  public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, Object o){
    // TODO: Thing on left should be arrayType (perhaps)
    stmt.ref.visit(this, null);
    stmt.ix.visit(this, null);
    stmt.exp.visit(this, null);
    return null;
  }

  public TypeDenoter visitCallStmt(CallStmt stmt, Object o){
    stmt.methodRef.visit(this, null);
    for (Expression e: stmt.argList) {
      e.visit(this, null);
    }
    return null;
  }

  public TypeDenoter visitReturnStmt(ReturnStmt stmt, Object o){
    if (stmt.returnExpr != null)
      stmt.returnExpr.visit(this, null);
    return null;
  }

  public TypeDenoter visitIfStmt(IfStmt stmt, Object o){
    stmt.cond.visit(this, null);
    stmt.thenStmt.visit(this, null);
    if (stmt.elseStmt != null)
      stmt.elseStmt.visit(this, null);
    return null;
  }

  public TypeDenoter visitWhileStmt(WhileStmt stmt, Object o){
    stmt.cond.visit(this, null);
    stmt.body.visit(this, null);
    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // EXPRESSIONS
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitUnaryExpr(UnaryExpr expr, Object o){
    expr.operator.visit(this, null);
    TypeDenoter tdExpr = expr.expr.visit(this, null);
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
    expr.operator.visit(this, null);
    TypeDenoter tdLeft = expr.left.visit(this, null);
    TypeDenoter tdRight = expr.right.visit(this, null);
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
    return expr.ref.visit(this, null);
//    return null;
  }

  public TypeDenoter visitIxExpr(IxExpr ie, Object o){
    // TODO: Thing on left should be arrayType
    ie.ref.visit(this, null);
    ie.ixExpr.visit(this, null);
    return null;
  }
  // TODO: Lots to do in visitCallExpr
  public TypeDenoter visitCallExpr(CallExpr expr, Object o){
    expr.functionRef.visit(this, null);
    for (Expression e: expr.argList) {
      e.visit(this, null);
    }
    return null;
  }

  public TypeDenoter visitLiteralExpr(LiteralExpr expr, Object o){
    return expr.lit.visit(this, null);
//    return null;
  }

  public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, Object o){
    expr.eltType.visit(this, null);
    expr.sizeExpr.visit(this, null);
    return null;
  }

  public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, Object o){
    return expr.classtype.visit(this, null);
//    return null;
  }


  ///////////////////////////////////////////////////////////////////////////////
  //
  // REFERENCES
  //
  ///////////////////////////////////////////////////////////////////////////////

  public TypeDenoter visitThisRef(ThisRef ref, Object o) {
    return null;
  }

  public TypeDenoter visitIdRef(IdRef ref, Object o) {
    ref.id.visit(this, null);
    return null;
  }

  public TypeDenoter visitQRef(QualRef qr, Object o) {
    TypeDenoter tdRef = qr.ref.visit(this, null); // TODO: keep workin here bud
    qr.id.visit(this, null);
    return null;
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