package com.craftinginterpreters.lox;

import java.util.List;

class Parser {
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // expression -> equality ;
  private Expr expression() {
    return equality();
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

  // unary -> ( "!" | "-" ) unary
  //        | primary ;
  private Expr unary() {
    if (match(TokenType.BANG, TokenType.MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return primary();
  }

  // primary -> NUMBER | STRING
  //          | "true" | "false" | "nil"
  //          | "(" expression ")" ;
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
}
