package com.ferasinfotech.scopespeaker;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Created by DoguD on 01/07/2017.
 */

public class TranslatorBackgroundTask extends AsyncTask<String, Void, String> {

    //Storage for this instance of the translaor class
    private ScopeSpeakerActivity scopeSpeakerActivity = null;
    String who_said_it = null;
    String textToBeTranslated = null;
    String languagePair = null;
    String resultString = null;


    //Declare Context
    Context ctx;

    //Set Context on init
    TranslatorBackgroundTask(Context ctx){
        this.ctx = ctx;
    }

    public void init(ScopeSpeakerActivity ssa) {
        try {
            scopeSpeakerActivity = ssa;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    protected String doInBackground(String... params) {

        // object asking for translation
        ScopeSpeakerActivity ssa;

        // save the data related to the translation request
        this.who_said_it = params[0];
        this.textToBeTranslated = params[1];
        this.languagePair = params[2];

        String jsonString;
        String translation_command;

        if (languagePair.indexOf("?") == 0) {
            translation_command = languagePair.split("-")[1];
        }
        else {
            translation_command = languagePair;
        }

        try {
            //Set up the translation call URL
            String yandexKey = "trnsl.1.1.20170707T040715Z.91d8bbf749039bd6.313fa4324e6371e9ae58a30e2a4f93b47dca1ca2";
            String yandexUrl = "https://translate.yandex.net/api/v1.5/tr.json/translate?key=" + yandexKey
             + "&text=" + URLEncoder.encode(textToBeTranslated, "UTF-8") + "&lang=" + translation_command;
            URL yandexTranslateURL = new URL(yandexUrl);

            //Set Http Conncection, Input Stream, and Buffered Reader
            HttpURLConnection httpJsonConnection = (HttpURLConnection) yandexTranslateURL.openConnection();
            InputStream inputStream = httpJsonConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            //Set string builder and insert retrieved JSON result into it
            StringBuilder jsonStringBuilder = new StringBuilder();
            while ((jsonString = bufferedReader.readLine()) != null) {
                jsonStringBuilder.append(jsonString + "\n");
            }

            //Close and disconnect
            bufferedReader.close();
            inputStream.close();
            httpJsonConnection.disconnect();

            //Making result human readable
            resultString = jsonStringBuilder.toString().trim();
            //Getting the characters between [ and ]
            resultString = resultString.substring(resultString.indexOf('[')+1);
            resultString = resultString.substring(0,resultString.indexOf("]"));
            //Getting the characters between " and "
            resultString = resultString.substring(resultString.indexOf("\"")+1);
            resultString = resultString.substring(0,resultString.indexOf("\""));

            return jsonStringBuilder.toString().trim();

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(String result) {
        String translation_to_say = resultString;

        if (translation_to_say == null) {
            translation_to_say = textToBeTranslated;
        }
        scopeSpeakerActivity.sayTranslated(who_said_it, translation_to_say,
                "<br><br>" + this.languagePair + " Translation by Yandex");
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
    }
}