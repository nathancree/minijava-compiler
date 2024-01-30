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
    while (_currentToken.getTokenType() != TokenType.RCURLY){
      parseVisibility();
      parseAccess();
      if (_currentToken.getTokenType() == TokenType.VOID) {
        parseMethodDeclaration();
      } else {
        parseType();
        accept(TokenType.IDENTIFIER);
        if (_currentToken.getTokenType() == TokenType.SEMICOLON) {
          accept(TokenType.SEMICOLON);
        } else {
          parseMethodDeclaration();
        }
      }
    }
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
      accept(TokenType.IDENTIFIER);
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
    if (_currentToken.getTokenType() == TokenType.LCURLY) {
      accept(TokenType.LCURLY);
      while (_currentToken.getTokenType() != TokenType.RCURLY) {
        parseStatement();
      }
      accept(TokenType.RCURLY);
    } else if (_currentToken.getTokenType() == TokenType.INT
        || _currentToken.getTokenType() == TokenType.BOOLEAN) {
      parseType();
      accept(TokenType.IDENTIFIER);
      accept(TokenType.EQUALS);
      parseExpression();
      accept(TokenType.SEMICOLON);
    } else if (_currentToken.getTokenType() == TokenType.IDENTIFIER) { // could be Reference or Type
      accept(TokenType.IDENTIFIER);
      if (_currentToken.getTokenType() != TokenType.PERIOD) { // accepts Type ID[]
        accept(TokenType.LBRACK);
        accept(TokenType.RBRACK);
        accept(TokenType.IDENTIFIER);
        accept(TokenType.EQUALS);
        parseExpression();
        accept(TokenType.SEMICOLON);
      } else {
        parseReference();
        if (_currentToken.getTokenType() == TokenType.EQUALS) {
          accept(TokenType.EQUALS);
          parseExpression();
          accept(TokenType.SEMICOLON);
        } else if (_currentToken.getTokenType() == TokenType.LBRACK) {
          accept(TokenType.LBRACK);
          parseExpression();
          accept(TokenType.RBRACK);
          accept(TokenType.EQUALS);
          parseExpression();
          accept(TokenType.SEMICOLON);
        } else {
          accept(TokenType.LPAREN);
          if (_currentToken.getTokenType() != TokenType.RPAREN) {
            parseArgumentList();
          }
          accept(TokenType.RPAREN);
          accept(TokenType.SEMICOLON);
        }
      }
    } else if (_currentToken.getTokenType() == TokenType.RETURN) {
      accept(TokenType.RETURN);
      if (_currentToken.getTokenType() != TokenType.SEMICOLON) {
        parseExpression();
      }
      accept(TokenType.SEMICOLON);
    } else if (_currentToken.getTokenType() == TokenType.IF) {
      accept(TokenType.IF);
      accept(TokenType.LPAREN);
      parseExpression();
      accept(TokenType.RPAREN);
      parseStatement();
      if (_currentToken.getTokenType() == TokenType.ELSE) {
        accept(TokenType.ELSE);
        parseStatement();
      }
    } else if (_currentToken.getTokenType() == TokenType.WHILE) {
      accept(TokenType.WHILE);
      accept(TokenType.LPAREN);
      parseExpression();
      accept(TokenType.RPAREN);
      parseStatement();
    }
  }

  private void parseExpression() throws SyntaxError {
    if (_currentToken.getTokenType() == TokenType.IDENTIFIER || _currentToken.getTokenType() == TokenType.THIS) {
      parseReference();
      if (_currentToken.getTokenType() == TokenType.LBRACK) {
        accept(TokenType.LBRACK);
        parseExpression();
        accept(TokenType.RBRACK);
      } else if (_currentToken.getTokenType() == TokenType.LPAREN) {
        accept(TokenType.LPAREN);
        if (_currentToken.getTokenType() != TokenType.RPAREN) {
          parseArgumentList();
        }
        accept(TokenType.RPAREN);
      }
    } else if (_currentToken.getTokenText().equals("!") || _currentToken.getTokenText().equals("-")) { //warning
      _currentToken = _scanner.scan();
      parseExpression();
    } else if (_currentToken.getTokenType() == TokenType.LPAREN) {
      accept(TokenType.LPAREN);
      parseExpression();
      accept(TokenType.RPAREN);
    } else if (_currentToken.getTokenType() == TokenType.INTLITERAL || _currentToken.getTokenType() == TokenType.BOOLEANLITERAL) {
      _currentToken = _scanner.scan();
    } else if (_currentToken.getTokenType() == TokenType.NEW) {
      accept(TokenType.NEW);
      if (_currentToken.getTokenType() == TokenType.INT) {
        accept(TokenType.INT);
        accept(TokenType.LBRACK);
        parseExpression();
        accept(TokenType.RBRACK);
      } else {
        accept(TokenType.IDENTIFIER);
        if (_currentToken.getTokenType() == TokenType.LPAREN) {
          accept(TokenType.LPAREN);
          accept(TokenType.RPAREN);
        } else {
          accept(TokenType.LBRACK);
          parseExpression();
          accept(TokenType.RBRACK);
        }
      }
    } else {
      parseExpression();
      accept(TokenType.OPERATOR);
      parseExpression();
    }
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
    _errors.reportError("Expected " + expectedType + ", but got " + _currentToken.getTokenText());
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
