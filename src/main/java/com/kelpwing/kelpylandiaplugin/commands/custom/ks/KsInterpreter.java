package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tree-walking interpreter for KS Lite.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * KsInterpreter ks = new KsInterpreter(msg -> player.sendMessage(msg));
 * // Inject Minecraft-specific built-ins:
 * ks.registerNative("argument", args -> KsValue.ofStr(cmdArgs[(int) args.get(0).asNum() - 1]));
 * // Then run the script (may throw KsException.Return or KsException.Runtime):
 * ks.exec(source);
 * }</pre>
 *
 * <h3>Built-in functions registered in the constructor</h3>
 * <ul>
 *   <li>{@code str(v)}, {@code num(v)}, {@code int(v)}, {@code bool(v)}, {@code type(v)}</li>
 *   <li>{@code len(v)} - string / list / dict</li>
 *   <li>{@code upper(s)}, {@code lower(s)}, {@code trim(s)}</li>
 *   <li>{@code contains(collection, item)}</li>
 *   <li>{@code range(stop)}, {@code range(start, stop)}, {@code range(start, stop, step)}</li>
 *   <li>{@code random(min, max)} - inclusive random integer</li>
 *   <li>{@code abs(n)}, {@code floor(n)}, {@code ceil(n)}, {@code round(n)}</li>
 *   <li>{@code min(a, b)}, {@code max(a, b)}, {@code pow(base, exp)}, {@code sqrt(n)}</li>
 *   <li>{@code colorize(s)} - translates {@code &} colour codes to §</li>
 *   <li>{@code strip_colors(s)} - removes § colour sequences from a string</li>
 * </ul>
 *
 * <h3>String interpolation</h3>
 * String literals containing {@code {$varName}} are resolved against the current
 * environment at evaluation time - no special AST node needed.
 *
 * <h3>Method calls</h3>
 * String, list, and dict values support dot-method syntax matching the KelpyShark
 * standard library reference (e.g. {@code s.upper()}, {@code list.append(x)}).
 */
public final class KsInterpreter {

        // Regex for string interpolation: {$varName}
        private static final Pattern INTERP = Pattern.compile("\\{\\$([A-Za-z_][A-Za-z0-9_]*)\\}");

        // State
        private final KsEnvironment env     = new KsEnvironment();
    private final Consumer<String> print;   // where `print` statements send output
    private final Random rng             = new Random();

    /**
     * Create an interpreter.
     *
     * @param printHandler called whenever a {@code print} statement (or any statement
     *                     that emits a message) executes; typically {@code player::sendMessage}.
     */
    public KsInterpreter(Consumer<String> printHandler) {
        this.print = printHandler;
        registerStdlib();
    }

        // Public API
    
    /**
     * Register (or overwrite) a native built-in function by name.
     * The {@code Function} receives a {@link List} of evaluated argument values.
     * It should throw {@link KsException.Runtime} on type errors, not return null.
     *
     * @param name the function name visible inside scripts
     * @param fn   the Java implementation
     */
    public void registerNative(String name, Function<List<KsValue>, KsValue> fn) {
        env.set(name, KsValue.ofNative(name, fn));
    }

    /**
     * Execute a KS Lite script given as a source string.
     *
     * @throws KsException.Return  if a top-level {@code return} statement was hit
     *                             (value available in the exception)
     * @throws KsException.Runtime on script runtime errors
     */
    public void exec(String source) {
        List<KsToken> tokens = new KsLexer(source).tokenize();
        List<KsNode.Stmt> program = new KsParser(tokens).parse();
        execBlock(program);
    }

    /**
     * Execute a pre-parsed program (list of statements).
     * Same throw behaviour as {@link #exec(String)}.
     */
    public void execParsed(List<KsNode.Stmt> program) {
        execBlock(program);
    }

        // Statement execution
    
    /** Execute a list of statements in the current scope. */
    private void execBlock(List<KsNode.Stmt> stmts) {
        for (KsNode.Stmt s : stmts) execStmt(s);
    }

    private void execStmt(KsNode.Stmt stmt) {
        if (stmt instanceof KsNode.Assign) {
            KsNode.Assign a = (KsNode.Assign) stmt;
            env.update(a.name, evalExpr(a.value));

        } else if (stmt instanceof KsNode.CompoundAssign) {
            KsNode.CompoundAssign ca = (KsNode.CompoundAssign) stmt;
            KsValue cur = requireVar(ca.name);
            KsValue rhs = evalExpr(ca.value);
            KsValue result;
            switch (ca.op) {
                case "+=": result = applyAdd(cur, rhs);  break;
                case "-=": result = applyArith("-", cur, rhs); break;
                case "*=": result = applyArith("*", cur, rhs); break;
                case "/=": result = applyArith("/", cur, rhs); break;
                default: throw new KsException.Runtime("Unknown compound op: " + ca.op);
            }
            env.update(ca.name, result);

        } else if (stmt instanceof KsNode.If) {
            KsNode.If ifs = (KsNode.If) stmt;
            boolean done = false;
            for (int i = 0; i < ifs.conditions.size(); i++) {
                if (evalExpr(ifs.conditions.get(i)).isTruthy()) {
                    execScopedBlock(ifs.bodies.get(i));
                    done = true;
                    break;
                }
            }
            if (!done && ifs.elseBody != null) execScopedBlock(ifs.elseBody);

        } else if (stmt instanceof KsNode.While) {
            KsNode.While w = (KsNode.While) stmt;
            while (evalExpr(w.condition).isTruthy()) {
                try {
                    execScopedBlock(w.body);
                } catch (KsException.Break ignored) {
                    break;
                } catch (KsException.Continue ignored) {
                    // continue to next iteration
                }
            }

        } else if (stmt instanceof KsNode.For) {
            KsNode.For f = (KsNode.For) stmt;
            KsValue iter = evalExpr(f.iterable);
            if (iter.type != KsValue.Type.LIST)
                throw new KsException.Runtime("for-loop: cannot iterate over " + iter.type);
            for (KsValue item : iter.asList()) {
                try {
                    env.push();
                    env.set(f.variable, item);
                    execBlock(f.body);
                    env.pop();
                } catch (KsException.Break ignored) {
                    env.pop();
                    break;
                } catch (KsException.Continue ignored) {
                    env.pop();
                    // next iteration
                }
            }

        } else if (stmt instanceof KsNode.Return) {
            KsNode.Return r = (KsNode.Return) stmt;
            KsValue val = r.value != null ? evalExpr(r.value) : KsValue.NULL;
            throw new KsException.Return(val);

        } else if (stmt instanceof KsNode.Break) {
            throw KsException.Break.INSTANCE;

        } else if (stmt instanceof KsNode.Continue) {
            throw KsException.Continue.INSTANCE;

        } else if (stmt instanceof KsNode.Print) {
            KsNode.Print p = (KsNode.Print) stmt;
            print.accept(evalExpr(p.value).toString());

        } else if (stmt instanceof KsNode.TryCatch) {
            KsNode.TryCatch tc = (KsNode.TryCatch) stmt;
            try {
                execScopedBlock(tc.tryBody);
            } catch (KsException.Throw t) {
                env.push();
                env.set(tc.errVar, t.value);
                execBlock(tc.catchBody);
                env.pop();
            } catch (KsException.Runtime re) {
                env.push();
                env.set(tc.errVar, KsValue.ofStr(re.getMessage() != null ? re.getMessage() : "error"));
                execBlock(tc.catchBody);
                env.pop();
            }

        } else if (stmt instanceof KsNode.Throw) {
            KsNode.Throw t = (KsNode.Throw) stmt;
            throw new KsException.Throw(evalExpr(t.value));

        } else if (stmt instanceof KsNode.FuncDef) {
            KsNode.FuncDef fd = (KsNode.FuncDef) stmt;
            env.set(fd.name, KsValue.ofFn(fd.name, fd.params, fd.body));

        } else if (stmt instanceof KsNode.ExprStmt) {
            evalExpr(((KsNode.ExprStmt) stmt).expr); // side effects only

        } else {
            throw new KsException.Runtime("Unknown statement type: " + stmt.getClass().getSimpleName());
        }
    }

    /** Execute a block in a fresh inner scope. */
    private void execScopedBlock(List<KsNode.Stmt> stmts) {
        env.push();
        try {
            execBlock(stmts);
        } finally {
            env.pop();
        }
    }

        // Expression evaluation
    
    public KsValue evalExpr(KsNode.Expr expr) {
        if (expr instanceof KsNode.NumLit) {
            return KsValue.ofNum(((KsNode.NumLit) expr).value);

        } else if (expr instanceof KsNode.StrLit) {
            return KsValue.ofStr(interpolate(((KsNode.StrLit) expr).value));

        } else if (expr instanceof KsNode.BoolLit) {
            return KsValue.ofBool(((KsNode.BoolLit) expr).value);

        } else if (expr instanceof KsNode.NullLit) {
            return KsValue.NULL;

        } else if (expr instanceof KsNode.Ident) {
            String name = ((KsNode.Ident) expr).name;
            KsValue v = env.get(name);
            if (v == null) throw new KsException.Runtime("Undefined variable: '" + name + "'");
            return v;

        } else if (expr instanceof KsNode.BinOp) {
            KsNode.BinOp bo = (KsNode.BinOp) expr;
            // Short-circuit for and/or
            if ("and".equals(bo.op)) {
                KsValue l = evalExpr(bo.left);
                return l.isTruthy() ? evalExpr(bo.right) : KsValue.FALSE;
            }
            if ("or".equals(bo.op)) {
                KsValue l = evalExpr(bo.left);
                return l.isTruthy() ? l : evalExpr(bo.right);
            }
            KsValue left  = evalExpr(bo.left);
            KsValue right = evalExpr(bo.right);
            return evalBinOp(bo.op, left, right);

        } else if (expr instanceof KsNode.UnaryOp) {
            KsNode.UnaryOp uo = (KsNode.UnaryOp) expr;
            KsValue v = evalExpr(uo.operand);
            if ("-".equals(uo.op)) {
                if (v.type != KsValue.Type.NUMBER)
                    throw new KsException.Runtime("Unary minus: expected number, got " + v.type);
                return KsValue.ofNum(-v.asNum());
            }
            if ("not".equals(uo.op)) return KsValue.ofBool(!v.isTruthy());
            throw new KsException.Runtime("Unknown unary op: " + uo.op);

        } else if (expr instanceof KsNode.Call) {
            KsNode.Call c = (KsNode.Call) expr;
            KsValue callee = evalExpr(c.callee);
            List<KsValue> args = evalArgs(c.args);
            return callValue(callee, args);

        } else if (expr instanceof KsNode.MethodCall) {
            KsNode.MethodCall mc = (KsNode.MethodCall) expr;
            KsValue obj  = evalExpr(mc.object);
            List<KsValue> args = evalArgs(mc.args);
            return callMethod(obj, mc.method, args);

        } else if (expr instanceof KsNode.Index) {
            KsNode.Index ix = (KsNode.Index) expr;
            KsValue obj = evalExpr(ix.object);
            KsValue idx = evalExpr(ix.index);
            return evalIndex(obj, idx);

        } else if (expr instanceof KsNode.MemberAccess) {
            KsNode.MemberAccess ma = (KsNode.MemberAccess) expr;
            KsValue obj = evalExpr(ma.object);
            return evalMember(obj, ma.member);

        } else if (expr instanceof KsNode.ListLit) {
            List<KsValue> items = new ArrayList<>();
            for (KsNode.Expr e : ((KsNode.ListLit) expr).elements) items.add(evalExpr(e));
            return KsValue.ofList(items);

        } else if (expr instanceof KsNode.DictLit) {
            KsNode.DictLit dl = (KsNode.DictLit) expr;
            Map<String, KsValue> map = new LinkedHashMap<>();
            for (int i = 0; i < dl.keys.size(); i++) {
                String k = evalExpr(dl.keys.get(i)).toString();
                map.put(k, evalExpr(dl.values.get(i)));
            }
            return KsValue.ofDict(map);

        } else {
            throw new KsException.Runtime("Unknown expression type: " + expr.getClass().getSimpleName());
        }
    }

        // Binary operators
    
    private KsValue evalBinOp(String op, KsValue l, KsValue r) {
        switch (op) {
            case "+":  return applyAdd(l, r);
            case "-":  return applyArith("-", l, r);
            case "*":  return applyArith("*", l, r);
            case "/":  return applyArith("/", l, r);
            case "%":  return applyArith("%", l, r);
            case "==": return KsValue.ofBool(l.equals(r));
            case "!=": return KsValue.ofBool(!l.equals(r));
            case "<":  return KsValue.ofBool(compare(l, r) < 0);
            case "<=": return KsValue.ofBool(compare(l, r) <= 0);
            case ">":  return KsValue.ofBool(compare(l, r) > 0);
            case ">=": return KsValue.ofBool(compare(l, r) >= 0);
            default:   throw new KsException.Runtime("Unknown operator: " + op);
        }
    }

    private KsValue applyAdd(KsValue l, KsValue r) {
        if (l.type == KsValue.Type.NUMBER && r.type == KsValue.Type.NUMBER)
            return KsValue.ofNum(l.asNum() + r.asNum());
        // String coercion: any + string or string + any
        return KsValue.ofStr(l.toString() + r.toString());
    }

    private KsValue applyArith(String op, KsValue l, KsValue r) {
        if (l.type != KsValue.Type.NUMBER || r.type != KsValue.Type.NUMBER)
            throw new KsException.Runtime(
                "Operator '" + op + "' requires numbers, got " + l.type + " and " + r.type);
        double a = l.asNum(), b = r.asNum();
        switch (op) {
            case "-": return KsValue.ofNum(a - b);
            case "*": return KsValue.ofNum(a * b);
            case "/": if (b == 0) throw new KsException.Runtime("Division by zero");
                      return KsValue.ofNum(a / b);
            case "%": if (b == 0) throw new KsException.Runtime("Modulo by zero");
                      return KsValue.ofNum(a % b);
            default:  throw new KsException.Runtime("Unknown arith op: " + op);
        }
    }

    private int compare(KsValue l, KsValue r) {
        if (l.type == KsValue.Type.NUMBER && r.type == KsValue.Type.NUMBER)
            return Double.compare(l.asNum(), r.asNum());
        if (l.type == KsValue.Type.STRING && r.type == KsValue.Type.STRING)
            return l.asStr().compareTo(r.asStr());
        throw new KsException.Runtime("Cannot compare " + l.type + " and " + r.type);
    }

        // Function / method dispatch
    
    private KsValue callValue(KsValue callee, List<KsValue> args) {
        if (callee.type == KsValue.Type.NATIVE) {
            return callee.asNative().fn.apply(args);
        }
        if (callee.type == KsValue.Type.FUNCTION) {
            KsValue.KsFn fn = callee.asFn();
            if (args.size() != fn.params.size())
                throw new KsException.Runtime("Function '" + fn.name + "' expects " +
                    fn.params.size() + " args, got " + args.size());
            env.push();
            for (int i = 0; i < fn.params.size(); i++) env.set(fn.params.get(i), args.get(i));
            KsValue result = KsValue.NULL;
            try {
                execBlock(fn.body);
            } catch (KsException.Return ret) {
                result = ret.value;
            } finally {
                env.pop();
            }
            return result;
        }
        throw new KsException.Runtime("Not a function: " + callee.type);
    }

    /** Dispatch a method call on a built-in type. */
    private KsValue callMethod(KsValue obj, String method, List<KsValue> args) {
        switch (obj.type) {
            case STRING:  return callStringMethod(obj.asStr(), method, args);
            case LIST:    return callListMethod(obj.asList(), method, args);
            case DICT:    return callDictMethod(obj.asDict(), method, args);
            default:
                throw new KsException.Runtime(obj.type + " has no method '" + method + "'");
        }
    }

    // String methods 

    private KsValue callStringMethod(String s, String method, List<KsValue> args) {
        switch (method) {
            case "upper":       return KsValue.ofStr(s.toUpperCase());
            case "lower":       return KsValue.ofStr(s.toLowerCase());
            case "trim":        return KsValue.ofStr(s.trim());
            case "reverse":     return KsValue.ofStr(new StringBuilder(s).reverse().toString());
            case "len": case "length": return KsValue.ofNum(s.length());
            case "to_num": case "to_number":
                try { return KsValue.ofNum(Double.parseDouble(s)); }
                catch (NumberFormatException e) { throw new KsException.Runtime("Cannot parse '" + s + "' as number"); }
            case "split": {
                String sep = args.isEmpty() ? " " : args.get(0).toString();
                String[] parts = s.split(Pattern.quote(sep), -1);
                List<KsValue> list = new ArrayList<>();
                for (String p : parts) list.add(KsValue.ofStr(p));
                return KsValue.ofList(list);
            }
            case "contains":    return KsValue.ofBool(s.contains(str(args, 0, "contains")));
            case "starts_with": return KsValue.ofBool(s.startsWith(str(args, 0, "starts_with")));
            case "ends_with":   return KsValue.ofBool(s.endsWith(str(args, 0, "ends_with")));
            case "replace": {
                String from = str(args, 0, "replace");
                String to   = str(args, 1, "replace");
                return KsValue.ofStr(s.replace(from, to));
            }
            case "char_at": {
                int idx = (int) num(args, 0, "char_at");
                if (idx < 0 || idx >= s.length())
                    throw new KsException.Runtime("char_at: index " + idx + " out of bounds");
                return KsValue.ofStr(String.valueOf(s.charAt(idx)));
            }
            case "substring": {
                int start = (int) num(args, 0, "substring");
                int end   = (int) num(args, 1, "substring");
                if (start < 0 || end > s.length() || start > end)
                    throw new KsException.Runtime("substring: invalid range " + start + ".." + end);
                return KsValue.ofStr(s.substring(start, end));
            }
            case "index_of":     return KsValue.ofNum(s.indexOf(str(args, 0, "index_of")));
            default:
                throw new KsException.Runtime("String has no method '" + method + "'");
        }
    }

    // List methods 

    private KsValue callListMethod(List<KsValue> list, String method, List<KsValue> args) {
        switch (method) {
            case "len": case "length": case "size":
                return KsValue.ofNum(list.size());
            case "append": case "push": {
                List<KsValue> copy = new ArrayList<>(list);
                copy.add(reqArg(args, 0, "append"));
                return KsValue.ofList(copy);
            }
            case "pop": {
                if (list.isEmpty()) throw new KsException.Runtime("pop: list is empty");
                List<KsValue> copy = new ArrayList<>(list);
                copy.remove(copy.size() - 1);
                return KsValue.ofList(copy);
            }
            case "contains":    return KsValue.ofBool(list.contains(reqArg(args, 0, "contains")));
            case "first":       return list.isEmpty() ? KsValue.NULL : list.get(0);
            case "last":        return list.isEmpty() ? KsValue.NULL : list.get(list.size() - 1);
            case "reverse": {
                List<KsValue> copy = new ArrayList<>(list);
                java.util.Collections.reverse(copy);
                return KsValue.ofList(copy);
            }
            case "sort": {
                List<KsValue> copy = new ArrayList<>(list);
                copy.sort((a, b) -> {
                    if (a.type == KsValue.Type.NUMBER && b.type == KsValue.Type.NUMBER)
                        return Double.compare(a.asNum(), b.asNum());
                    return a.toString().compareTo(b.toString());
                });
                return KsValue.ofList(copy);
            }
            case "join": {
                String sep = args.isEmpty() ? "" : args.get(0).toString();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(sep);
                    sb.append(list.get(i).toString());
                }
                return KsValue.ofStr(sb.toString());
            }
            default:
                throw new KsException.Runtime("List has no method '" + method + "'");
        }
    }

    // Dict methods 

    private KsValue callDictMethod(Map<String, KsValue> dict, String method, List<KsValue> args) {
        switch (method) {
            case "keys": {
                List<KsValue> keys = new ArrayList<>();
                for (String k : dict.keySet()) keys.add(KsValue.ofStr(k));
                return KsValue.ofList(keys);
            }
            case "values": {
                return KsValue.ofList(new ArrayList<>(dict.values()));
            }
            case "items": {
                List<KsValue> pairs = new ArrayList<>();
                for (Map.Entry<String, KsValue> e : dict.entrySet()) {
                    List<KsValue> pair = new ArrayList<>();
                    pair.add(KsValue.ofStr(e.getKey()));
                    pair.add(e.getValue());
                    pairs.add(KsValue.ofList(pair));
                }
                return KsValue.ofList(pairs);
            }
            case "has_key": case "contains":
                return KsValue.ofBool(dict.containsKey(str(args, 0, "has_key")));
            case "len": case "size":
                return KsValue.ofNum(dict.size());
            case "get": {
                String key = str(args, 0, "get");
                KsValue def = args.size() > 1 ? args.get(1) : KsValue.NULL;
                return dict.getOrDefault(key, def);
            }
            default:
                throw new KsException.Runtime("Dict has no method '" + method + "'");
        }
    }

    // Index / member access 

    private KsValue evalIndex(KsValue obj, KsValue idx) {
        if (obj.type == KsValue.Type.LIST) {
            if (idx.type != KsValue.Type.NUMBER)
                throw new KsException.Runtime("List index must be a number");
            int i = (int) idx.asNum();
            List<KsValue> list = obj.asList();
            if (i < 0 || i >= list.size())
                throw new KsException.Runtime("Index " + i + " out of bounds (size " + list.size() + ")");
            return list.get(i);
        }
        if (obj.type == KsValue.Type.DICT) {
            String key = idx.toString();
            KsValue v = obj.asDict().get(key);
            if (v == null) throw new KsException.Runtime("Key '" + key + "' not found in dict");
            return v;
        }
        if (obj.type == KsValue.Type.STRING) {
            int i = (int) idx.asNum();
            String s = obj.asStr();
            if (i < 0 || i >= s.length())
                throw new KsException.Runtime("String index " + i + " out of bounds");
            return KsValue.ofStr(String.valueOf(s.charAt(i)));
        }
        throw new KsException.Runtime("Cannot index " + obj.type);
    }

    private KsValue evalMember(KsValue obj, String member) {
        if (obj.type == KsValue.Type.DICT) {
            KsValue v = obj.asDict().get(member);
            if (v == null) throw new KsException.Runtime("Key '" + member + "' not found in dict");
            return v;
        }
        throw new KsException.Runtime(obj.type + " does not support member access (use method call with parentheses)");
    }

        // String interpolation  "{$varName}"
    
    private String interpolate(String s) {
        if (!s.contains("{$")) return s;
        Matcher m = INTERP.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1);
            KsValue val = env.get(varName);
            String rep = val != null ? val.toString() : "{$" + varName + "}";
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

        // Argument helpers
    
    private List<KsValue> evalArgs(List<KsNode.Expr> exprs) {
        List<KsValue> vals = new ArrayList<>(exprs.size());
        for (KsNode.Expr e : exprs) vals.add(evalExpr(e));
        return vals;
    }

    private KsValue reqArg(List<KsValue> args, int idx, String fn) {
        if (idx >= args.size())
            throw new KsException.Runtime(fn + "() requires at least " + (idx + 1) + " argument(s)");
        return args.get(idx);
    }

    private String str(List<KsValue> args, int idx, String fn) {
        return reqArg(args, idx, fn).toString();
    }

    private double num(List<KsValue> args, int idx, String fn) {
        KsValue v = reqArg(args, idx, fn);
        if (v.type != KsValue.Type.NUMBER)
            throw new KsException.Runtime(fn + "(): arg " + (idx+1) + " must be a number, got " + v.type);
        return v.asNum();
    }

    private KsValue requireVar(String name) {
        KsValue v = env.get(name);
        if (v == null) throw new KsException.Runtime("Undefined variable: '" + name + "'");
        return v;
    }

        // Standard library built-ins
    
    private void registerStdlib() {

        // Type conversion 
        registerNative("str", args -> KsValue.ofStr(reqArg(args, 0, "str").toString()));

        registerNative("num", args -> {
            KsValue v = reqArg(args, 0, "num");
            if (v.type == KsValue.Type.NUMBER) return v;
            if (v.type == KsValue.Type.STRING) {
                try { return KsValue.ofNum(Double.parseDouble(v.asStr())); }
                catch (NumberFormatException e) { throw new KsException.Runtime("num(): cannot parse '" + v.asStr() + "'"); }
            }
            if (v.type == KsValue.Type.BOOLEAN) return KsValue.ofNum(v.asBool() ? 1 : 0);
            throw new KsException.Runtime("num(): cannot convert " + v.type);
        });

        registerNative("int", args -> {
            KsValue v = reqArg(args, 0, "int");
            if (v.type == KsValue.Type.NUMBER) return KsValue.ofNum(Math.floor(v.asNum()));
            if (v.type == KsValue.Type.STRING) {
                try { return KsValue.ofNum(Math.floor(Double.parseDouble(v.asStr()))); }
                catch (NumberFormatException e) { throw new KsException.Runtime("int(): cannot parse '" + v.asStr() + "'"); }
            }
            if (v.type == KsValue.Type.BOOLEAN) return KsValue.ofNum(v.asBool() ? 1 : 0);
            throw new KsException.Runtime("int(): cannot convert " + v.type);
        });

        registerNative("float", args -> {
            KsValue v = reqArg(args, 0, "float");
            if (v.type == KsValue.Type.NUMBER) return v;
            if (v.type == KsValue.Type.STRING) {
                try { return KsValue.ofNum(Double.parseDouble(v.asStr())); }
                catch (NumberFormatException e) { throw new KsException.Runtime("float(): cannot parse '" + v.asStr() + "'"); }
            }
            throw new KsException.Runtime("float(): cannot convert " + v.type);
        });

        registerNative("bool", args -> {
            KsValue v = reqArg(args, 0, "bool");
            return KsValue.ofBool(v.isTruthy());
        });

        registerNative("type", args -> KsValue.ofStr(reqArg(args, 0, "type").type.name().toLowerCase()));

        // Collections 
        registerNative("len", args -> {
            KsValue v = reqArg(args, 0, "len");
            switch (v.type) {
                case STRING: return KsValue.ofNum(v.asStr().length());
                case LIST:   return KsValue.ofNum(v.asList().size());
                case DICT:   return KsValue.ofNum(v.asDict().size());
                default: throw new KsException.Runtime("len(): unsupported type " + v.type);
            }
        });

        registerNative("contains", args -> {
            KsValue col  = reqArg(args, 0, "contains");
            KsValue item = reqArg(args, 1, "contains");
            if (col.type == KsValue.Type.LIST)   return KsValue.ofBool(col.asList().contains(item));
            if (col.type == KsValue.Type.DICT)   return KsValue.ofBool(col.asDict().containsKey(item.toString()));
            if (col.type == KsValue.Type.STRING) return KsValue.ofBool(col.asStr().contains(item.toString()));
            throw new KsException.Runtime("contains(): unsupported type " + col.type);
        });

        // String helpers 
        registerNative("upper", args -> KsValue.ofStr(str(args, 0, "upper").toUpperCase()));
        registerNative("lower", args -> KsValue.ofStr(str(args, 0, "lower").toLowerCase()));
        registerNative("trim",  args -> KsValue.ofStr(str(args, 0, "trim").trim()));

        // Range 
        registerNative("range", args -> {
            double start, stop, step;
            if (args.size() == 1) {
                start = 0; stop = num(args, 0, "range"); step = 1;
            } else if (args.size() == 2) {
                start = num(args, 0, "range"); stop = num(args, 1, "range"); step = 1;
            } else if (args.size() == 3) {
                start = num(args, 0, "range"); stop = num(args, 1, "range"); step = num(args, 2, "range");
                if (step == 0) throw new KsException.Runtime("range(): step cannot be zero");
            } else {
                throw new KsException.Runtime("range() takes 1-3 arguments");
            }
            List<KsValue> list = new ArrayList<>();
            for (double i = start; (step > 0 ? i < stop : i > stop); i += step)
                list.add(KsValue.ofNum(i));
            return KsValue.ofList(list);
        });

        // Math 
        registerNative("random", args -> {
            int min = (int) num(args, 0, "random");
            int max = (int) num(args, 1, "random");
            if (min > max) throw new KsException.Runtime("random(): min > max");
            return KsValue.ofNum(min + rng.nextInt(max - min + 1));
        });

        registerNative("abs",   args -> KsValue.ofNum(Math.abs(num(args, 0, "abs"))));
        registerNative("floor", args -> KsValue.ofNum(Math.floor(num(args, 0, "floor"))));
        registerNative("ceil",  args -> KsValue.ofNum(Math.ceil(num(args, 0, "ceil"))));
        registerNative("round", args -> KsValue.ofNum(Math.round(num(args, 0, "round"))));
        registerNative("sqrt",  args -> {
            double n = num(args, 0, "sqrt");
            if (n < 0) throw new KsException.Runtime("sqrt(): negative argument");
            return KsValue.ofNum(Math.sqrt(n));
        });
        registerNative("pow",   args -> KsValue.ofNum(Math.pow(num(args, 0, "pow"), num(args, 1, "pow"))));
        registerNative("min",   args -> KsValue.ofNum(Math.min(num(args, 0, "min"),  num(args, 1, "min"))));
        registerNative("max",   args -> KsValue.ofNum(Math.max(num(args, 0, "max"),  num(args, 1, "max"))));

        // Colour helpers 
        registerNative("colorize", args -> {
            String s = str(args, 0, "colorize");
            return KsValue.ofStr(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
        });

        registerNative("strip_colors", args -> {
            String s = str(args, 0, "strip_colors");
            return KsValue.ofStr(org.bukkit.ChatColor.stripColor(s));
        });
    }
}
