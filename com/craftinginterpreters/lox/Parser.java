package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  Expr parseExpression() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  // program -> declaration* EOF ;
  List<Stmt> parseStatements() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }
    return statements;
  }

  // declaration -> varDeclaration
  //              | funDeclaration
  //              | statement ;
  private Stmt declaration() {
    try {
      if (match(TokenType.VAR))
        return varDeclaration();
      if (match(TokenType.FUN))
        return function("function");
      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  // varDeclaration -> "var" IDENTIFIER ( "=" expression )? ";" ;
  private Stmt varDeclaration() {
    Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");
    Expr initializer = match(TokenType.EQUAL) ? expression() : null;
    consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  // funDeclartion -> "fun" function ;
  // function -> IDENTIFIER "(" parameters? ")" block ;
  private Stmt function(String kind) {
    Token name = consume(TokenType.IDENTIFIER, "Expect " + kind + " name.");
    consume(TokenType.LEFT_BRACE, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    if (!check(TokenType.RIGHT_BRACE)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters");
        }
        parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."));
      } while (match(TokenType.COMMA));
    }
    consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.");
    consume(TokenType.LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);
  }

  // statement -> printStatement
  //            | ifStatement
  //            | whileStatement
  //            | forStatement
  //            | exprStatement
  //            | block ;
  private Stmt statement() {
    if (match(TokenType.PRINT))
      return printStatement();
    if (match(TokenType.IF))
      return ifStatement();
    if (match(TokenType.WHILE))
      return whileStatement();
    if (match(TokenType.FOR))
      return forStatement();
    if (match(TokenType.LEFT_BRACE))
      return new Stmt.Block(block());
    return expressionStatement();
  }

  // printStatement -> "print" expression ";" ;
  private Stmt printStatement() {
    Expr value = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  // ifStatement -> "if" "(" expression ")" statement
  //              ( "else" statement )? ;
  private Stmt ifStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.");
    Stmt thenBranch = statement();
    Stmt elseBranch = match(TokenType.ELSE) ? statement() : null;
    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  // whileStatement -> "while" "(" expression ")" statement ;
  private Stmt whileStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after while condition.");
    Stmt body = statement();
    return new Stmt.While(condition, body);
  }

  // forStatement -> "for" "(" ( varDeclaration | exprStatement | ";")
  //                 expression? ";"
  //                 expression? ")" statement ;
  private Stmt forStatement() {
    consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.");
    Stmt initializer =
        match(TokenType.SEMICOLON)
            ? null
            : (match(TokenType.VAR) ? varDeclaration() : expressionStatement());
    // If the condition is omitted, it'll be evaluated as a `true` expression.
    Expr condition =
        check(TokenType.SEMICOLON) ? new Expr.Literal(true) : expression();
    consume(TokenType.SEMICOLON, "Expect ';' after for loop condition");
    Expr increment = check(TokenType.RIGHT_PAREN) ? null : expression();
    consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");
    Stmt body = statement();
    // If there is an increment, then we replace the `body` with a block that
    // contains the original `body` followed by an expression statement that
    // evaluates the `increment`.
    if (increment != null) {
      body =
          new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
    }
    // Takes the `condition` and the `body` and build the loop using a primitive
    // `while` loop.
    body = new Stmt.While(condition, body);
    // If there is an initializer, it runs once before the entire loop. We do
    // that by, again, replacing the whole statement with a block that runs the
    // initializer and then executes the loop.
    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }
    return body;
  }

  // exprStatement -> expression ";" ;
  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(TokenType.SEMICOLON, "Expect ';' after expr.");
    return new Stmt.Expression(expr);
  }

  // block -> "{" declaration* "}" ;
  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();
    // The loop also has an explicit check for `isAtEnd()` since we have to be
    // careful to avoid infinite loops, even when parsing invalid code. If the
    // user forgets a closing `}`, the parser needs to not get stuck.
    while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }
    consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  // expression -> assignment ;
  private Expr expression() {
    return assignment();
  }

  // assignment -> IDENTIFIEER "=" assignment
  //             | or ;
  private Expr assignment() {
    Expr expr = or();
    if (match(TokenType.EQUAL)) {
      Token equals = previous();
      Expr value = assignment();
      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      }
      error(equals, "Invalid assignment target.");
    }
    return expr;
  }

  // or -> and ( "or" and )* ;
  private Expr or() {
    Expr expr = and();
    while (match(TokenType.OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
  }

  // and -> equality ( "and" equality )* ;
  private Expr and() {
    Expr expr = equality();
    while (match(TokenType.AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }
    return expr;
  }

  // equality -> comparison ( ( "!=" | "==" ) comparison )* ;
  private Expr equality() {
    Expr expr = comparison();
    while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  // comparison -> term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
  private Expr comparison() {
    Expr expr = term();
    while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS,
                 TokenType.LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  // term -> factor ( ( "-" | "+" ) factor )* ;
  private Expr term() {
    Expr expr = factor();
    while (match(TokenType.MINUS, TokenType.PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  // factor -> unary ( ( "/" | "*" ) unary )* ;
  private Expr factor() {
    Expr expr = unary();
    while (match(TokenType.SLASH, TokenType.STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  // unary -> ( "!" | "-" ) unary | call ;
  private Expr unary() {
    if (match(TokenType.BANG, TokenType.MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return call();
  }

  // call -> primary ( "(" arguments? ")" )* ;
  private Expr call() {
    Expr expr = primary();
    while (true) {
      if (match(TokenType.LEFT_PAREN)) {
        expr = finishCall(expr);
      } else {
        break;
      }
    }
    return expr;
  }

  // arguments -> expression ( "," expression )* ;
  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }
        arguments.add(expression());
      } while (match(TokenType.COMMA));
    }
    Token paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
    return new Expr.Call(callee, paren, arguments);
  }

  // primary -> NUMBER | STRING
  //          | "true" | "false" | "nil"
  //          | "(" expression ")"
  //          | IDENTIFIER ;
  private Expr primary() {
    if (match(TokenType.NUMBER, TokenType.STRING))
      return new Expr.Literal(previous().literal);

    if (match(TokenType.TRUE))
      return new Expr.Literal(true);
    if (match(TokenType.FALSE))
      return new Expr.Literal(false);
    if (match(TokenType.NIL))
      return new Expr.Literal(null);

    if (match(TokenType.LEFT_PAREN)) {
      Expr expr = expression();
      // After we matching an opening '(' and parse the expression inside it, we
      // must find a ')' token. If we don't, that's an error.
      consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(TokenType.IDENTIFIER))
      return new Expr.Variable(previous());

    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance(); // Consumes the token.
        return true;
      }
    }
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type))
      return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd())
      return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd())
      ++current;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == TokenType.EOF;
  }

  // Returns the current token we have yet to consume.
  private Token peek() {
    return tokens.get(current);
  }

  // Returns the most recently consumed token. Makes it easier to use `match()`
  // and then access the just-matched token.
  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == TokenType.SEMICOLON)
        return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN: return;
        default: break;
      }

      advance();
    }
  }
}
