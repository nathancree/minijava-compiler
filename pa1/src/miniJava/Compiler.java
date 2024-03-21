package miniJava;

import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ContextualAnalysis.Identification;
import miniJava.ContextualAnalysis.TypeChecking;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class Compiler {
  // Main function, the file to compile will be an argument.
  public static void main(String[] args) {
    // TODO: Instantiate the ErrorReporter object
    ErrorReporter errorReports = new ErrorReporter();

    // TODO: Check to make sure a file path is given in args
    // TODO: Create the inputStream using new FileInputStream
    FileInputStream inputStream = null;
    try {
      inputStream = new FileInputStream(args[0]);
    } catch (FileNotFoundException e) {
      //      throw new RuntimeException(e);
      System.out.println("Error \nFile Not Found");
      return;
    }

    // TODO: Instantiate the scanner with the input stream and error object
    Scanner scanner = new Scanner(inputStream, errorReports);

    // TODO: Instantiate the parser with the scanner and error object
    Parser parser = new Parser(scanner, errorReports);

    // TODO: Call the parser's parse function
//    AST ast = parser.parse();
    Package prog = parser.parse();
    Identification identification = new Identification(errorReports, prog);
    identification.parse(prog);
    TypeChecking typeChecker = new TypeChecking(errorReports);
    typeChecker.parse(prog);

    // TODO: Check if any errors exist, if so, println("Error")
    //  then output the errors
    if (errorReports.hasErrors()) {
      System.out.println("Error");
      errorReports.outputErrors();
    } else {

      System.out.println("Success");
//      ASTDisplay display = new ASTDisplay();
//      display.showTree(ast);
    }

  }
}
