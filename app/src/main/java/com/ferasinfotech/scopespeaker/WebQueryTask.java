package com.ferasinfotech.scopespeaker;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by jferas on 4/12/17.
 */

/**
 * Implementation of AsyncTask, to fetch the data in the background away from
 * the UI thread.
 */
public class WebQueryTask extends AsyncTask<String, Void, String> {

    private ScopeSpeakerActivity scopeSpeakerActivity = null;
    private Context the_context = null;
    private Bundle params = null;

    public void init(Context context, ScopeSpeakerActivity sta) {
        try {
            scopeSpeakerActivity = sta;
            the_context = context;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String doInBackground(String... urls) {
        try {
            return loadFromNetwork(urls[0]);
        }
        catch (IOException e) {
            scopeSpeakerActivity.webQueryError("Connection error");
            return("Connection error");
        }
    }

    @Override
    protected void onPostExecute(String web_result) {
        scopeSpeakerActivity.webQueryResult(web_result);
    }

    /** Initiates the fetch operation. */
    private String loadFromNetwork(String urlString) throws IOException {
        InputStream stream = null;
        String str = "";

        try {
            stream = downloadUrl(urlString);
            str = readIt(stream);
        }
        finally {
            if (stream != null) {
                stream.close();
            }
        }
        return str;
    }

    /**
     * Given a string representation of a URL, sets up a connection and gets
     * an input stream.
     * @param urlString A string representation of a URL.
     * @return An InputStream retrieved from a successful HttpURLConnection.
     * @throws java.io.IOException
     */
    private InputStream downloadUrl(String urlString) throws IOException {
        // BEGIN_INCLUDE(get_inputstream)
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000 /* milliseconds */);
        conn.setConnectTimeout(15000 /* milliseconds */);
        conn.setRequestMethod("GET");
        //   conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        //   conn.setRequestProperty("Data-Type", "json");
        conn.setDoInput(true);
        // Start the query
        conn.connect();
        return conn.getInputStream();
        // END_INCLUDE(get_inputstream)
    }

    /** Reads an InputStream and converts it to a String.
     * @param stream InputStream containing HTML from targeted site.
     * @return String concatenated according to len parameter.
     * @throws java.io.IOException
     * @throws java.io.UnsupportedEncodingException
     */
    private String readIt(InputStream stream) throws IOException {
        char[] buffer = new char[1000];
        StringBuilder s = new StringBuilder();
        int bytes_read;
        Reader reader = new InputStreamReader(stream, "UTF-8");
        while ((bytes_read = reader.read(buffer)) >= 0) {
            s.append(new String(buffer, 0, bytes_read));
        }
        return s.toString();
    }
}
