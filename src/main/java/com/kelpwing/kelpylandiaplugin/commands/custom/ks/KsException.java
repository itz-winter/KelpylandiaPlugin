package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

/**
 * Control-flow signals and runtime errors for the KS Lite interpreter.
 *
 * All inner classes intentionally suppress stack-trace collection
 * (super(msg, null, true, false)) because they are used for normal
 * control flow inside a script, not genuine JVM errors.
 */
public final class KsException {

        //  Control-flow signals
    
    /** Thrown when a `return expr` statement executes inside a script. */
    public static final class Return extends RuntimeException {
        public final KsValue value;
        public Return(KsValue value) {
            super(value != null ? value.toString() : "null", null, true, false);
            this.value = value;
        }
    }

    /** Thrown when a `break` statement executes. */
    public static final class Break extends RuntimeException {
        public static final Break INSTANCE = new Break();
        private Break() { super(null, null, true, false); }
    }

    /** Thrown when a `continue` statement executes. */
    public static final class Continue extends RuntimeException {
        public static final Continue INSTANCE = new Continue();
        private Continue() { super(null, null, true, false); }
    }

    /**
     * Thrown when a `throw expr` statement executes.
     * Caught by the nearest enclosing try/catch block.
     */
    public static final class Throw extends RuntimeException {
        public final KsValue value;
        public Throw(KsValue value) {
            super(value != null ? value.toString() : "null", null, true, false);
            this.value = value;
        }
    }

        //  Runtime errors
    
    /**
     * Thrown on fatal runtime errors: undefined variable, type mismatch,
     * wrong number of arguments, etc.
     */
    public static final class Runtime extends RuntimeException {
        public Runtime(String message) {
            super(message, null, true, false);
        }
    }

    private KsException() {}
}
