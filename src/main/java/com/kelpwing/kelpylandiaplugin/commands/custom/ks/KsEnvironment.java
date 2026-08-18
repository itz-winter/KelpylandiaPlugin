package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * A lexical-scope chain for the KS Lite interpreter.
 *
 * Each function call or block that introduces names (for-loop variable, catch-var,
 * function params) pushes a new scope.  Variable lookup walks from the innermost
 * scope outward.  Assignment via {@link #update} writes to the scope where the
 * variable was first declared, or creates it in the current scope if absent.
 */
public final class KsEnvironment {

    private final Deque<Map<String, KsValue>> scopes = new ArrayDeque<>();

    public KsEnvironment() {
        scopes.push(new HashMap<>());   // global scope
    }

        //  Scope management
    
    /** Push a new (inner) scope. */
    public void push() {
        scopes.push(new HashMap<>());
    }

    /** Pop the innermost scope. Never pops the global scope. */
    public void pop() {
        if (scopes.size() > 1) scopes.pop();
    }

        //  Variable operations
    
    /**
     * Declare or overwrite a variable in the <em>current</em> (innermost) scope.
     * Used for function parameters, for-loop variables, and catch variables.
     */
    public void set(String name, KsValue value) {
        scopes.peek().put(name, value);
    }

    /**
     * Assign to the nearest scope that already contains {@code name}.
     * If {@code name} is not found in any scope, it is created in the current scope
     * (i.e. normal script-level assignment without prior declaration).
     */
    public void update(String name, KsValue value) {
        for (Map<String, KsValue> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        scopes.peek().put(name, value);
    }

    /**
     * Look up {@code name} in the scope chain.
     * Returns {@code null} if not found (not {@link KsValue#NULL}).
     */
    public KsValue get(String name) {
        for (Map<String, KsValue> scope : scopes) {
            KsValue v = scope.get(name);
            if (v != null) return v;
        }
        return null;
    }
}
