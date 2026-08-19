package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A runtime value in the KS Lite interpreter.
 *
 * Supports: number (double), string, boolean, null, list, dict,
 * user-defined function ({@link KsFn}), and native (Java) function ({@link KsNative}).
 *
 * All instances are immutable except {@link #asList()} and {@link #asDict()} which
 * return the backing mutable collections - callers must not mutate them externally.
 */
public final class KsValue {

        // Type enum
    
    public enum Type { NUMBER, STRING, BOOLEAN, NULL, LIST, DICT, FUNCTION, NATIVE }

        // Fields
    
    public final Type   type;
    private final Object raw;

    private KsValue(Type type, Object raw) {
        this.type = type;
        this.raw  = raw;
    }

        // Well-known singletons
    
    public static final KsValue NULL  = new KsValue(Type.NULL,    null);
    public static final KsValue TRUE  = new KsValue(Type.BOOLEAN, Boolean.TRUE);
    public static final KsValue FALSE = new KsValue(Type.BOOLEAN, Boolean.FALSE);

        // Factory methods
    
    public static KsValue ofNum(double n)  { return new KsValue(Type.NUMBER,  n); }
    public static KsValue ofStr(String s)  { return new KsValue(Type.STRING,  s); }
    public static KsValue ofBool(boolean b){ return b ? TRUE : FALSE; }

    public static KsValue ofList(List<KsValue> l) {
        return new KsValue(Type.LIST, new ArrayList<>(l));
    }

    public static KsValue ofDict(Map<String, KsValue> m) {
        return new KsValue(Type.DICT, new LinkedHashMap<>(m));
    }

    public static KsValue ofFn(String name, List<String> params, List<KsNode.Stmt> body) {
        return new KsValue(Type.FUNCTION, new KsFn(name, params, body));
    }

    public static KsValue ofNative(String name, Function<List<KsValue>, KsValue> fn) {
        return new KsValue(Type.NATIVE, new KsNative(name, fn));
    }

        // Typed accessors (unchecked - call after checking type)
    
    public double               asNum()    { return (Double)  raw; }
    public String               asStr()    { return (String)  raw; }
    public boolean              asBool()   { return (Boolean) raw; }
    @SuppressWarnings("unchecked")
    public List<KsValue>        asList()   { return (List<KsValue>) raw; }
    @SuppressWarnings("unchecked")
    public Map<String, KsValue> asDict()   { return (Map<String, KsValue>) raw; }
    public KsFn                 asFn()     { return (KsFn)     raw; }
    public KsNative             asNative() { return (KsNative) raw; }

        // Truthiness (falsy: null, false, 0, "", [], {})
    
    public boolean isTruthy() {
        switch (type) {
            case NULL:    return false;
            case BOOLEAN: return asBool();
            case NUMBER:  return asNum() != 0.0;
            case STRING:  return !asStr().isEmpty();
            case LIST:    return !asList().isEmpty();
            case DICT:    return !asDict().isEmpty();
            default:      return true; // FUNCTION, NATIVE are always truthy
        }
    }

        // Display
    
    @Override
    public String toString() {
        switch (type) {
            case NULL:    return "null";
            case BOOLEAN: return asBool() ? "true" : "false";
            case NUMBER: {
                double n = asNum();
                if (!Double.isInfinite(n) && !Double.isNaN(n) && n == Math.floor(n) && Math.abs(n) < 1e15)
                    return String.valueOf((long) n);
                return String.valueOf(n);
            }
            case STRING: return asStr();
            case LIST: {
                StringBuilder sb = new StringBuilder("[");
                List<KsValue> l = asList();
                for (int i = 0; i < l.size(); i++) {
                    if (i > 0) sb.append(", ");
                    KsValue item = l.get(i);
                    if (item.type == Type.STRING) sb.append('"').append(item.asStr()).append('"');
                    else sb.append(item);
                }
                return sb.append(']').toString();
            }
            case DICT: {
                StringBuilder sb = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, KsValue> e : asDict().entrySet()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append('"').append(e.getKey()).append("\": ");
                    KsValue v = e.getValue();
                    if (v.type == Type.STRING) sb.append('"').append(v.asStr()).append('"');
                    else sb.append(v);
                }
                return sb.append('}').toString();
            }
            case FUNCTION: return "<function " + asFn().name + ">";
            case NATIVE:   return "<native "   + asNative().name + ">";
            default:       return "?";
        }
    }

        // Equality
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KsValue)) return false;
        KsValue other = (KsValue) o;
        if (type != other.type) return false;
        return Objects.equals(raw, other.raw);
    }

    @Override
    public int hashCode() { return raw == null ? 0 : raw.hashCode(); }

        // Inner helper types
    
    /** A user-defined function value (name + param list + AST body). */
    public static final class KsFn {
        public final String           name;
        public final List<String>     params;
        public final List<KsNode.Stmt> body;

        public KsFn(String name, List<String> params, List<KsNode.Stmt> body) {
            this.name   = name;
            this.params = params;
            this.body   = body;
        }
    }

    /** A native (Java lambda) function value. */
    public static final class KsNative {
        public final String name;
        public final Function<List<KsValue>, KsValue> fn;

        public KsNative(String name, Function<List<KsValue>, KsValue> fn) {
            this.name = name;
            this.fn   = fn;
        }
    }
}
