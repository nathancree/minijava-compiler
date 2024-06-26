package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.*;

public class Parser {
  private Scanner _scanner;
  private ErrorReporter _errors;
  private Token _currentToken;

  public Parser(Scanner scanner, ErrorReporter errors) {
    this._scanner = scanner;
    this._errors = errors;
    this._currentToken = this._scanner.scan();
  }

  public Package parse() {
    try {
      // The first thing we need to parse is the Program symbol
      return parseProgram();
    } catch (SyntaxError e) {
    }
    return null;
  }

  // Program ::= (ClassDeclaration)* eot
  private Package parseProgram() throws SyntaxError {
    // TODO: Keep parsing class declarations until eot
    ClassDeclList cdl = new ClassDeclList();
    while (_currentToken.getTokenType() != TokenType.EOT) {
      cdl.add(parseClassDeclaration());
    }
    return new Package(cdl,null);
  }

  // ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
  private ClassDecl parseClassDeclaration() throws SyntaxError {
    // TODO: Take in a "class" token (check by the TokenType)
    //  What should be done if the first token isn't "class"?
    FieldDeclList fdl = new FieldDeclList();
    MethodDeclList mdl = new MethodDeclList();
    accept(TokenType.CLASS);

    // TODO: Take in an identifier token
    String cn = _currentToken.getTokenText();
    Identifier classId = new Identifier(_currentToken);
    accept(TokenType.IDENTIFIER);
    // TODO: Take in a {
    accept(TokenType.LCURLY);
    // TODO: Parse either a FieldDeclaration or MethodDeclaration
    while (_currentToken.getTokenType() != TokenType.RCURLY) {
      boolean isPrivate = parseVisibility();
      boolean isStatic = parseAccess();
      if (_currentToken.getTokenType() == TokenType.VOID) {
        mdl.add(parseMethodDeclaration(null, isPrivate, isStatic));
      } else {
        TypeDenoter t = parseType();
        FieldDecl fd = new FieldDecl(isPrivate, isStatic, t, _currentToken.getTokenText(), null);
        accept(TokenType.IDENTIFIER);
        if (_currentToken.getTokenType() == TokenType.SEMICOLON) {
          fdl.add(fd);
          accept(TokenType.SEMICOLON);
        } else {
          mdl.add(parseMethodDeclaration(fd, isPrivate, isStatic));
        }
      }
    }
    // TODO: Take in a }
    accept(TokenType.RCURLY);
    ClassDecl cl = new ClassDecl(cn, fdl, mdl, null);
    cl.type = new ClassType(classId, null);
    return cl;
  }
  private FieldDecl parseFieldDeclaration() throws SyntaxError {
    parseType();
    accept(TokenType.IDENTIFIER);
    accept(TokenType.SEMICOLON);
    return null;
  }
  private MethodDecl parseMethodDeclaration(FieldDecl fieldDecl, boolean isPrivate, boolean isStatic) throws SyntaxError {
    FieldDecl fd = fieldDecl;
    ParameterDeclList paraml = new ParameterDeclList();
    StatementList statel = new StatementList();
    if (_currentToken.getTokenType() == TokenType.VOID) {
      accept(TokenType.VOID);
      fd = new FieldDecl(isPrivate, isStatic, new BaseType(TypeKind.VOID, null), _currentToken.getTokenText(), null);
      accept(TokenType.IDENTIFIER);
    }
    accept(TokenType.LPAREN);
    if (_currentToken.getTokenType() != TokenType.RPAREN) {
      paraml = parseParameterList();
    }
    accept(TokenType.RPAREN);
    accept(TokenType.LCURLY);
    while (_currentToken.getTokenType() != TokenType.RCURLY) {
      statel.add(parseStatement());
    }
    accept(TokenType.RCURLY);
    return new MethodDecl(fd, paraml, statel, null); //TODO: WARNING NO RIGHT DOESNT WORK
  }

  private boolean parseVisibility() throws SyntaxError { //returns true if visibility is private
    if (_currentToken.getTokenType() == TokenType.PUBLIC) {
      _currentToken = _scanner.scan();
    } else if (_currentToken.getTokenType() == TokenType.PRIVATE) {
      _currentToken = _scanner.scan();
      return true;
    }
    return false;
  }

  private boolean parseAccess() throws SyntaxError { // returns true if static
    if (_currentToken.getTokenType() == TokenType.STATIC) {
      _currentToken = _scanner.scan();
      return true;
    }
    return false;
  }
  private TypeDenoter parseType() throws SyntaxError {
    TypeKind typeKind;
    if (_currentToken.getTokenType() == TokenType.BOOLEAN) {
      typeKind = TypeKind.BOOLEAN;
      accept(TokenType.BOOLEAN);
    } else if (_currentToken.getTokenType() == TokenType.INT) {
      typeKind = TypeKind.INT;
      accept(TokenType.INT);
      if (_currentToken.getTokenType() == TokenType.BRACKETS) {
        accept(TokenType.BRACKETS);
        return new ArrayType(new BaseType(typeKind, null), null);
      }
    } else if (_currentToken.getTokenType() == TokenType.IDENTIFIER){
      Identifier cn = new Identifier(_currentToken);
      accept(TokenType.IDENTIFIER);
      if (_currentToken.getTokenType() == TokenType.BRACKETS) {
        typeKind = TypeKind.ARRAY;
        accept(TokenType.BRACKETS);
        return new ArrayType(new ClassType(cn, null), null);
      }
      if (_currentToken.getTokenType() == TokenType.PERIOD) {
        return null;
      } else {
        return new ClassType(cn, null);
      }
    } else {
      typeKind = TypeKind.UNSUPPORTED;
    }
    return new BaseType(typeKind, null);
  }

  private ParameterDeclList parseParameterList() throws SyntaxError {
    ParameterDeclList paraml = new ParameterDeclList();
    TypeDenoter td0 = parseType();
    paraml.add(new ParameterDecl(td0, _currentToken.getTokenText(), null));
    accept(TokenType.IDENTIFIER);

    while (_currentToken.getTokenType() == TokenType.COMMA) {
      accept(TokenType.COMMA);
      TypeDenoter td1 = parseType();
      paraml.add(new ParameterDecl(td1, _currentToken.getTokenText(), null));
      accept(TokenType.IDENTIFIER);
    }
    return paraml;
  }

  private ExprList parseArgumentList() throws SyntaxError {
    ExprList exprl = new ExprList();
    exprl.add(parseExpression());
    while (_currentToken.getTokenType() == TokenType.COMMA) {
      accept(TokenType.COMMA);
      exprl.add(parseExpression());
    }
    return exprl;
  }

  private Reference parseReference() throws SyntaxError { //check method return type
    Reference ref;
    if (_currentToken.getTokenType() == TokenType.IDENTIFIER) {
      Identifier id = new Identifier(_currentToken);
      ref = new IdRef(id, null);
      accept(TokenType.IDENTIFIER);
    } else if (_currentToken.getTokenType() == TokenType.THIS){
      ref = new ThisRef(null);
      accept(TokenType.THIS);
    } else {
      ref = null;
    }
    while (_currentToken.getTokenType() == TokenType.PERIOD) {
      accept(TokenType.PERIOD);
      ref = new QualRef(ref, new Identifier(_currentToken), null);
      accept(TokenType.IDENTIFIER);
    }
    return ref;
  }
  //TODO:
  private Statement parseStatement() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.LCURLY) {
      StatementList statel = new StatementList();
      accept(TokenType.LCURLY);
      while (_currentToken.getTokenType() != TokenType.RCURLY) {
        statel.add(parseStatement());
      }
      accept(TokenType.RCURLY);
      return new BlockStmt(statel, null);
    } else if (_currentToken.getTokenType() == TokenType.INT
        || _currentToken.getTokenType() == TokenType.BOOLEAN) {
      TypeDenoter t = parseType();
      String name = _currentToken.getTokenText();
      accept(TokenType.IDENTIFIER);
      accept(TokenType.EQUALS);
      Expression expr = parseExpression();
      accept(TokenType.SEMICOLON);
      return new VarDeclStmt(new VarDecl(t, name, null), expr, null);
    } else if (_currentToken.getTokenType() == TokenType.IDENTIFIER || _currentToken.getTokenType() == TokenType.THIS) { // could be Reference or Type
      String id0 = _currentToken.getTokenText();
      Identifier id = new Identifier(_currentToken);
//      accept(TokenType.IDENTIFIER);
      Reference ref;
      TypeDenoter t0 = null;
//      ref = new IdRef(id, null);
//      t0 = parseType();
//      if (_currentToken.getTokenType() == TokenType.THIS) {
//        ref = parseReference();
//      } else {
//        ref = new IdRef(id, null);
//        t0 = parseType();
//      }
      ref = parseReference();
      if (ref instanceof IdRef) {
        t0 = new ClassType(id, null);
        if (_currentToken.getTokenType() == TokenType.BRACKETS){
          t0 = new ArrayType(t0, null);
        }
      }
      if (_currentToken.getTokenType() == TokenType.BRACKETS) { //id[]
        accept(TokenType.BRACKETS);
        String name = _currentToken.getTokenText();
//        accept(TokenType.IDENTIFIER);
        TypeDenoter t = parseType();
        accept(TokenType.EQUALS);
        Expression expr = parseExpression();
        accept(TokenType.SEMICOLON);
        return new VarDeclStmt(new VarDecl(t0, name, null), expr, null);
      } else if (_currentToken.getTokenType() == TokenType.IDENTIFIER) {
//        accept(TokenType.IDENTIFIER);
        String name = _currentToken.getTokenText();
        TypeDenoter t = parseType();
        accept(TokenType.EQUALS);
        Expression expr = parseExpression();
        accept(TokenType.SEMICOLON);
        return new VarDeclStmt(new VarDecl(t0, name, null), expr, null);
      } else if (_currentToken.getTokenType() == TokenType.PERIOD) {
//        accept(TokenType.PERIOD);
//        parseReference();
        return parseStatement();
      } else if (_currentToken.getTokenType() == TokenType.EQUALS) {
        accept(TokenType.EQUALS);
        Expression expr = parseExpression();
        accept(TokenType.SEMICOLON);
        return new AssignStmt(ref, expr, null);
      } else if (_currentToken.getTokenType() == TokenType.LBRACK) {
        accept(TokenType.LBRACK);
        Expression expr0 = parseExpression();
        accept(TokenType.RBRACK);
        accept(TokenType.EQUALS);
        Expression expr1 = parseExpression();
        accept(TokenType.SEMICOLON);
        return new IxAssignStmt(ref, expr0, expr1, null);
      } else if (_currentToken.getTokenType() == TokenType.LPAREN) {
        ExprList exprl = new ExprList();
        accept(TokenType.LPAREN);
        if (_currentToken.getTokenType() != TokenType.RPAREN) {
          exprl = parseArgumentList();
        }
        accept(TokenType.RPAREN);
        accept(TokenType.SEMICOLON);
        return new CallStmt(ref, exprl, null);
      } else {
        _errors.reportError("Expected a Statement, but got \"" + _currentToken.getTokenText() + "\"");
        throw new SyntaxError();
      }
    } else if (_currentToken.getTokenType() == TokenType.RETURN) {
      Expression expr = null;
      accept(TokenType.RETURN);
      if (_currentToken.getTokenType() != TokenType.SEMICOLON) {
        expr = parseExpression();
      }
      accept(TokenType.SEMICOLON);
      return new ReturnStmt(expr, null);
    } else if (_currentToken.getTokenType() == TokenType.IF) {
      accept(TokenType.IF);
      accept(TokenType.LPAREN);
      Expression expr = parseExpression();
      accept(TokenType.RPAREN);
      if (_currentToken.getTokenType() != TokenType.LCURLY) {
//        _errors.reportError("Can't have single line if statements");
      }
      Statement stmt0 = parseStatement();
      Statement stmt1 = null;
      if (_currentToken.getTokenType() == TokenType.ELSE) {
        accept(TokenType.ELSE);
//        if (_currentToken.getTokenType() != TokenType.LCURLY) {
//          _errors.reportError("Can't have single line else statements");
//        }
        stmt1 = parseStatement();
      }
      return new IfStmt(expr, stmt0, stmt1, null);
    } else if (_currentToken.getTokenType() == TokenType.WHILE) {
      accept(TokenType.WHILE);
      accept(TokenType.LPAREN);
      Expression expr = parseExpression();
      accept(TokenType.RPAREN);
//      if (_currentToken.getTokenType() != TokenType.LCURLY) {
//        _errors.reportError("Can't have single line while statements");
//      }
      Statement stmt = parseStatement();
      return new WhileStmt(expr, stmt, null);
    } else {
      _errors.reportError("Expected a Statement, but got \"" + _currentToken.getTokenText() + "\"");
      throw new SyntaxError();
    }
  }
  private Expression parseExpression() throws SyntaxError {
    Expression finalExpr = null;
    if (_currentToken.getTokenType() == TokenType.IDENTIFIER
        || _currentToken.getTokenType() == TokenType.THIS) {
      Reference ref = parseReference();
      if (_currentToken.getTokenType() == TokenType.LBRACK) {
        accept(TokenType.LBRACK);
        Expression expr = parseExpression();
        accept(TokenType.RBRACK);
//        return new IxExpr(ref, expr, null);
        finalExpr = new IxExpr(ref, expr, null);
      } else if (_currentToken.getTokenType() == TokenType.LPAREN) {
        ExprList exprl = new ExprList();
        accept(TokenType.LPAREN);
        if (_currentToken.getTokenType() != TokenType.RPAREN) {
          exprl = parseArgumentList();
        }
        accept(TokenType.RPAREN);
//        return new CallExpr(ref, exprl, null);
        finalExpr = new CallExpr(ref, exprl, null);

      } else if (_currentToken.getTokenType() == TokenType.SEMICOLON || _currentToken.getTokenType() == TokenType.RPAREN || _currentToken.getTokenType() == TokenType.RBRACK || _currentToken.getTokenType() == TokenType.COMMA) {
//        return new RefExpr(ref, null);
        finalExpr = new RefExpr(ref, null);

      } else if (_currentToken.getTokenType() == TokenType.OPERATOR && !_currentToken.getTokenText().equals("!")) {
        Operator op = new Operator(_currentToken);
        accept(TokenType.OPERATOR);
        Expression expr = parseExpression();
//        return new BinaryExpr(op, new RefExpr(ref, null), expr, null);
        if (expr instanceof BinaryExpr) {
          finalExpr = new BinaryExpr(((BinaryExpr) expr).operator, new BinaryExpr(op, new RefExpr(ref, null), ((BinaryExpr) expr).left, null), ((BinaryExpr) expr).right, null);
        } else {
          finalExpr = new BinaryExpr(op, new RefExpr(ref, null), expr, null);
        }
//        finalExpr = new BinaryExpr(op, new RefExpr(ref, null), expr, null);

      }
    } else if (_currentToken.getTokenText().equals("!")
        || _currentToken.getTokenText().equals("-")) { // TODO: warning need to check - for unop or binop?
      Operator op = new Operator(_currentToken);
      _currentToken = _scanner.scan();
      Expression expr = parseExpression();
//      return new UnaryExpr(op, expr, null);
      if (expr instanceof BinaryExpr) {
        finalExpr = new BinaryExpr(((BinaryExpr) expr).operator, new UnaryExpr(op, ((BinaryExpr) expr).left, null), ((BinaryExpr) expr).right, null);
      } else {
        finalExpr = new UnaryExpr(op, expr, null);
      }
    } else if (_currentToken.getTokenType() == TokenType.LPAREN) {
      accept(TokenType.LPAREN);
      Expression expr = parseExpression();
      accept(TokenType.RPAREN);
//      return expr;
      finalExpr = expr;

    } else if (_currentToken.getTokenType() == TokenType.INTLITERAL
        || _currentToken.getTokenType() == TokenType.BOOLEANLITERAL || _currentToken.getTokenType() == TokenType.NULLLITERAL) { // TODO: warning need to check for expression at start not just int or bool
      Expression expr0;
      if (_currentToken.getTokenType() == TokenType.INTLITERAL) {
        expr0 = new LiteralExpr(new IntLiteral(_currentToken), null);
      } else if (_currentToken.getTokenType() == TokenType.BOOLEANLITERAL){
        expr0 = new LiteralExpr(new BooleanLiteral(_currentToken), null);
      } else {
        expr0 = new LiteralExpr(new NullLiteral(_currentToken), null);
      }
      _currentToken = _scanner.scan();
      if (_currentToken.getTokenType() == TokenType.OPERATOR) {
        Operator op = new Operator(_currentToken);
        accept(TokenType.OPERATOR);
        Expression expr = parseExpression();
//        return new BinaryExpr(op, expr0, expr, null);
        if (expr instanceof BinaryExpr) {
          finalExpr = new BinaryExpr(((BinaryExpr) expr).operator, new BinaryExpr(op, expr0, ((BinaryExpr) expr).left, null), ((BinaryExpr) expr).right, null);
        } else {
          finalExpr = new BinaryExpr(op, expr0, expr, null);
        }

      } else {
        //      return expr0;
        finalExpr = expr0;
      }
    } else if (_currentToken.getTokenType() == TokenType.NEW) {
      accept(TokenType.NEW);
      if (_currentToken.getTokenType() == TokenType.INT) {
//        accept(TokenType.INT);
        TypeDenoter td = parseType();
        accept(TokenType.LBRACK);
        Expression expr = parseExpression();
        accept(TokenType.RBRACK);
//        return new NewArrayExpr(td, expr, null);
        finalExpr = new NewArrayExpr(td, expr, null);

      } else {
        ClassType ct = new ClassType(new Identifier(_currentToken), null);
        accept(TokenType.IDENTIFIER);
        if (_currentToken.getTokenType() == TokenType.LPAREN) {
          accept(TokenType.LPAREN);
          accept(TokenType.RPAREN);
//          return new NewObjectExpr(ct, null);
          finalExpr = new NewObjectExpr(ct, null);

        } else {
          accept(TokenType.LBRACK);
          Expression expr = parseExpression();
          accept(TokenType.RBRACK);
//          return new NewArrayExpr(ct, expr, null);
          finalExpr = new NewArrayExpr(ct, expr, null);

        }
      }
    } else {
      _errors.reportError(
          "Expected an Expression, but got \"" + _currentToken.getTokenText() + "\"");
      throw new SyntaxError();
    }
    if (_currentToken.getTokenType() == TokenType.OPERATOR
        && !_currentToken.getTokenText().equals("!")) {
      Operator op = new Operator(_currentToken);
      accept(TokenType.OPERATOR);
      Expression expr = parseExpression();
      finalExpr = new BinaryExpr(op, finalExpr, expr, null);

    }
    return finalExpr;
  }

  // This method will accept the token and retrieve the next token.
  //  Can be useful if you want to error check and accept all-in-one.
  private void accept(TokenType expectedType) throws SyntaxError {
    if (_currentToken.getTokenType() == expectedType) {
      _currentToken = _scanner.scan();
      return;
    }

    // TODO: Report an error here.
    //  "Expected token X, but got Y"
    _errors.reportError(
        "Expected " + expectedType + ", but got \"" + _currentToken.getTokenText() + "\"");
    throw new SyntaxError();
  }

  class SyntaxError extends Error {
    private static final long serialVersionUID = -6461942006097999362L;
  }
}
