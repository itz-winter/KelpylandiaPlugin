package com.kelpwing.kelpylandiaplugin.commands.custom.ks;

/**
 * A single token produced by {@link KsLexer}.
 *
 * Implemented as a plain class (not a Java 16 record) to be explicit
 * and to allow the {@code type} field to be checked in a switch directly.
 */
public final class KsToken {

    public enum Type {
        //  Literals 
        NUMBER,     // 42  3.14
        STRING,     // "hello"  'world'
        BOOL_LIT,   // true  false
        NULL_LIT,   // null

        //  Identifiers 
        IDENT,

        //  Keywords 
        DEF, IF, ELIF, ELSE,
        WHILE, FOR, IN,
        RETURN, BREAK, CONTINUE,
        PRINT,
        TRY, CATCH, THROW,
        AND, OR, NOT,

        //  Operators 
        PLUS,       // +
        MINUS,      // -
        STAR,       // *
        SLASH,      // /
        PERCENT,    // %
        EQ,         // ==
        NEQ,        // !=
        LT,         // <
        LE,         // <=
        GT,         // >
        GE,         // >=
        ASSIGN,     // =
        PLUS_EQ,    // +=
        MINUS_EQ,   // -=
        STAR_EQ,    // *=
        SLASH_EQ,   // /=

        //  Punctuation 
        LPAREN,     // (
        RPAREN,     // )
        LBRACE,     // {
        RBRACE,     // }
        LBRACKET,   // [
        RBRACKET,   // ]
        COMMA,      // ,
        COLON,      // :
        DOT,        // .

        //  Special 
        EOF
    }

    public final Type   type;
    public final String value;
    public final int    line;

    public KsToken(Type type, String value, int line) {
        this.type  = type;
        this.value = value;
        this.line  = line;
    }

    @Override
    public String toString() {
        return type + "('" + value + "')@" + line;
    }
}
