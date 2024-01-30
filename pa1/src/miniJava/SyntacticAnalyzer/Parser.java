package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

public class Parser {
  private Scanner _scanner;
  private ErrorReporter _errors;
  private Token _currentToken;

  public Parser(Scanner scanner, ErrorReporter errors) {
    this._scanner = scanner;
    this._errors = errors;
    this._currentToken = this._scanner.scan();
  }

  public void parse() {
    try {
      // The first thing we need to parse is the Program symbol
      parseProgram();
    } catch (SyntaxError e) {
    }
  }

  // Program ::= (ClassDeclaration)* eot
  private void parseProgram() throws SyntaxError {
    // TODO: Keep parsing class declarations until eot
    while (_currentToken.getTokenType() != TokenType.EOT) {
      parseClassDeclaration();
    }
  }

  // ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
  private void parseClassDeclaration() throws SyntaxError {
    // TODO: Take in a "class" token (check by the TokenType)
    //  What should be done if the first token isn't "class"?
    accept(TokenType.CLASS);

    // TODO: Take in an identifier token
    accept(TokenType.IDENTIFIER);
    // TODO: Take in a {
    accept(TokenType.LCURLY);
    // TODO: Parse either a FieldDeclaration or MethodDeclaration
    parseVisibility();
    parseAccess();
    // TODO: Take in a }
    accept(TokenType.RCURLY);
  }

  private void parseFieldDeclaration() throws SyntaxError {
    parseType();
    accept(TokenType.IDENTIFIER);
    accept(TokenType.SEMICOLON);
  }

  private void parseMethodDeclaration() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.VOID) {
      accept(TokenType.VOID);
    } else {
      parseType();
    }
    accept(TokenType.LPAREN);
    if (_currentToken.getTokenType() != TokenType.RPAREN) {
      parseParameterList();
    }
    accept(TokenType.RPAREN);
    accept(TokenType.LCURLY);
    while (_currentToken.getTokenType() != TokenType.RCURLY) {
      parseStatement();
    }
    accept(TokenType.RCURLY);
  }

  private void parseVisibility() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.PUBLIC
        || _currentToken.getTokenType() == TokenType.PRIVATE) {
      _currentToken = _scanner.scan();
    }
  }

  private void parseAccess() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.STATIC) {
      _currentToken = _scanner.scan();
    }
  }

  private void parseType() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.BOOLEAN) {
      accept(TokenType.BOOLEAN);
    } else if (_currentToken.getTokenType() == TokenType.INT) {
      accept(TokenType.INT);
      if (_currentToken.getTokenType() == TokenType.LBRACK) {
        accept(TokenType.LBRACK);
        accept(TokenType.RBRACK);
      }
    }
  }

  private void parseParameterList() throws SyntaxError {
    parseType();
    accept(TokenType.IDENTIFIER);
    accept(TokenType.LPAREN);

    while (_currentToken.getTokenType() == TokenType.COMMA) {
      accept(TokenType.COMMA);
      parseType();
      accept(TokenType.IDENTIFIER);
    }
  }

  private void parseArgumentList() throws SyntaxError {
    parseExpression();
    while (_currentToken.getTokenType() == TokenType.COMMA) {
      accept(TokenType.COMMA);
      parseExpression();
    }
  }

  private void parseReference() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.IDENTIFIER) {
      accept(TokenType.IDENTIFIER);
    } else {
      accept(TokenType.THIS);
    }
    while (_currentToken.getTokenType() == TokenType.PERIOD) {
      accept(TokenType.PERIOD);
      accept(TokenType.IDENTIFIER);
    }
  }

  private void parseStatement() throws SyntaxError {

  }

  private void parseExpression() throws SyntaxError {}

  // This method will accept the token and retrieve the next token.
  //  Can be useful if you want to error check and accept all-in-one.
  private void accept(TokenType expectedType) throws SyntaxError {
    if (_currentToken.getTokenType() == expectedType) {
      _currentToken = _scanner.scan();
      return;
    }

    // TODO: Report an error here.
    //  "Expected token X, but got Y"
    _errors.reportError("Expected " + expectedType + ", but got " + _currentToken);
    throw new SyntaxError();
  }

  private boolean optionalAccept(TokenType expectedType) throws SyntaxError {
    if (_currentToken.getTokenType() == expectedType) {
      _currentToken = _scanner.scan();
      return true;
    } else {
      return false;
    }
  }

  class SyntaxError extends Error {
    private static final long serialVersionUID = -6461942006097999362L;
  }
}
