package com.ferasinfotech.scopespeaker;

import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.BoolRes;
import android.support.annotation.StringDef;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ScopeSpeakerActivity extends AppCompatActivity {

    private final static String PERISCOPE_URL = "https://www.periscope.tv/";
    private final static String PERISCOPE_BROACAST_INFO_URL = "https://api.periscope.tv/api/v2/accessVideoPublic?broadcast_id=";
    private final static String PERISCOPE_CHAT_ACCESS_URL = "https://api.periscope.tv/api/v2/accessChatPublic?chat_token=";

    private final static String VIDEO_TAG = "https://www.pscp.tv/w/";
    private final static String JSON_TAG_CHAT_TOKEN = "chat_token";
    private final static String JSON_TAG_ENDPOINT_URL = "endpoint";

    private enum State {AWAITING_BROADCAST_ID, AWAITING_CHAT_ACCESS_TOKEN, AWAITING_CHAT_ENDPOINT,
                        AWAITING_WEBSOCKET_CONNECTION};

    private State appState = null;

    // state variable indicating whether speech is in progress or not
    private Boolean      speaking = false;

    private WebView      messageView = null;
    private WebQueryTask webQueryTask = null;
    private TTSManager   ttsManager = null;

    // timer variables
    private Handler     handler = null;
    private Runnable    the_runnable = null;


    // storage for queue of messages that are spoken on a FIFO basis
    private final List<String> messages = new ArrayList<>();
    private String       queuedMessageBeingSaid = null;

    // username text widget
    private TextView userNameText = null;

    // name of user fetch from text widget
    private String userName = null;

    // Broadcast ID fetched as first found from Periscope query for given user
    private String broadcastID = null;

    // chat access token fetched from the URL that gives individual broadcast info
    private String chatAccessToken = null;

    // endpoint URL for websocket connection to establish for chat messages
    private String endpointURL = null;

    // settings variables
    private Integer     secondsToWait = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webQueryTask = new WebQueryTask();
        webQueryTask.init(this);
        createTextToSpeechManager();

        setContentView(R.layout.activity_scope_speaker);
        userNameText = (TextView) findViewById(R.id.username);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        messageView = (WebView) findViewById(R.id.messageView);
        setMessageView("Enter a Periscope user's name and ScopeSpeaker will attempt to find their current live broadcast, and audibly read the broadcast's chat messages to you.");
    }

    // app shutdown - destroy allocated objects
    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyTextToSpeechManager();
    }

    // create the text to speech manager
    private void createTextToSpeechManager() {
        ttsManager = new TTSManager();
        ttsManager.init(this, this);
    }

    // destroy the text to speech manager
    private void destroyTextToSpeechManager() {
        if (ttsManager != null) {
            ttsManager.shutDown();
            ttsManager = null;
        }
    }


    // Inflate the menu; this adds items to the action bar if it is present.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scope_speaker, menu);
        return true;
    }

    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // start the process of getting the chat messages for the specified user's live broadcast
    public void processChatMessages(View v) {
        userQuery();
    }

    private void userQuery() {
        userName = (String) userNameText.getText().toString();
        queueMessageToSay("Looking for a Periscope live stream by " + userName);
        appState = State.AWAITING_BROADCAST_ID;
        webQueryTask.execute(PERISCOPE_URL + userName);
    }

    private void schedulePeriscopeUserQuery() {
        queueMessageToSay("Will check again in " + secondsToWait + " seconds");
        handler = new Handler();
        the_runnable = new Runnable() {
            @Override
            public void run() {
                //Query Periscope for user again after 'secondsToWait' seconds (converted to ms)
                handler = null;
                userQuery();
            }
        };
        handler.postDelayed(the_runnable, secondsToWait * 1000);
    }

    // process the successful result of a webQueryTask request
    public void webQueryResult(String response) {
        if (appState == State.AWAITING_BROADCAST_ID) {
            broadcastID = extractBroadcastID(response);
            if (broadcastID != null) {
                appState = State.AWAITING_CHAT_ACCESS_TOKEN;
                webQueryTask.execute(PERISCOPE_BROACAST_INFO_URL + broadcastID);
            }
            else {
                queueMessageToSay(userName + " has no broadcasts");
            }
        }
        else if (appState == State.AWAITING_CHAT_ACCESS_TOKEN) {
            try {
                JSONObject infoJsonResponse = new JSONObject(response);
                chatAccessToken = infoJsonResponse.getString(JSON_TAG_CHAT_TOKEN);
                appState = State.AWAITING_CHAT_ENDPOINT;
                webQueryTask.execute(PERISCOPE_CHAT_ACCESS_URL + chatAccessToken);
            }
            catch (JSONException e) {
                schedulePeriscopeUserQuery();
            }
        }
        else if (appState == State.AWAITING_CHAT_ENDPOINT) {
            try {
                JSONObject infoJsonResponse = new JSONObject(response);
                endpointURL = infoJsonResponse.getString(JSON_TAG_ENDPOINT_URL);
                appState = State.AWAITING_WEBSOCKET_CONNECTION;
                // this is where we start up the web socket and send some handshake info
                // (broadcast ID and chat access token)
            }
            catch (JSONException e) {
                queueMessageToSay("Error retreieving chat server endpoint URL");
            }
        }
    };

    // process the error result of a webQueryTask request
    public void webQueryError(String error) {
        queueMessageToSay("Got a bad response from periscope");
    };

    private String extractBroadcastID(String periscopeResponse) {
        int startOfVideoTag = periscopeResponse.indexOf(VIDEO_TAG);
        if (startOfVideoTag > 0) {
            int startOfId = startOfVideoTag + VIDEO_TAG.length();
            int endOfId = periscopeResponse.indexOf('&', startOfVideoTag);
            String idString = periscopeResponse.substring(startOfId, endOfId);
            queueMessageToSay("Got a video tag of " + idString);
            return(idString);
        }
        else
        {
            return(null);
        }
    }


    // queues a message that is of higher priority than tweets, will be dequeued in sayNext method
    private void queueMessageToSay(String msg) {
        messages.add(msg);
        if (!speaking) {
            sayNext();
        }
    }

    // Says the next enqueued message
    private void sayNext() {
        String speak_string = null;

        if (speaking) {
            return;
        }

        if (!messages.isEmpty()) {
            speak_string = messages.get(0);
            messages.remove(0);
            speaking = true;
            queuedMessageBeingSaid = speak_string;
            setMessageView(speak_string);
            ttsManager.initQueue(speak_string);
        }
    }


    // invoked by text to speech object when something is done being said
    //  Note: speech object doesn't run on UI thread, but the chat logic does, so we have to use 'runOnUiThread' invocation
    public void speechComplete () {
        speaking = false;
        ScopeSpeakerActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                queuedMessageBeingSaid = null;
                sayNext();
            }
        });
    }


    // put the desired message up on the display
    private void setMessageView(String s) {
        String html_page_string;
        html_page_string = "<html><body>"
                + "<h2><p align=\"justify\">" + s + "</p> " + "</h2></body></html>";
        messageView.loadData(html_page_string, "text/html; charset=utf-8", "UTF-8");
    }
}

