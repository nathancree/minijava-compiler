/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.Token;

public class Identifier extends Terminal {
  private String name;
  private Declaration declaration;


  public Identifier (Token t) {
    super (t);
    name = t.getTokenText();
    declaration = null;
  }
  public Identifier (Token t, Declaration d) {
    super (t);
    declaration = d;
    name = t.getTokenText();
  }

  public <A,R> R visit(Visitor<A,R> v, A o) {
      return v.visitIdentifier(this, o);
  }

  public Declaration getDeclaration() {
    return declaration;
  }

  public void setDeclaration(Declaration d) {
    declaration = d;
  }

  public String getName() {
    return name;
  }
}
