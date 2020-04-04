package com.jn.sqlhelper.dialect.ast.expression;

import com.jn.langx.util.collection.Collects;
import com.jn.langx.util.function.Consumer2;

import java.util.List;

public class ListExpression implements SQLExpression<SQLExpression> {
    private List<SQLExpression> expressions = Collects.emptyArrayList();

    @Override
    public SQLExpression execute() {
        return null;
    }

    public void addAll(List<SQLExpression> expressions) {
        for (SQLExpression expression : expressions) {
            add(expression);
        }
    }

    public void add(SQLExpression expression) {
        expressions.add(expression);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(255);
        Collects.forEach(expressions, new Consumer2<Integer, SQLExpression>() {
            @Override
            public void accept(Integer index, SQLExpression expression) {
                if (index > 0) {
                    builder.append(", ");
                }
                builder.append(expression.toString());
            }
        });
        return builder.toString();
    }
}