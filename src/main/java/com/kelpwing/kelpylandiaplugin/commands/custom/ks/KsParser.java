package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for KS Lite.
 *
 * Consumes a {@link List} of {@link KsToken} and produces a flat list of
 * {@link KsNode.Stmt} (the program body).
 *
 * <h3>Grammar summary</h3>
 * <pre>
 * program     = stmt*
 * stmt        = funcDef | if | while | for | return | break | continue
 *             | print | tryCatch | throw | assignment | compoundAssign | exprStmt
 * block       = '{' stmt* '}'
 * funcDef     = 'def' IDENT '(' params ')' block
 * if          = 'if' expr block ( 'elif' expr block )* ( 'else' block )?
 * while       = 'while' expr block
 * for         = 'for' IDENT 'in' expr block
 * return      = 'return' expr?
 * tryCatch    = 'try' block 'catch' '(' IDENT ')' block
 * throw       = 'throw' expr
 * assignment  = IDENT '=' expr
 * compound    = IDENT ( '+=' | '-=' | '*=' | '/=' ) expr
 * exprStmt    = expr
 *
 * expr        = or
 * or          = and ( 'or' and )*
 * and         = not ( 'and' not )*
 * not         = 'not' not | comparison
 * comparison  = addSub ( ( '==' | '!=' | '<' | '<=' | '>' | '>=' ) addSub )?
 * addSub      = mulDiv ( ( '+' | '-' ) mulDiv )*
 * mulDiv      = unary  ( ( '*' | '/' | '%' ) unary  )*
 * unary       = ( '-' | 'not' ) unary | postfix
 * postfix     = primary ( '.' IDENT ('(' args ')')? | '[' expr ']' | '(' args ')' )*
 * primary     = NUMBER | STRING | BOOL | NULL | IDENT | '(' expr ')'
 *             | '[' ( expr ( ',' expr )* ','? )? ']'
 *             | '{' ( expr ':' expr ( ',' expr ':' expr )* ','? )? '}'
 * </pre>
 */
public final class KsParser {

    private final List<KsToken> tokens;
    private int pos = 0;

    public KsParser(List<KsToken> tokens) {
        this.tokens = tokens;
    }

        //  Public API
    
    /** Parse the token list into a program (list of statements). */
    public List<KsNode.Stmt> parse() {
        List<KsNode.Stmt> stmts = new ArrayList<>();
        while (!check(KsToken.Type.EOF)) {
            stmts.add(parseStmt());
        }
        return stmts;
    }

        //  Token helpers
    
    private KsToken peek()           { return tokens.get(pos); }
    private KsToken peekAt(int off)  {
        int i = pos + off;
        return i < tokens.size() ? tokens.get(i) : tokens.get(tokens.size() - 1);
    }
    private boolean check(KsToken.Type t) { return peek().type == t; }
    private KsToken consume()             { return tokens.get(pos++); }

    private KsToken expect(KsToken.Type t) {
        if (!check(t)) {
            KsToken got = peek();
            throw new KsException.Runtime(
                "Expected " + t + " but got '" + got.value + "' at line " + got.line);
        }
        return consume();
    }

        //  Statement parsers
    
    private KsNode.Stmt parseStmt() {
        KsToken t = peek();
        switch (t.type) {
            case DEF:      return parseFuncDef();
            case IF:       return parseIf();
            case WHILE:    return parseWhile();
            case FOR:      return parseFor();
            case RETURN:   return parseReturn();
            case BREAK:    consume(); return new KsNode.Break();
            case CONTINUE: consume(); return new KsNode.Continue();
            case PRINT:    consume(); return new KsNode.Print(parseExpr());
            case TRY:      return parseTryCatch();
            case THROW:    consume(); return new KsNode.Throw(parseExpr());
            case IDENT: {
                // Lookahead: assignment or compound-assign vs expression statement
                KsToken.Type next = peekAt(1).type;
                if (next == KsToken.Type.ASSIGN) {
                    String name = consume().value;
                    consume(); // =
                    return new KsNode.Assign(name, parseExpr());
                }
                if (next == KsToken.Type.PLUS_EQ || next == KsToken.Type.MINUS_EQ
                        || next == KsToken.Type.STAR_EQ || next == KsToken.Type.SLASH_EQ) {
                    String name = consume().value;
                    String op   = consume().value;
                    return new KsNode.CompoundAssign(name, op, parseExpr());
                }
                // Fall through to expression statement
                return new KsNode.ExprStmt(parseExpr());
            }
            default:
                return new KsNode.ExprStmt(parseExpr());
        }
    }

    /** Parse {@code { stmt* }}. */
    private List<KsNode.Stmt> parseBlock() {
        expect(KsToken.Type.LBRACE);
        List<KsNode.Stmt> body = new ArrayList<>();
        while (!check(KsToken.Type.RBRACE) && !check(KsToken.Type.EOF)) {
            body.add(parseStmt());
        }
        expect(KsToken.Type.RBRACE);
        return body;
    }

    /** {@code def name(p1, p2) { body }} */
    private KsNode.Stmt parseFuncDef() {
        consume(); // def
        String name = expect(KsToken.Type.IDENT).value;
        expect(KsToken.Type.LPAREN);
        List<String> params = new ArrayList<>();
        if (!check(KsToken.Type.RPAREN)) {
            params.add(expect(KsToken.Type.IDENT).value);
            while (check(KsToken.Type.COMMA)) {
                consume();
                params.add(expect(KsToken.Type.IDENT).value);
            }
        }
        expect(KsToken.Type.RPAREN);
        return new KsNode.FuncDef(name, params, parseBlock());
    }

    /** {@code if cond { } [elif cond { }]* [else { }]} */
    private KsNode.Stmt parseIf() {
        consume(); // if
        List<KsNode.Expr>       conditions = new ArrayList<>();
        List<List<KsNode.Stmt>> bodies     = new ArrayList<>();
        conditions.add(parseExpr());
        bodies.add(parseBlock());
        while (check(KsToken.Type.ELIF)) {
            consume();
            conditions.add(parseExpr());
            bodies.add(parseBlock());
        }
        List<KsNode.Stmt> elseBody = null;
        if (check(KsToken.Type.ELSE)) {
            consume();
            elseBody = parseBlock();
        }
        return new KsNode.If(conditions, bodies, elseBody);
    }

    /** {@code while cond { body }} */
    private KsNode.Stmt parseWhile() {
        consume(); // while
        KsNode.Expr cond = parseExpr();
        return new KsNode.While(cond, parseBlock());
    }

    /** {@code for var in iterable { body }} */
    private KsNode.Stmt parseFor() {
        consume(); // for
        String var = expect(KsToken.Type.IDENT).value;
        expect(KsToken.Type.IN);
        KsNode.Expr iterable = parseExpr();
        return new KsNode.For(var, iterable, parseBlock());
    }

    /**
     * {@code return [expr]}
     * The expression is optional — if the very next token cannot start an
     * expression, a bare {@code return} (returns null) is parsed.
     */
    private KsNode.Stmt parseReturn() {
        consume(); // return
        if (canStartExpr()) return new KsNode.Return(parseExpr());
        return new KsNode.Return(null);
    }

    /** {@code try { body } catch (errVar) { handler }} */
    private KsNode.Stmt parseTryCatch() {
        consume(); // try
        List<KsNode.Stmt> tryBody = parseBlock();
        expect(KsToken.Type.CATCH);
        expect(KsToken.Type.LPAREN);
        String errVar = expect(KsToken.Type.IDENT).value;
        expect(KsToken.Type.RPAREN);
        List<KsNode.Stmt> catchBody = parseBlock();
        return new KsNode.TryCatch(tryBody, errVar, catchBody);
    }

    /** Returns true if the current token can begin an expression. */
    private boolean canStartExpr() {
        switch (peek().type) {
            case NUMBER: case STRING: case BOOL_LIT: case NULL_LIT:
            case IDENT:  case LPAREN: case LBRACKET: case LBRACE:
            case MINUS:  case NOT:
                return true;
            default:
                return false;
        }
    }

        //  Expression parsers (precedence, low → high)
    
    private KsNode.Expr parseExpr()       { return parseOr(); }

    private KsNode.Expr parseOr() {
        KsNode.Expr left = parseAnd();
        while (check(KsToken.Type.OR)) {
            consume();
            left = new KsNode.BinOp(left, "or", parseAnd());
        }
        return left;
    }

    private KsNode.Expr parseAnd() {
        KsNode.Expr left = parseNot();
        while (check(KsToken.Type.AND)) {
            consume();
            left = new KsNode.BinOp(left, "and", parseNot());
        }
        return left;
    }

    private KsNode.Expr parseNot() {
        if (check(KsToken.Type.NOT)) { consume(); return new KsNode.UnaryOp("not", parseNot()); }
        return parseComparison();
    }

    private KsNode.Expr parseComparison() {
        KsNode.Expr left = parseAddSub();
        KsToken t = peek();
        switch (t.type) {
            case EQ: case NEQ: case LT: case LE: case GT: case GE:
                consume();
                return new KsNode.BinOp(left, t.value, parseAddSub());
            default:
                return left;
        }
    }

    private KsNode.Expr parseAddSub() {
        KsNode.Expr left = parseMulDiv();
        while (check(KsToken.Type.PLUS) || check(KsToken.Type.MINUS)) {
            String op = consume().value;
            left = new KsNode.BinOp(left, op, parseMulDiv());
        }
        return left;
    }

    private KsNode.Expr parseMulDiv() {
        KsNode.Expr left = parseUnary();
        while (check(KsToken.Type.STAR) || check(KsToken.Type.SLASH) || check(KsToken.Type.PERCENT)) {
            String op = consume().value;
            left = new KsNode.BinOp(left, op, parseUnary());
        }
        return left;
    }

    private KsNode.Expr parseUnary() {
        if (check(KsToken.Type.MINUS)) { consume(); return new KsNode.UnaryOp("-",   parseUnary()); }
        if (check(KsToken.Type.NOT))   { consume(); return new KsNode.UnaryOp("not", parseUnary()); }
        return parsePostfix();
    }

    /**
     * Postfix: handles {@code .method(args)}, {@code .field}, {@code [index]},
     * and {@code (args)} (function call on any expression).
     */
    private KsNode.Expr parsePostfix() {
        KsNode.Expr expr = parsePrimary();
        while (true) {
            if (check(KsToken.Type.DOT)) {
                consume();
                String member = expect(KsToken.Type.IDENT).value;
                if (check(KsToken.Type.LPAREN)) {
                    consume();
                    List<KsNode.Expr> args = parseArgList();
                    expect(KsToken.Type.RPAREN);
                    expr = new KsNode.MethodCall(expr, member, args);
                } else {
                    expr = new KsNode.MemberAccess(expr, member);
                }
            } else if (check(KsToken.Type.LBRACKET)) {
                consume();
                KsNode.Expr idx = parseExpr();
                expect(KsToken.Type.RBRACKET);
                expr = new KsNode.Index(expr, idx);
            } else if (check(KsToken.Type.LPAREN)) {
                // Function call: callee can be any expression that resolved to a function
                consume();
                List<KsNode.Expr> args = parseArgList();
                expect(KsToken.Type.RPAREN);
                expr = new KsNode.Call(expr, args);
            } else {
                break;
            }
        }
        return expr;
    }

    /** Parses a comma-separated argument list, stopping before the closing ')'. */
    private List<KsNode.Expr> parseArgList() {
        List<KsNode.Expr> args = new ArrayList<>();
        if (!check(KsToken.Type.RPAREN)) {
            args.add(parseExpr());
            while (check(KsToken.Type.COMMA)) {
                consume();
                if (check(KsToken.Type.RPAREN)) break; // trailing comma
                args.add(parseExpr());
            }
        }
        return args;
    }

    /** Primary expressions: literals, identifiers, grouped expressions, list/dict literals. */
    private KsNode.Expr parsePrimary() {
        KsToken t = peek();
        switch (t.type) {
            case NUMBER:
                consume();
                return new KsNode.NumLit(Double.parseDouble(t.value));
            case STRING:
                consume();
                return new KsNode.StrLit(t.value);
            case BOOL_LIT:
                consume();
                return new KsNode.BoolLit("true".equals(t.value));
            case NULL_LIT:
                consume();
                return new KsNode.NullLit();
            case IDENT:
                consume();
                return new KsNode.Ident(t.value);
            case LPAREN: {
                consume();
                KsNode.Expr inner = parseExpr();
                expect(KsToken.Type.RPAREN);
                return inner;
            }
            case LBRACKET: {
                consume();
                List<KsNode.Expr> elems = new ArrayList<>();
                if (!check(KsToken.Type.RBRACKET)) {
                    elems.add(parseExpr());
                    while (check(KsToken.Type.COMMA)) {
                        consume();
                        if (check(KsToken.Type.RBRACKET)) break;
                        elems.add(parseExpr());
                    }
                }
                expect(KsToken.Type.RBRACKET);
                return new KsNode.ListLit(elems);
            }
            case LBRACE: {
                consume();
                List<KsNode.Expr> keys = new ArrayList<>();
                List<KsNode.Expr> vals = new ArrayList<>();
                if (!check(KsToken.Type.RBRACE)) {
                    keys.add(parseExpr());
                    expect(KsToken.Type.COLON);
                    vals.add(parseExpr());
                    while (check(KsToken.Type.COMMA)) {
                        consume();
                        if (check(KsToken.Type.RBRACE)) break;
                        keys.add(parseExpr());
                        expect(KsToken.Type.COLON);
                        vals.add(parseExpr());
                    }
                }
                expect(KsToken.Type.RBRACE);
                return new KsNode.DictLit(keys, vals);
            }
            default:
                throw new KsException.Runtime(
                    "Unexpected token '" + t.value + "' (" + t.type + ") at line " + t.line);
        }
    }
}
