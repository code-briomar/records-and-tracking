package com.courttrack.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FirebaseRestClient {
    private static final Logger LOGGER = Logger.getLogger(FirebaseRestClient.class.getName());
    private static final String API_KEY = "AIzaSyAfTT-Op0ZRi90F40fx1fmN0znjVxbiL1Q";
    private static final String PROJECT_ID = "records-and-tracking";
    private static final String FIRESTORE_BASE =
            "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents";
    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY;
    private static final String REFRESH_URL =
            "https://securetoken.googleapis.com/v1/token?key=" + API_KEY;

    private static volatile FirebaseRestClient instance;

    private volatile String idToken;
    private volatile String refreshToken;
    private volatile long tokenExpiresAt = 0;

    private FirebaseRestClient() {}

    public static FirebaseRestClient getInstance() {
        if (instance == null) {
            synchronized (FirebaseRestClient.class) {
                if (instance == null) {
                    instance = new FirebaseRestClient();
                }
            }
        }
        return instance;
    }

    public void signInAnonymously() throws IOException {
        String response = post(SIGN_IN_URL, "{\"returnSecureToken\":true}", null);
        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        idToken = obj.get("idToken").getAsString();
        refreshToken = obj.get("refreshToken").getAsString();
        long expiresIn = Long.parseLong(obj.get("expiresIn").getAsString());
        tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000;
        LOGGER.info("Anonymous sign-in successful");
    }

    public boolean isAuthenticated() {
        return idToken != null;
    }

    private void refreshIfNeeded() throws IOException {
        if (idToken == null) {
            signInAnonymously();
            return;
        }
        if (System.currentTimeMillis() >= tokenExpiresAt) {
            if (refreshToken != null) {
                refreshIdToken();
            } else {
                signInAnonymously();
            }
        }
    }

    private void refreshIdToken() throws IOException {
        String body = "{\"grant_type\":\"refresh_token\",\"refresh_token\":\"" + refreshToken + "\"}";
        String response = post(REFRESH_URL, body, null);
        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        idToken = obj.get("id_token").getAsString();
        refreshToken = obj.get("refresh_token").getAsString();
        long expiresIn = Long.parseLong(obj.get("expires_in").getAsString());
        tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 60) * 1000;
        LOGGER.info("Token refreshed");
    }

    /**
     * List all documents in a collection path (e.g., "courts/court1/offenders").
     * Handles pagination automatically.
     */
    public List<Map.Entry<String, Map<String, Object>>> listDocuments(String collectionPath) throws IOException {
        refreshIfNeeded();
        List<Map.Entry<String, Map<String, Object>>> results = new ArrayList<>();
        String pageToken = null;

        do {
            String url = FIRESTORE_BASE + "/" + collectionPath + "?pageSize=500"
                    + (pageToken != null ? "&pageToken=" + pageToken : "");
            String response = get(url, idToken);
            JsonObject obj = JsonParser.parseString(response).getAsJsonObject();

            if (obj.has("documents")) {
                for (JsonElement elem : obj.getAsJsonArray("documents")) {
                    JsonObject doc = elem.getAsJsonObject();
                    String name = doc.get("name").getAsString();
                    String docId = name.substring(name.lastIndexOf('/') + 1);
                    Map<String, Object> fields = doc.has("fields")
                            ? parseFirestoreFields(doc.getAsJsonObject("fields"))
                            : new HashMap<>();
                    results.add(new AbstractMap.SimpleEntry<>(docId, fields));
                }
            }

            pageToken = obj.has("nextPageToken") ? obj.get("nextPageToken").getAsString() : null;
        } while (pageToken != null);

        return results;
    }

    /**
     * Write (upsert) a document at path (e.g., "courts/court1/offenders/person1").
     */
    public void upsertDocument(String docPath, Map<String, Object> data) throws IOException {
        refreshIfNeeded();
        String url = FIRESTORE_BASE + "/" + docPath;
        JsonObject body = new JsonObject();
        body.add("fields", buildFirestoreFields(data));
        patch(url, body.toString(), idToken);
    }

    /**
     * Query documents where field == value (EQUAL).
     * parentPath: e.g., "courts/court1"
     * collectionId: e.g., "users"
     */
    public List<Map.Entry<String, Map<String, Object>>> queryEqual(
            String parentPath, String collectionId, String field, Object value) throws IOException {
        return runStructuredQuery(parentPath, collectionId, field, "EQUAL", value);
    }

    /**
     * Query documents where field > value (GREATER_THAN).
     */
    public List<Map.Entry<String, Map<String, Object>>> queryGreaterThan(
            String parentPath, String collectionId, String field, Object value) throws IOException {
        return runStructuredQuery(parentPath, collectionId, field, "GREATER_THAN", value);
    }

    private List<Map.Entry<String, Map<String, Object>>> runStructuredQuery(
            String parentPath, String collectionId, String field, String op, Object value) throws IOException {
        refreshIfNeeded();
        String url = FIRESTORE_BASE + "/" + parentPath + ":runQuery";

        JsonObject fieldFilter = new JsonObject();
        JsonObject fieldObj = new JsonObject();
        fieldObj.addProperty("fieldPath", field);
        fieldFilter.add("field", fieldObj);
        fieldFilter.addProperty("op", op);
        fieldFilter.add("value", toFirestoreValue(value));

        JsonObject where = new JsonObject();
        where.add("fieldFilter", fieldFilter);

        JsonObject from = new JsonObject();
        from.addProperty("collectionId", collectionId);
        JsonArray fromArray = new JsonArray();
        fromArray.add(from);

        JsonObject structuredQuery = new JsonObject();
        structuredQuery.add("from", fromArray);
        structuredQuery.add("where", where);

        JsonObject body = new JsonObject();
        body.add("structuredQuery", structuredQuery);

        String response = post(url, body.toString(), idToken);
        List<Map.Entry<String, Map<String, Object>>> results = new ArrayList<>();

        JsonArray arr = JsonParser.parseString(response).getAsJsonArray();
        for (JsonElement elem : arr) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("document")) {
                JsonObject doc = obj.getAsJsonObject("document");
                String name = doc.get("name").getAsString();
                String docId = name.substring(name.lastIndexOf('/') + 1);
                Map<String, Object> fields = doc.has("fields")
                        ? parseFirestoreFields(doc.getAsJsonObject("fields"))
                        : new HashMap<>();
                results.add(new AbstractMap.SimpleEntry<>(docId, fields));
            }
        }

        return results;
    }

    // --- Firestore field format conversion ---

    private Map<String, Object> parseFirestoreFields(JsonObject fields) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : fields.entrySet()) {
            result.put(entry.getKey(), parseFirestoreValue(entry.getValue().getAsJsonObject()));
        }
        return result;
    }

    private Object parseFirestoreValue(JsonObject valueObj) {
        if (valueObj.has("stringValue")) {
            return valueObj.get("stringValue").getAsString();
        } else if (valueObj.has("integerValue")) {
            try {
                return Long.parseLong(valueObj.get("integerValue").getAsString());
            } catch (NumberFormatException e) {
                return valueObj.get("integerValue").getAsString();
            }
        } else if (valueObj.has("doubleValue")) {
            return valueObj.get("doubleValue").getAsDouble();
        } else if (valueObj.has("booleanValue")) {
            return valueObj.get("booleanValue").getAsBoolean();
        } else if (valueObj.has("nullValue")) {
            return null;
        } else if (valueObj.has("timestampValue")) {
            return valueObj.get("timestampValue").getAsString();
        } else if (valueObj.has("mapValue")) {
            JsonObject mapFields = valueObj.getAsJsonObject("mapValue").getAsJsonObject("fields");
            return parseFirestoreFields(mapFields);
        } else if (valueObj.has("arrayValue")) {
            List<Object> list = new ArrayList<>();
            JsonObject arrayValue = valueObj.getAsJsonObject("arrayValue");
            if (arrayValue.has("values")) {
                for (JsonElement elem : arrayValue.getAsJsonArray("values")) {
                    list.add(parseFirestoreValue(elem.getAsJsonObject()));
                }
            }
            return list;
        }
        return null;
    }

    private JsonObject buildFirestoreFields(Map<String, Object> data) {
        JsonObject fields = new JsonObject();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            fields.add(entry.getKey(), toFirestoreValue(entry.getValue()));
        }
        return fields;
    }

    private JsonElement toFirestoreValue(Object value) {
        JsonObject obj = new JsonObject();
        if (value == null) {
            obj.add("nullValue", JsonNull.INSTANCE);
        } else if (value instanceof Boolean) {
            obj.addProperty("booleanValue", (Boolean) value);
        } else if (value instanceof Long) {
            obj.addProperty("integerValue", value.toString());
        } else if (value instanceof Integer) {
            obj.addProperty("integerValue", value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            obj.addProperty("doubleValue", ((Number) value).doubleValue());
        } else if (value instanceof Number) {
            obj.addProperty("integerValue", value.toString());
        } else if (value instanceof String) {
            obj.addProperty("stringValue", (String) value);
        } else if (value instanceof List) {
            JsonObject arrayValue = new JsonObject();
            JsonArray values = new JsonArray();
            for (Object item : (List<?>) value) {
                values.add(toFirestoreValue(item));
            }
            arrayValue.add("values", values);
            obj.add("arrayValue", arrayValue);
        } else if (value instanceof Map) {
            JsonObject mapValue = new JsonObject();
            JsonObject innerFields = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                innerFields.add(entry.getKey().toString(), toFirestoreValue(entry.getValue()));
            }
            mapValue.add("fields", innerFields);
            obj.add("mapValue", mapValue);
        } else {
            obj.addProperty("stringValue", value.toString());
        }
        return obj;
    }

    // --- HTTP helpers ---

    private String get(String url, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        return readResponse(conn);
    }

    private String post(String url, String body, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private void patch(String url, String body, String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PATCH");
        conn.setRequestProperty("Content-Type", "application/json");
        if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) throw new IOException("HTTP " + status + " with no body");
        String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + ": " + response);
        }
        return response;
    }
}
