package cn.alvkeke.dropto.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import cn.alvkeke.dropto.data.NoteItem;

public class SyncHelper {

    // FIXME: debug use
    private static final boolean disableNetwork = true;
    private static final String DEBUG_TAG = "SyncHelper";

    private static final String hostname = "http://10.0.0.143:9000/";

    private static String buildURL(String type, String operation) {
        return hostname + type + "/" + operation;
    }
    private static String buildCategory(String op) {
        return buildURL("category", op);
    }
    private static String buildNote(String operation) {
        return buildURL("note", operation);
    }
    private static String buildNoteCreate() {
        return buildNote("create");
    }
    private static String buildNoteRemove() {
        return buildNote("remove");
    }
    private static String buildNoteUpdate() {
        return buildNote("update");
    }

    private static HttpURLConnection setupConnection(URL url) throws Exception{
        return (HttpURLConnection) url.openConnection();
    }

    private static void sendBody(HttpURLConnection connection, byte[] body) throws Exception {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        if (body != null) {
            OutputStream os = connection.getOutputStream();
            os.write(body);
            os.close();
        }
    }

    private static String readResponse(HttpURLConnection connection) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder resp = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            resp.append(line);
            resp.append('\n');
        }
        in.close();
        return resp.toString();
    }

    public static void noteCreate(NoteItem note) throws Exception{
        if (disableNetwork) return;

        URL url = new URL(buildNoteCreate());
        HttpURLConnection connection = setupConnection(url);
        sendBody(connection, note.toJSON().toString().getBytes());

        int respCode = connection.getResponseCode();
        if (respCode == HttpURLConnection.HTTP_OK) {
            Log.e(DEBUG_TAG, "resp: " + readResponse(connection));
        } else {
            throw new Exception("connection error: " + respCode);
        }
    }

    public static void noteUpdate(NoteItem note) throws Exception {
        if (disableNetwork) return;

        URL url = new URL(buildNoteUpdate());
        HttpURLConnection connection =  setupConnection(url);
        sendBody(connection, note.toJSON().toString().getBytes());

        int respCode = connection.getResponseCode();
        if (respCode == HttpURLConnection.HTTP_OK) {
            Log.e(DEBUG_TAG, "resp: " + readResponse(connection));
        } else {
            throw new Exception("connection error: " + respCode);
        }
    }

    public static void noteRemove(NoteItem note) throws Exception {
        if (disableNetwork) return;

        URL url = new URL(buildNoteRemove());
        HttpURLConnection connection = setupConnection(url);
        sendBody(connection, note.toIdOnlyJSON().toString().getBytes());

        int respCode = connection.getResponseCode();
        if (respCode == HttpURLConnection.HTTP_OK) {
            Log.e(DEBUG_TAG, "resp: " + readResponse(connection));
        } else {
            throw new Exception("connection error: " + respCode);
        }
    }

}
