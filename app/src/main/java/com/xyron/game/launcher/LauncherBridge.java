package com.xyron.game.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.webkit.JavascriptInterface;

import com.xyron.game.launcher.util.ServerConfigManager;

import org.ini4j.Wini;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LauncherBridge {

    private final WebLauncherActivity activity;

    public LauncherBridge(WebLauncherActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getSettings() {
        try {
            JSONObject obj = new JSONObject();
            Wini wini = openIni();
            if (wini != null) {
                String name = wini.get("client", "name");
                String chatLines = wini.get("gui", "ChatMaxMessages");
                String voiceChat = wini.get("gui", "VoiceChatEnable");
                obj.put("nickname", name != null ? name.trim() : "");
                obj.put("chatLines", chatLines != null ? chatLines.trim() : "5");
                obj.put("voiceChat", parseBool(voiceChat, true));
            } else {
                obj.put("nickname", "");
                obj.put("chatLines", "5");
                obj.put("voiceChat", true);
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            obj.put("keyboard", prefs.getBoolean("ANDROID_KEYBOARD", false));
            obj.put("version", com.xyron.game.BuildConfig.VERSION_NAME);
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String saveSettings(String nickname, String chatLines, boolean keyboard, boolean voiceChat) {
        try {
            if (nickname != null && !nickname.trim().isEmpty()) {
                Wini wini = openOrCreateIni();
                if (wini != null) {
                    wini.put("client", "name", nickname.trim());
                    wini.put("gui", "ChatMaxMessages", chatLines != null ? chatLines.trim() : "5");
                    wini.put("gui", "VoiceChatEnable", voiceChat ? "true" : "false");
                    wini.store();
                }
            }
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            prefs.edit().putBoolean("ANDROID_KEYBOARD", keyboard).apply();
            return "ok";
        } catch (Exception e) {
            return "Erro ao salvar: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public String getServers() {
        try {
            JSONArray arr = new JSONArray();
            List<ServerConfigManager.ServerOption> servers =
                    ServerConfigManager.getAvailableServers(activity);
            ServerConfigManager.ServerOption selected =
                    ServerConfigManager.getSelectedServer(activity);
            for (ServerConfigManager.ServerOption s : servers) {
                JSONObject obj = new JSONObject();
                obj.put("host", s.host);
                obj.put("port", s.port);
                obj.put("name", s.name != null ? s.name : s.host + ":" + s.port);
                obj.put("favorite", s.favorite);
                boolean isSelected = selected != null && selected.isValid()
                        && selected.host.equals(s.host) && selected.port == s.port;
                obj.put("selected", isSelected);
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    @JavascriptInterface
    public String addServer(String rawAddress) {
        try {
            if (!ServerConfigManager.isValidRawAddress(rawAddress)) {
                return "Endereço inválido. Use o formato IP:PORTA.";
            }
            ServerConfigManager.ServerOption option =
                    ServerConfigManager.addOrUpdateServer(activity, rawAddress);
            if (!option.isValid()) {
                return "Limite de " + ServerConfigManager.getMaxSavedServers() + " servidores atingido.";
            }
            return "ok";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public String selectServer(String host, int port) {
        try {
            List<ServerConfigManager.ServerOption> servers =
                    ServerConfigManager.getAvailableServers(activity);
            for (ServerConfigManager.ServerOption s : servers) {
                if (s.host.equals(host) && s.port == port) {
                    ServerConfigManager.saveSelectedServer(activity, s);
                    return "ok";
                }
            }
            return "Servidor não encontrado.";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public String removeServer(String host, int port) {
        try {
            List<ServerConfigManager.ServerOption> servers =
                    ServerConfigManager.getAvailableServers(activity);
            for (ServerConfigManager.ServerOption s : servers) {
                if (s.host.equals(host) && s.port == port) {
                    ServerConfigManager.removeServer(activity, s);
                    ServerConfigManager.ensureSelectedServer(activity);
                    return "ok";
                }
            }
            return "Servidor não encontrado.";
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public void playGame() {
        activity.runOnUiThread(activity::startGameFromBridge);
    }

    @JavascriptInterface
    public void reinstallData() {
        activity.runOnUiThread(activity::reinstallDataFromBridge);
    }

    @JavascriptInterface
    public String getSelectedServer() {
        try {
            ServerConfigManager.ServerOption s = ServerConfigManager.getSelectedServer(activity);
            if (s == null || !s.isValid()) return "";
            JSONObject obj = new JSONObject();
            obj.put("host", s.host);
            obj.put("port", s.port);
            obj.put("name", s.name != null ? s.name : s.host + ":" + s.port);
            return obj.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private Wini openIni() {
        try {
            File file = getSettingsFile();
            if (file == null || !file.exists()) return null;
            return new Wini(file);
        } catch (IOException e) {
            return null;
        }
    }

    private Wini openOrCreateIni() {
        try {
            File file = getSettingsFile();
            if (file == null) return null;
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                file.createNewFile();
            }
            return new Wini(file);
        } catch (IOException e) {
            return null;
        }
    }

    private File getSettingsFile() {
        if (activity.getExternalFilesDir(null) == null) return null;
        return new File(activity.getExternalFilesDir(null), "SAMP/settings.ini");
    }

    private boolean parseBool(String value, boolean fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String v = value.trim().toLowerCase();
        if ("1".equals(v) || "true".equals(v) || "yes".equals(v)) return true;
        if ("0".equals(v) || "false".equals(v) || "no".equals(v)) return false;
        return fallback;
    }
}
