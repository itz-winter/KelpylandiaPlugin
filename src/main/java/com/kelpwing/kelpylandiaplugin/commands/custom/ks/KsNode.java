package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.List;

/**
 * All AST (Abstract Syntax Tree) node types produced by {@link KsParser}.
 *
 * Statements and expressions are separate hierarchies both rooted at {@link KsNode}.
 * Each concrete node type is a static inner class to keep the whole AST definition
 * in one place and to let the interpreter use {@code instanceof} pattern matching.
 */
public abstract class KsNode {

    
    // Statement nodes
    

    /** Base class for all statement nodes. */
    public abstract static class Stmt extends KsNode {}

    /** {@code var = expr} */
    public static final class Assign extends Stmt {
        public final String   name;
        public final Expr     value;
        public Assign(String name, Expr value) { this.name = name; this.value = value; }
    }

    /** {@code var += expr} / {@code -=} / {@code *=} / {@code /=} */
    public static final class CompoundAssign extends Stmt {
        public final String name;
        public final String op;   // "+=", "-=", "*=", "/="
        public final Expr   value;
        public CompoundAssign(String name, String op, Expr value) {
            this.name = name; this.op = op; this.value = value;
        }
    }

    /**
     * {@code if cond { then } [elif cond { then }]* [else { else }]}
     *
     * {@code conditions[i]} pairs with {@code bodies[i]}.
     * {@code elseBody} may be null.
     */
    public static final class If extends Stmt {
        public final List<Expr>         conditions;
        public final List<List<Stmt>>   bodies;
        public final List<Stmt>         elseBody;   // null if absent
        public If(List<Expr> conditions, List<List<Stmt>> bodies, List<Stmt> elseBody) {
            this.conditions = conditions; this.bodies = bodies; this.elseBody = elseBody;
        }
    }

    /** {@code while cond { body }} */
    public static final class While extends Stmt {
        public final Expr       condition;
        public final List<Stmt> body;
        public While(Expr condition, List<Stmt> body) {
            this.condition = condition; this.body = body;
        }
    }

    /** {@code for var in iterable { body }} */
    public static final class For extends Stmt {
        public final String     variable;
        public final Expr       iterable;
        public final List<Stmt> body;
        public For(String variable, Expr iterable, List<Stmt> body) {
            this.variable = variable; this.iterable = iterable; this.body = body;
        }
    }

    /** {@code return [expr]} - value is null if bare return */
    public static final class Return extends Stmt {
        public final Expr value;  // may be null
        public Return(Expr value) { this.value = value; }
    }

    /** {@code break} */
    public static final class Break extends Stmt {}

    /** {@code continue} */
    public static final class Continue extends Stmt {}

    /** {@code print expr} - sends a message to the player */
    public static final class Print extends Stmt {
        public final Expr value;
        public Print(Expr value) { this.value = value; }
    }

    /** {@code try { tryBody } catch (errVar) { catchBody }} */
    public static final class TryCatch extends Stmt {
        public final List<Stmt> tryBody;
        public final String     errVar;
        public final List<Stmt> catchBody;
        public TryCatch(List<Stmt> tryBody, String errVar, List<Stmt> catchBody) {
            this.tryBody = tryBody; this.errVar = errVar; this.catchBody = catchBody;
        }
    }

    /** {@code throw expr} */
    public static final class Throw extends Stmt {
        public final Expr value;
        public Throw(Expr value) { this.value = value; }
    }

    /** {@code def name(params) { body }} */
    public static final class FuncDef extends Stmt {
        public final String       name;
        public final List<String> params;
        public final List<Stmt>   body;
        public FuncDef(String name, List<String> params, List<Stmt> body) {
            this.name = name; this.params = params; this.body = body;
        }
    }

    /** An expression used as a statement (e.g. a function call). */
    public static final class ExprStmt extends Stmt {
        public final Expr expr;
        public ExprStmt(Expr expr) { this.expr = expr; }
    }

    
    // Expression nodes
    

    /** Base class for all expression nodes. */
    public abstract static class Expr extends KsNode {}

    /** A numeric literal: {@code 42} or {@code 3.14} */
    public static final class NumLit extends Expr {
        public final double value;
        public NumLit(double value) { this.value = value; }
    }

    /**
     * A string literal: {@code "hello"} or {@code 'world'}.
     * May contain {@code {$var}} interpolation placeholders - resolved at eval time.
     */
    public static final class StrLit extends Expr {
        public final String value;
        public StrLit(String value) { this.value = value; }
    }

    /** A boolean literal: {@code true} or {@code false} */
    public static final class BoolLit extends Expr {
        public final boolean value;
        public BoolLit(boolean value) { this.value = value; }
    }

    /** The {@code null} literal */
    public static final class NullLit extends Expr {}

    /** A variable reference: {@code x} */
    public static final class Ident extends Expr {
        public final String name;
        public Ident(String name) { this.name = name; }
    }

    /** A binary operation: {@code a + b}, {@code x == y}, etc. */
    public static final class BinOp extends Expr {
        public final Expr   left;
        public final String op;
        public final Expr   right;
        public BinOp(Expr left, String op, Expr right) {
            this.left = left; this.op = op; this.right = right;
        }
    }

    /** A unary operation: {@code -x}, {@code not b} */
    public static final class UnaryOp extends Expr {
        public final String op;
        public final Expr   operand;
        public UnaryOp(String op, Expr operand) { this.op = op; this.operand = operand; }
    }

    /** A function call: {@code foo(a, b)} */
    public static final class Call extends Expr {
        public final Expr       callee;
        public final List<Expr> args;
        public Call(Expr callee, List<Expr> args) { this.callee = callee; this.args = args; }
    }

    /** A method call: {@code obj.method(args)} */
    public static final class MethodCall extends Expr {
        public final Expr       object;
        public final String     method;
        public final List<Expr> args;
        public MethodCall(Expr object, String method, List<Expr> args) {
            this.object = object; this.method = method; this.args = args;
        }
    }

    /** Index access: {@code list[i]} or {@code dict["key"]} */
    public static final class Index extends Expr {
        public final Expr object;
        public final Expr index;
        public Index(Expr object, Expr index) { this.object = object; this.index = index; }
    }

    /** Member (field) access: {@code dict.key} (non-call) */
    public static final class MemberAccess extends Expr {
        public final Expr   object;
        public final String member;
        public MemberAccess(Expr object, String member) {
            this.object = object; this.member = member;
        }
    }

    /** A list literal: {@code [1, 2, 3]} */
    public static final class ListLit extends Expr {
        public final List<Expr> elements;
        public ListLit(List<Expr> elements) { this.elements = elements; }
    }

    /** A dict literal: {@code {"key": value}} */
    public static final class DictLit extends Expr {
        public final List<Expr> keys;
        public final List<Expr> values;
        public DictLit(List<Expr> keys, List<Expr> values) {
            this.keys = keys; this.values = values;
        }
    }
}
