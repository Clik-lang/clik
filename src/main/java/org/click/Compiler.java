package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Compiler {
    private final AtomicInteger counter = new AtomicInteger();
    private final Context context;

    record Context(ScopeWalker<TrackedVariable> walker,
                   Map<String, Program.Expression> variables,
                   Map<String, Program.Struct> structs,
                   Map<String, Program.Function> functions,
                   Map<String, Type.Function> globalFunctions) {
        public Context {
            globalFunctions = Map.copyOf(globalFunctions);
        }
    }

    record Scope(ScopeWalker<TrackedVariable> previousWalker, Value.FunctionDecl functionDecl,
                 Set<String> captures, List<Program.Statement> statements) {
    }

    record TrackedVariable(String globalName, Type type, DeclarationType declarationType) {
    }

    public Compiler(List<Ast.Statement> astStatements) {
        // Find global functions
        Map<String, Type.Function> globalFunctions = new HashMap<>();
        for (Ast.Statement statement : astStatements) {
            if (statement instanceof Ast.Statement.Declare declare &&
                    declare.initializer() instanceof Ast.Expression.Constant constant &&
                    constant.value() instanceof Value.FunctionDecl functionDecl) {
                final String name = declare.names().get(0);
                final Type.Function type = new Type.Function(functionDecl.parameters(), functionDecl.returnType());
                globalFunctions.put(name, type);
            }
        }
        // Start analyzing
        ScopeWalker<TrackedVariable> walker = new ScopeWalker<>();
        this.context = new Context(walker, new HashMap<>(), new HashMap<>(), new HashMap<>(), globalFunctions);

        walker.enterBlock();
        var comp = new StatementCompiler(context, null);
        for (Ast.Statement statement : astStatements) comp.compileStatement(statement);
        walker.exitBlock();
    }

    record StatementCompiler(Context context, Scope scope) {
        private Scope compileFunction(Value.FunctionDecl functionDecl) {
            ScopeWalker<TrackedVariable> walker = context.walker();

            ScopeWalker<TrackedVariable> copy = new ScopeWalker<>();
            copy.enterBlock();
            walker.currentScope().tracked().forEach(copy::register);
            Scope newScope = new Scope(copy, functionDecl, new HashSet<>(), new ArrayList<>());
            StatementCompiler comp = new StatementCompiler(context, newScope);

            walker.enterBlock();
            for (Ast.Parameter param : functionDecl.parameters()) {
                final String name = param.name();
                final TrackedVariable tracked = new TrackedVariable(name, param.type(), DeclarationType.VARIABLE);
                walker.register(name, tracked);
            }
            for (Ast.Statement statement : functionDecl.body()) comp.compileStatement(statement);
            walker.exitBlock();
            return comp.scope();
        }

        private Program.Statement compileBlock(Ast.Statement statement) {
            List<Program.Statement> statements = new ArrayList<>();
            ScopeWalker<TrackedVariable> walker = context.walker();
            walker.enterBlock();
            var newScope = new Scope(walker, scope.functionDecl, scope.captures, statements);
            StatementCompiler comp = new StatementCompiler(context, newScope);
            comp.compileStatement(statement);
            walker.exitBlock();
            return new Program.Statement.Block(statements);
        }

        private void compileStatement(Ast.Statement statement) {
            ScopeWalker<TrackedVariable> walker = context.walker();
            var variables = context.variables();
            var functions = context.functions();
            var structs = context.structs();
            switch (statement) {
                case Ast.Statement.Declare declare -> {
                    final DeclarationType declarationType = declare.declarationType();
                    if (scope == null && declarationType == DeclarationType.VARIABLE) {
                        throw error("Global variables are not supported, must be constant or shared.");
                    }

                    final List<String> names = declare.names();
                    // Check for duplicates
                    for (String name : names) {
                        final TrackedVariable present = walker.find(name);
                        if (present != null) throw error("Variable " + name + " already declared");
                    }

                    final Program.Expression expression = compileExpression(declare.initializer(), declare.explicitType());
                    boolean special = false;
                    if (expression instanceof Program.Expression.Constant constant) {
                        if (constant.value() instanceof Value.FunctionDecl functionDecl) {
                            final Scope scope = compileFunction(functionDecl);
                            final Type.Function functionType = new Type.Function(scope.functionDecl.parameters(), scope.functionDecl.returnType());
                            functions.put(names.get(0), new Program.Function(
                                    functionType, scope.captures, scope.statements
                            ));
                            special = true;
                        } else if (constant.value() instanceof Value.StructDecl structDecl) {
                            final List<Program.TypedName> fields = new ArrayList<>();
                            for (Ast.Parameter parameter : structDecl.parameters()) {
                                fields.add(new Program.TypedName(parameter.name(), parameter.type()));
                            }
                            structs.put(names.get(0), new Program.Struct(fields));
                            special = true;
                        }
                    }

                    if (!special) {
                        if (scope == null) {
                            variables.put(names.get(0), expression);
                        } else {
                            this.scope.statements.add(new Program.Statement.Declare(names.get(0), declarationType, expression));
                        }
                    }

                    // Declare
                    for (String name : names) {
                        walker.register(name, new TrackedVariable(name, expression.expressionType(), declarationType));
                    }
                }
                case Ast.Statement.Assign assign -> {
                    if (scope == null) throw error("Assignments are not allowed in global scope");
                    final String name = assign.targets().get(0).name();
                    final TrackedVariable tracked = walker.find(name);
                    if (tracked == null) throw error("Variable " + assign.targets().get(0).name() + " not declared");
                    if (tracked.declarationType() == DeclarationType.CONSTANT)
                        throw error("Cannot assign to constant " + assign.targets().get(0).name());
                    final Program.Expression expression = compileExpression(assign.expression(), tracked.type());
                    this.scope.statements.add(new Program.Statement.Assign(assign.targets().get(0).name(), expression));
                }
                case Ast.Statement.Run run -> {
                    final Program.Expression expression = compileExpression(run.expression(), null);
                    this.scope.statements.add(new Program.Statement.Run(expression));
                }
                case Ast.Statement.Branch branch -> {
                    final Program.Expression condition = compileExpression(branch.condition(), Type.BOOL);
                    final Program.Statement then = compileBlock(branch.thenBranch());
                    final Program.Statement else_ = branch.elseBranch() != null ? compileBlock(branch.elseBranch()) : null;
                    this.scope.statements.add(new Program.Statement.Branch(condition, then, else_));
                }
                case Ast.Statement.Block block -> {
                    walker.enterBlock();
                    for (Ast.Statement statement1 : block.statements()) compileStatement(statement1);
                    walker.exitBlock();
                }
                case Ast.Statement.Return returnStatement -> {
                    if (scope == null) throw error("Cannot return from global scope");
                    final Program.Expression expression = compileExpression(returnStatement.expression(), scope.functionDecl().returnType());
                    this.scope.statements.add(new Program.Statement.Return(expression));
                }
                default -> throw error("Unknown statement " + statement);
            }
        }

        private Program.Expression compileExpression(Ast.Expression expression, @Nullable Type explicitType) {
            ScopeWalker<TrackedVariable> walker = context.walker();
            final Program.Expression result = switch (expression) {
                case Ast.Expression.Constant constant -> new Program.Expression.Constant(constant.value());
                case Ast.Expression.Variable variable -> {
                    final String name = variable.name();
                    final TrackedVariable tracked = walker.find(name);
                    if (tracked == null) throw error("Variable " + name + " not declared");
                    if (scope.previousWalker.find(name) != null) this.scope.captures.add(name);
                    if (tracked.type() instanceof Type.Function functionType) {
                        yield new Program.Expression.Constant(new Value.Function(functionType, name));
                    } else {
                        yield new Program.Expression.Variable(tracked.type(), name);
                    }
                }
                case Ast.Expression.Binary binary -> {
                    final Program.Expression left = compileExpression(binary.left(), explicitType);
                    final Program.Expression right = compileExpression(binary.right(), explicitType);
                    final Type type = left.expressionType();
                    if (!type.equals(right.expressionType())) {
                        throw error("Type mismatch, expected " + type + " but got " + right.expressionType());
                    }
                    yield new Program.Expression.Binary(
                            type,
                            left,
                            binary.operator(),
                            right
                    );
                }
                case Ast.Expression.Unary unary -> {
                    final Program.Expression operand = compileExpression(unary.expression(), explicitType);
                    if (operand.expressionType() != Type.BOOL)
                        throw error("Unary operator " + unary.operator() + " only works on boolean expressions");
                    yield new Program.Expression.Unary(
                            Type.BOOL,
                            unary.operator(),
                            operand
                    );
                }
                case Ast.Expression.Call call -> {
                    final String name = call.name();
                    Type.Function functionType;
                    final TrackedVariable tracked = walker.find(name);
                    if (tracked == null) {
                        functionType = context.globalFunctions.get(name);
                        if (functionType == null) throw error("Variable '" + name + "' is not a function");
                    } else {
                        if (!(tracked.type() instanceof Type.Function type))
                            throw error("Variable '" + name + "' is not a function");
                        functionType = type;
                    }
                    final List<Program.Expression> params = call.arguments().expressions().stream()
                            .map(e -> compileExpression(e, null)).toList();
                    yield new Program.Expression.Call(functionType.returnType(), name, params);
                }
                case Ast.Expression.Initialization initialization -> {
                    final Type type = Objects.requireNonNullElse(initialization.type(), explicitType);
                    final Ast.Parameter.Passed passed = initialization.parameters();
                    yield switch (type) {
                        case Type.Identifier identifier -> {
                            if (passed instanceof Ast.Parameter.Passed.Positional positional) {
                                final List<Program.Expression> expressions = positional.expressions().stream()
                                        .map(e -> compileExpression(e, null)).toList();
                                yield new Program.Expression.Struct(identifier, new Program.TypedName.Passed.Positional(expressions));
                            } else if (passed instanceof Ast.Parameter.Passed.Named named) {
                                Map<String, Program.Expression> entries = new HashMap<>();
                                for (Map.Entry<String, Ast.Expression> entry : named.entries().entrySet()) {
                                    entries.put(entry.getKey(), compileExpression(entry.getValue(), null));
                                }
                                yield new Program.Expression.Struct(identifier, new Program.TypedName.Passed.Named(entries));
                            } else {
                                throw error("Expected positional or named parameters, got: " + passed);
                            }
                        }
                        case Type.Array arrayType -> {
                            if (passed instanceof Ast.Parameter.Passed.Positional positional) {
                                final List<Program.Expression> expressions = positional.expressions().stream()
                                        .map(e -> compileExpression(e, null)).toList();
                                yield new Program.Expression.Array(arrayType, new Program.TypedName.Passed.Positional(expressions));
                            } else {
                                throw error("Expected positional parameters, got: " + passed);
                            }
                        }
                        case Type.Map mapType -> {
                            if (passed instanceof Ast.Parameter.Passed.Mapped mapped) {
                                Map<Program.Expression, Program.Expression> entries = new HashMap<>();
                                for (var entry : mapped.entries().entrySet()) {
                                    var key = compileExpression(entry.getKey(), null);
                                    var value = compileExpression(entry.getValue(), null);
                                    entries.put(key, value);
                                }
                                yield new Program.Expression.Map(mapType, new Program.TypedName.Passed.Mapped(entries));
                            } else {
                                throw error("Expected mapped parameters, got: " + passed);
                            }
                        }
                        case Type.Table tableType -> {
                            if (passed instanceof Ast.Parameter.Passed.Positional positional) {
                                final List<Program.Expression> expressions = positional.expressions().stream()
                                        .map(e -> compileExpression(e, null)).toList();
                                yield new Program.Expression.Table(tableType, new Program.TypedName.Passed.Positional(expressions));
                            } else {
                                throw new RuntimeException("Expected positional parameters, got: " + passed);
                            }
                        }
                        default ->
                                throw new RuntimeException("Invalid initialization: " + initialization + " " + explicitType);
                    };
                }
                default -> throw error("Unknown expression type " + expression.getClass().getName());
            };
            final Type type = result.expressionType();
            if (explicitType != null && !explicitType.equals(type)) {
                throw error("Type mismatch, expected " + explicitType + " but got " + type + " -> " + expression);
            }
            return result;
        }
    }

    public Program compile() {
        var variables = context.variables();
        var structs = context.structs();
        var functions = context.functions();
        return new ProgramImpl(variables, structs, functions);
    }

    private static RuntimeException error(String message) {
        return new RuntimeException(message);
    }

    record ProgramImpl(Map<String, Expression> variables,
                       Map<String, Program.Struct> structs,
                       Map<String, Program.Function> functions) implements Program {
    }
}
