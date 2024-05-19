package cn.alvkeke.dropto.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import cn.alvkeke.dropto.data.NoteItem;

public class SyncHelper {

    private static final String DEBUG_TAG = "SyncHelper";

    private static final String hostname = "http://10.0.0.143/";

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

    public static void noteCreate(NoteItem newItem) {
        try {
            URL url = new URL(buildNoteCreate());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");



            int respCode = connection.getResponseCode();
            Log.e(DEBUG_TAG, "response code: " + respCode);
            if (respCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    resp.append(line);
                    resp.append('\n');
                }
                in.close();

                Log.e(DEBUG_TAG, "resp: " + resp);
            }
        } catch (IOException ex) {
            Log.e(DEBUG_TAG, "failed to GET: " + ex);
        }
    }

}
