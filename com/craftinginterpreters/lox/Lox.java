package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
  // Makes the field static so that successive calls to `run()` inside a REPL
  // session reuse the same interpreter.
  private static final Interpreter interpreter = new Interpreter();
  static boolean hadError = false;
  static boolean hadRuntimeError = false;

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64);
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    run(new String(bytes, Charset.defaultCharset()), /*isREPL=*/false);

    // Indicate an error in the exit code.
    if (hadError)
      System.exit(65);
    if (hadRuntimeError)
      System.exit(70);
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    for (;;) {
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null)
        break;
      run(line, /*isREPL=*/true);
      // Reset this flag in the interactive loop. If the user makes a mistake,
      // it shouldn’t kill their entire session.
      hadError = false;
    }
  }

  private static void run(String source, boolean isREPL) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    Parser parser = new Parser(tokens);

    if (isREPL && tokens.get(tokens.size() - 2).type != TokenType.SEMICOLON) {
      Expr expression = parser.parseExpression();
      // Stop if there was a syntax error.
      if (hadError)
        return;
      interpreter.interpret(expression);
    } else {
      List<Stmt> statements = parser.parseStatements();
      // Stop if there was a syntax error.
      if (hadError)
        return;
      interpreter.interpret(statements);
    }
  }

  static void error(int line, String message) {
    report(line, "", message);
  }

  private static void report(int line, String where, String message) {
    System.err.println("[line " + line + "] Error" + where + ": " + message);
    hadError = true;
  }

  static void error(Token token, String message) {
    if (token.type == TokenType.EOF) {
      report(token.line, " at end", message);
    } else {
      report(token.line, " at '" + token.lexeme + "'", message);
    }
  }

  static void runtimeError(RuntimeError error) {
    System.err.println(error.getMessage() + "\n[line " + error.token.line +
                       "]");
    hadRuntimeError = true;
  }
}
