package com.kelpwing.kelpylandiaplugin.utils;

import com.kelpwing.kelpylandiaplugin.KelpylandiaPlugin;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;

/**
 * Checks Modrinth for a newer version of the plugin.
 * Uses the Modrinth API: GET /project/{id}/version (latest release).
 *
 * All network calls happen asynchronously so they never block the main thread.
 */
public class UpdateChecker {

    // Modrinth project slug - matches https://modrinth.com/plugin/kelpyylandia-plugin
    private static final String PROJECT_ID   = "kelpyylandia-plugin";
    private static final String API_BASE     = "https://api.modrinth.com/v2";
    private static final String USER_AGENT   = "KelpylandiaPlugin/UpdateChecker (github.com/itz-winter)";

    private final KelpylandiaPlugin plugin;
    private final String currentVersion;

    private volatile boolean updateAvailable = false;
    private volatile String  latestVersion   = null;
    private volatile String  downloadUrl     = null;
    private volatile String  versionName     = null; // human-readable release title
    private volatile boolean checkCompleted  = false; // true once any check has finished

    public UpdateChecker(KelpylandiaPlugin plugin) {
        this.plugin         = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    // Public API 

    public boolean isUpdateAvailable()  { return updateAvailable; }
    public boolean hasChecked()         { return checkCompleted;  }
    public String  getLatestVersion()   { return latestVersion;   }
    public String  getCurrentVersion()  { return currentVersion;  }
    public String  getDownloadUrl()     { return downloadUrl;      }
    public String  getVersionName()     { return versionName != null ? versionName : latestVersion; }

    // Async check 

    /**
     * Schedules an async check. Results are available via the getters above
     * once the callback has fired.
     *
     * @param onComplete optional Runnable called on the main thread after the
     *                   check finishes (whether or not an update was found).
     */
    public void checkAsync(Runnable onComplete) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                fetchLatestVersion();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[UpdateChecker] Failed to check for updates: " + e.getMessage());
            } finally {
                if (onComplete != null) {
                    Bukkit.getScheduler().runTask(plugin, onComplete);
                }
            }
        });
    }

    /** Blocking fetch - call only from an async thread. */
    public void fetchLatestVersion() throws Exception {
        // GET /project/{id}/version?loaders=["bukkit","spigot","paper"]&featured=true
        // We just grab the first (newest) release version.
        String urlStr = API_BASE + "/project/" + PROJECT_ID + "/version";
        HttpURLConnection con = openConnection(urlStr);
        int status = con.getResponseCode();
        if (status != 200) {
            plugin.getLogger().warning("[UpdateChecker] Modrinth returned HTTP " + status);
            return;
        }

        String body = readResponse(con);
        // Minimal JSON parse - avoid pulling in a JSON library dependency.
        // The response is a JSON array of version objects. We want the first entry's:
        //  "version_number" : "2.2.4"
        //  "name"           : "QoLPlugin v2.2.4"
        //  primary file url -> files[0].url

        String versionNumber = extractFirst(body, "\"version_number\"");
        String name          = extractFirst(body, "\"name\"");
        String fileUrl       = extractFirstFileUrl(body);

        if (versionNumber == null) {
            plugin.getLogger().warning("[UpdateChecker] Could not parse version from Modrinth response.");
            return;
        }

        latestVersion = versionNumber.trim();
        versionName   = name != null ? name.trim() : latestVersion;
        downloadUrl   = fileUrl;

        updateAvailable = isNewer(latestVersion, currentVersion);

        if (updateAvailable) {
            plugin.getLogger().info("[UpdateChecker] Update available: v" + currentVersion
                    + " → v" + latestVersion);
        } else {
            plugin.getLogger().info("[UpdateChecker] Plugin is up to date (v" + currentVersion + ").");
        }
        checkCompleted = true;
    }

    // Helpers 

    private HttpURLConnection openConnection(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setConnectTimeout(5_000);
        con.setReadTimeout(5_000);
        return con;
    }

    private String readResponse(HttpURLConnection con) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Extracts the string value of the first occurrence of {@code "key"} in the JSON.
     * Works for simple string values: "key":"value" or "key": "value".
     */
    private String extractFirst(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    /**
     * Extracts the primary file download URL from the "files" array of the first version object.
     * Looks for the first object in "files" where "primary":true, or falls back to the first url.
     */
    private String extractFirstFileUrl(String json) {
        int filesIdx = json.indexOf("\"files\"");
        if (filesIdx < 0) return null;
        // Find the first "url" inside the files block
        int urlIdx = json.indexOf("\"url\"", filesIdx);
        if (urlIdx < 0) return null;
        int colon = json.indexOf(':', urlIdx + 5);
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    /**
     * Returns true if {@code remote} is strictly newer than {@code local}.
     *
     * Each dot-separated segment is split into a numeric part and an optional
     * alphabetic suffix (e.g. "1b" → num=1, suffix="b").
     *
     * Ordering rules per segment:
     *   1. Higher numeric value wins (2 > 1).
     *   2. If the numeric values are equal, a segment WITH a letter suffix is
     *      considered newer than one without  ("1b" > "1", "1b" > "1a").
     *      Pre-release tags like "-SNAPSHOT" or "-beta" (which appear after a
     *      hyphen) are stripped before parsing - they do not bump the version.
     */
    static boolean isNewer(String remote, String local) {
        String[] rParts = splitDots(remote);
        String[] lParts = splitDots(local);
        int len = Math.max(rParts.length, lParts.length);
        for (int i = 0; i < len; i++) {
            String rSeg = i < rParts.length ? rParts[i] : "0";
            String lSeg = i < lParts.length ? lParts[i] : "0";
            int cmp = compareSegment(rSeg, lSeg);
            if (cmp > 0) return true;
            if (cmp < 0) return false;
        }
        return false;
    }

    /** Split on dots, stripping pre-release tags after a hyphen in each segment. */
    private static String[] splitDots(String v) {
        String[] parts = v.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            // Remove pre-release markers like "-SNAPSHOT", "-beta", "-RC1"
            int hyphen = parts[i].indexOf('-');
            if (hyphen >= 0) parts[i] = parts[i].substring(0, hyphen);
        }
        return parts;
    }

    /**
     * Compares two version segments that may each have the form {@code \d+[a-z]?}.
     * Returns positive if {@code a} > {@code b}, negative if {@code a} < {@code b}, 0 if equal.
     * A letter suffix makes the segment higher than the same number without one
     * ("1b" > "1", "1b" > "1a").
     */
    private static int compareSegment(String a, String b) {
        // Split each segment into numeric prefix + optional letter suffix
        int aNum = 0; String aSuf = "";
        int bNum = 0; String bSuf = "";

        java.util.regex.Matcher mA = java.util.regex.Pattern.compile("^(\\d+)([a-zA-Z]*)$").matcher(a.trim());
        java.util.regex.Matcher mB = java.util.regex.Pattern.compile("^(\\d+)([a-zA-Z]*)$").matcher(b.trim());

        if (mA.matches()) { aNum = Integer.parseInt(mA.group(1)); aSuf = mA.group(2).toLowerCase(); }
        if (mB.matches()) { bNum = Integer.parseInt(mB.group(1)); bSuf = mB.group(2).toLowerCase(); }

        if (aNum != bNum) return Integer.compare(aNum, bNum);
        // Same numeric value - a segment with any suffix is newer than one without.
        // Between two non-empty suffixes, compare alphabetically ("b" > "a").
        boolean aHas = !aSuf.isEmpty();
        boolean bHas = !bSuf.isEmpty();
        if (aHas && !bHas) return 1;   // "1b" > "1"
        if (!aHas && bHas) return -1;  // "1"  < "1b"
        return aSuf.compareTo(bSuf);   // "1b" vs "1a"
    }
}
