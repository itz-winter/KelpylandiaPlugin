package com.kelpwing.kelpylandiaplugin.commands.custom;

/**
 * Holds the parsed config data for a single custom command entry
 * from {@code custom-commands.yml}.
 *
 * Either {@link #script} (inline) or {@link #file} (external .ks path)
 * will be non-null — never both.
 */
public final class CustomCommandEntry {

    /** The command name as typed by the player (no leading slash). */
    public final String name;

    /**
     * Inline KS Lite script source, or {@code null} if a file is used.
     * Corresponds to the {@code function:} key in the YAML.
     */
    public final String script;

    /**
     * Path to an external {@code .ks} file, relative to the plugin data folder,
     * or {@code null} if an inline script is used.
     * Corresponds to the {@code file:} key in the YAML.
     */
    public final String file;

    /** Permission node required to run this command, or empty string for none. */
    public final String permission;

    /** Message shown when the player lacks {@link #permission}. */
    public final String noPermissionMessage;

    /** Short description shown in /help. */
    public final String description;

    /** Usage line shown on script error (e.g. {@code /give-spawner <MOB>}). */
    public final String usage;

    public CustomCommandEntry(
            String name,
            String script,
            String file,
            String permission,
            String noPermissionMessage,
            String description,
            String usage) {
        this.name               = name;
        this.script             = script;
        this.file               = file;
        this.permission         = permission != null ? permission : "";
        this.noPermissionMessage = noPermissionMessage != null ? noPermissionMessage : "";
        this.description        = description != null ? description : "";
        this.usage              = usage != null ? usage : "";
    }

    @Override
    public String toString() {
        return "CustomCommandEntry{name='" + name + "', file=" + file + "}";
    }
}
