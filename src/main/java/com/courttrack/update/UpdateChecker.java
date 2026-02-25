package com.courttrack.update;

import com.courttrack.util.AppVersion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class UpdateChecker {

    private static final String API_URL_TEMPLATE =
            "https://api.github.com/repos/%s/%s/releases/latest";

    public Optional<UpdateInfo> checkForUpdate() {
        String owner = AppVersion.getGitHubOwner();
        String repo = AppVersion.getGitHubRepo();
        if (owner.isBlank() || repo.isBlank()) {
            System.out.println("GitHub owner/repo not configured, skipping update check");
            return Optional.empty();
        }

        try {
            String apiUrl = String.format(API_URL_TEMPLATE, owner, repo);
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int status = conn.getResponseCode();
            if (status != 200) {
                System.out.println("Update check returned HTTP " + status);
                return Optional.empty();
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject release = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String tagName = release.get("tag_name").getAsString();
            String releaseNotes = release.has("body") && !release.get("body").isJsonNull()
                    ? release.get("body").getAsString() : "";

            AppVersion remoteVersion = new AppVersion(tagName);
            AppVersion currentVersion = AppVersion.getCurrent();

            if (remoteVersion.compareTo(currentVersion) <= 0) {
                System.out.println("App is up to date (current=" + currentVersion + ", remote=" + remoteVersion + ")");
                return Optional.empty();
            }

            JsonArray assets = release.getAsJsonArray("assets");
            String downloadUrl = null;
            long fileSize = -1;

            // Prefer .zip, fall back to .jar
            String[] preferred = {".zip", ".jar"};
            outer:
            for (String ext : preferred) {
                for (JsonElement el : assets) {
                    JsonObject asset = el.getAsJsonObject();
                    String name = asset.get("name").getAsString();
                    if (name.endsWith(ext)) {
                        downloadUrl = asset.get("browser_download_url").getAsString();
                        fileSize = asset.has("size") ? asset.get("size").getAsLong() : -1;
                        System.out.println("Using asset: " + name);
                        break outer;
                    }
                }
            }

            if (downloadUrl == null) {
                System.out.println("No matching installer asset found (.zip or .jar)");
                return Optional.empty();
            }

            return Optional.of(new UpdateInfo(tagName, downloadUrl, releaseNotes, fileSize));

        } catch (Exception e) {
            System.err.println("Update check failed: " + e.getMessage());
            return Optional.empty();
        }
    }
}
