package com.craftinginterpreters.lox;

public class Interpreter implements Expr.Visitor<Object> {
  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG: return !isTruthy(right);
      case MINUS: return -(double) right;
      default: return null;
    }
  }

  // Lox follows Ruby's simple rule: false and nil are falsey, and everything
  // else is truthy.
  private boolean isTruthy(Object object) {
    if (object == null)
      return false;
    if (object instanceof Boolean)
      return (boolean) object;
    return true;
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }
}
