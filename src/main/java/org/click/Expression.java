package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Expression {
    record Constant(Value value) implements Expression {
    }

    record Function(List<Parameter> parameters, Type returnType,
                    List<Statement> body) implements Expression {
    }

    record Struct(List<Parameter> parameters) implements Expression {
    }

    record Enum(@Nullable Type type, Map<String, Expression> entries) implements Expression {
    }

    record Union(Map<String, @Nullable Struct> entries) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Group(Expression expression) implements Expression {
    }

    record Field(Expression object, AccessPoint accessPoint) implements Expression {
    }

    record VariableAwait(String name) implements Expression {
    }

    record ArrayAccess(Expression array, Expression index, @Nullable Type transmuteType) implements Expression {
    }

    record Call(String name, Parameter.Passed arguments) implements Expression {
    }

    record Select(List<Statement.Block> blocks) implements Expression {
    }

    record StructValue(String name, Parameter.Passed fields) implements Expression {
    }

    record ArrayValue(Type.Array arrayType, @Nullable List<Expression> expressions) implements Expression {
    }

    record MapValue(Type.Map mapType, Parameter.Passed.Mapped parameters) implements Expression {
    }

    record InitializationBlock(Parameter.Passed parameters) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Token.Type operator, Expression right) implements Expression {
    }

    record Unary(Token.Type operator, Expression expression) implements Expression {
    }
}
