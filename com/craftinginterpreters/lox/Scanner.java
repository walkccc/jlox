package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Scanner {
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int start = 0;   // first char in the lexeme being scanned
  private int current = 0; // the char currently being considered
  private int line = 1;    // tracks the line number

  Scanner(String source) {
    this.source = source;
  }

  List<Token> scanTokens() {
    while (!isAtEnd()) {
      start = current;
      scanToken();
    }

    tokens.add(new Token(TokenType.EOF, "", null, line));
    return tokens;
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(': addToken(TokenType.LEFT_PAREN); break;
      case ')': addToken(TokenType.RIGHT_PAREN); break;
      case '{': addToken(TokenType.LEFT_BRACE); break;
      case '}': addToken(TokenType.RIGHT_BRACE); break;
      case ',': addToken(TokenType.COMMA); break;
      case '.': addToken(TokenType.DOT); break;
      case '-': addToken(TokenType.MINUS); break;
      case '+': addToken(TokenType.PLUS); break;
      case ';': addToken(TokenType.SEMICOLON); break;
      case '*': addToken(TokenType.STAR); break;
      case '!':
        addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
        break;
      case '=':
        addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
        break;
      case '<':
        addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
        break;
      case '>':
        addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
        break;
      case '/':
        if (match('/')) {
          // A comment goes until the end of the line.
          while (!isAtEnd() && peek() != '\n')
            advance();
        } else {
          addToken(TokenType.SLASH);
        }
        break;
      case ' ':
      case '\r':
      case '\t':
        // Ignore whitespace.
        break;
      case '\n': ++line; break;
      default: Lox.error(line, "Unexpected character."); break;
    }
  }

  // When we reach, for example, '!', we jump to its switch case. That means we
  // know the lexeme starts with '!'. Then, we look at the next character to
  // determine if we're on a "!=" or merely a '!'.
  private boolean match(char expected) {
    if (isAtEnd())
      return false;
    if (source.charAt(current) != expected)
      return false;
    ++current;
    return true;
  }

  private char peek() {
    if (isAtEnd())
      return '\0';
    return source.charAt(current);
  }

  private boolean isAtEnd() {
    return current >= source.length();
  }

  // Consumes the next character in the source file and returns it.
  private char advance() {
    return source.charAt(current++);
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line));
  }
}
