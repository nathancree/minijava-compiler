package miniJava.SyntacticAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import miniJava.ErrorReporter;

public class Scanner {
  private final InputStream _in;
  private final ErrorReporter _errors;
  private final StringBuilder _currentText;
  private char _currentChar;
  private boolean _endOfFileReached;

  public Scanner(InputStream in, ErrorReporter errors) {
    this._in = in;
    this._errors = errors;
    this._currentText = new StringBuilder();
    this._endOfFileReached = false;

    nextChar();
  }

  public Token scan() {
    // TODO: This function should check the current char to determine what the token could be.

    // TODO: Consider what happens if the current char is whitespace

    // TODO: Consider what happens if there is a comment (// or /* */)

    // TODO: What happens if there are no more tokens?

    // TODO: Determine what the token is. For example, if it is a number
    //  keep calling takeIt() until _currentChar is not a number. Then
    //  create the token via makeToken(TokenType.IntegerLiteral) and return it.

    _currentText.setLength(0); // reset _currentText string
    if (_endOfFileReached) {
      return makeToken(TokenType.EOT);
    }
    // skip white space
    while (_currentChar == ' '
        || _currentChar == '\n'
        || _currentChar == '\t'
        || _currentChar == '\r') {
      if (_endOfFileReached) {
        return makeToken(TokenType.EOT);
      }
      skipIt();
    }
    // check for comments
    if (_currentChar == '/') {
      skipIt();
      if (_currentChar == '/' || _currentChar == '*') {
        return commentScan();
      }
      return new Token(TokenType.OPERATOR, "/");
    } else if (_currentChar == '=') {
      takeIt();
      if (_currentChar == '=') {
        takeIt();
        return makeToken(TokenType.OPERATOR);
      }
      return makeToken(TokenType.EQUALS);
    } else if (_currentChar == '>' || _currentChar == '<' || _currentChar == '!') {
      takeIt();
      if (_currentChar == '=') {
        takeIt();
      }
      return makeToken(TokenType.OPERATOR);
    } else if (_currentChar == '&') {
      takeIt();
      if (_currentChar == '&') {
        takeIt();
        return new Token(TokenType.OPERATOR, "&&");
      }
      _errors.reportError("Cannot convert input: \"" + _currentText.toString() + "\" to a token");
      return scan();
    } else if (_currentChar == '|') {
      takeIt();
      if (_currentChar == '|') {
        takeIt();
        return new Token(TokenType.OPERATOR, "||");
      }
      _errors.reportError("Cannot convert input: \"" + _currentText.toString() + "\" to a token");
      return scan();
    } else if (_currentChar == '{') {
      takeIt();
      return makeToken(TokenType.LCURLY);
    } else if (_currentChar == '}') {
      takeIt();
      return makeToken(TokenType.RCURLY);
    } else if (_currentChar == '[') {
      takeIt();
      if(_currentChar == ']') {
        return makeToken(TokenType.BRACKETS);
      }
      return makeToken(TokenType.LBRACK);
    } else if (_currentChar == ']') {
      takeIt();
      return makeToken(TokenType.RBRACK);
    } else if (_currentChar == '(') {
      takeIt();
      return makeToken(TokenType.LPAREN);
    } else if (_currentChar == ')') {
      takeIt();
      return makeToken(TokenType.RPAREN);
    } else if (_currentChar == ',') {
      takeIt();
      return makeToken(TokenType.COMMA);
    } else if (_currentChar == ';') {
      takeIt();
      return makeToken(TokenType.SEMICOLON);
    } else if (_currentChar == '.') {
      takeIt();
      return makeToken(TokenType.PERIOD);
    } else if (_currentChar == '+' || _currentChar == '-' || _currentChar == '*') {
      takeIt();
      return makeToken(TokenType.OPERATOR);
    }
    // TODO: Check current char and return token type if "{" or other single char token

    // TODO: Check for int literals?
    while (_currentChar != '{'
        && _currentChar != '}'
        && _currentChar != '('
        && _currentChar != ')'
        && _currentChar != '['
        && _currentChar != ']'
        && _currentChar != ','
        && _currentChar != ';'
        && _currentChar != '.'
        && _currentChar != '='
        && _currentChar != ' '
        && _currentChar != '\n'
        && _currentChar != '\t'
        && _currentChar != '\r'
        && _currentChar != '/'
        && _currentChar != '>'
        && _currentChar != '<'
        && _currentChar != '!'
        && _currentChar != '&'
        && _currentChar != '|'
        && _currentChar != '+'
        && _currentChar != '-'
        && _currentChar != '*') {
      if (_endOfFileReached) {
        return makeToken(TokenType.EOT);
      }
      takeIt();
    }

    switch (_currentText.toString()) {
      case "class":
        return makeToken(TokenType.CLASS);
      case "public":
        return makeToken(TokenType.PUBLIC);
      case "private":
        return makeToken(TokenType.PRIVATE);
      case "static":
        return makeToken(TokenType.STATIC);
      case "void":
        return makeToken(TokenType.VOID);
      case "int":
        return makeToken(TokenType.INT);
      case "boolean":
        return makeToken(TokenType.BOOLEAN);
      case "if":
        return makeToken(TokenType.IF);
      case "else":
        return makeToken(TokenType.ELSE);
      case "return":
        return makeToken(TokenType.RETURN);
      case "while":
        return makeToken(TokenType.WHILE);
      case "this":
        return makeToken(TokenType.THIS);
      case "null":
        return makeToken(TokenType.NULL);
      case "new":
        return makeToken(TokenType.NEW);
      case "true":
      case "false":
        return makeToken(TokenType.BOOLEANLITERAL);
      default:
        if (Character.isDigit(_currentText.charAt(0))) {
          for (int i = 1; i < _currentText.length(); i++) {
            if (!Character.isDigit(_currentText.charAt(0))) {
              _errors.reportError(
                  "Cannot convert input: \"" + _currentText.toString() + "\" to a token");
              return scan();
            }
          }
          return makeToken(TokenType.INTLITERAL);
        } else {
          if (_currentText.charAt(0) != '_') {
            return makeToken(TokenType.IDENTIFIER);
          }
          _errors.reportError(
              "Cannot convert input: \"" + _currentText.toString() + "\" to a token");
          return scan();
        }
    }
  }
  // TODO: Reformat this, logic isn't great
  private Token commentScan() {
    if (_currentChar == '/') {
      skipIt();
      while (_currentChar != '\n' && _currentChar != '\r' && !_endOfFileReached) {
        skipIt();
      }
      return scan(); // return next token after comment
    } else if (_currentChar == '*') {
      skipIt();
      while (!_endOfFileReached) {
        if (_currentChar == '*') {
          skipIt();
          if (_currentChar == '/') {
            skipIt();
            return scan();
          }
        } else {
          skipIt();
        }
      }
    }
    return makeToken(TokenType.EOT); // Should never get here
  }

  private void takeIt() {
    _currentText.append(_currentChar);
    nextChar();
  }

  private void skipIt() {
    nextChar();
  }

  private void nextChar() {
    try {
      int c = _in.read();
      _currentChar = (char) c;

      // TODO: What happens if c == -1?
      if (c == -1) {
        _endOfFileReached = true;
      }
      // TODO: What happens if c is not a regular ASCII character?

    } catch (IOException e) {
      // TODO: Report an error here
      _errors.reportError("Unrecognized ASCII character, cannot scan");
    }
  }

  private Token makeToken(TokenType toktype) {
    // TODO: return a new Token with the appropriate type and text
    //  contained in
    return new Token(toktype, _currentText.toString());
  }
}
