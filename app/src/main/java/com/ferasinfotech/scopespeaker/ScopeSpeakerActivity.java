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

//jjf
//import org.java_websocket.drafts.Draft_75;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//import org.java_websocket.drafts.Draft_17;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;


import java.net.URI;
import java.net.URISyntaxException;

public class ScopeSpeakerActivity extends AppCompatActivity implements WebSocketConnectionObserver {

    private static final String TAG = ScopeSpeakerActivity.class.getName();

    private final static String PERISCOPE_URL = "https://www.periscope.tv/";
    private final static String PERISCOPE_BROACAST_INFO_URL = "https://api.periscope.tv/api/v2/accessVideoPublic?broadcast_id=";
    private final static String PERISCOPE_CHAT_ACCESS_URL = "https://api.periscope.tv/api/v2/accessChatPublic?chat_token=";

    private final static String VIDEO_TAG = "https://www.pscp.tv/w/";
    private final static String JSON_TAG_BROADCAST = "broadcast";
    private final static String JSON_TAG_VIDEO_STATE = "state";
    private final static String JSON_TAG_URL_CHAT_TOKEN = "chat_token";
    private final static String JSON_TAG_CHAT_ACCESS_TOKEN = "access_token";
    private final static String JSON_TAG_ENDPOINT_URL = "endpoint";

    private enum State {AWAITING_USER_REQUEST, AWAITING_BROADCAST_ID, AWAITING_CHAT_ACCESS_TOKEN, AWAITING_CHAT_ENDPOINT,
                        AWAITING_WEBSOCKET_CONNECTION, AWAITING_CHAT_MESSAGES};

    private State appState = null;

    // state variable indicating whether speech is in progress or not
    private Boolean      speaking = false;

    private WebView      messageView = null;
    private TTSManager   ttsManager = null;

    private WebQueryTask userQueryTask = null;
    private WebQueryTask infoQueryTask = null;
    private WebQueryTask chatQueryTask = null;

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
    private String chatURLAccessToken = null;

    // chat access token fetched from JSON response and fed to websocket on handshake
    private String chatAccessToken = null;

    // endpoint URL for websocket connection to establish for chat messages
    private String endpointURL = null;

    // JSON message strings for initial handshake with chat server over websocket
    private String joinJsonMessage = null;
    private String authJsonMessage = null;

    // web socket client for communicating with the Periscope chat server
    // commented out by jjf private WebSocketClient mWebSocketClient;
    private WebSocketConnection mConnection;


    // settings variables
    private Integer     secondsToWait = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createTextToSpeechManager();

        setContentView(R.layout.activity_scope_speaker);
        userNameText = (TextView) findViewById(R.id.username);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        messageView = (WebView) findViewById(R.id.messageView);
        setMessageView("ScopeSpeaker v0.1<br><br>Enter Periscope username and ScopeSpeaker will find their live stream, and read the stream chat messages aloud.");
    }

    // app shutdown - destroy allocated objects
    @Override
    public void onDestroy() {
        super.onDestroy();
        destroyTextToSpeechManager();
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
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
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
        userQuery();
    }

    private void userQuery() {
        userName = (String) userNameText.getText().toString();
        queueMessageToSay("Looking for a Periscope live stream by " + userName);
        appState = State.AWAITING_BROADCAST_ID;
        userQueryTask = new WebQueryTask();
        userQueryTask.init(this);
        userQueryTask.execute(PERISCOPE_URL + userName);
    }

    private void schedulePeriscopeUserQuery(int seconds) {
        queueMessageToSay("Will check for more live streams in " + seconds + " seconds");
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
        handler = new Handler();
        the_runnable = new Runnable() {
            @Override
            public void run() {
                //Query Periscope for user again after 'secondsToWait' seconds (converted to ms)
                handler = null;
                userQuery();
            }
        };
        handler.postDelayed(the_runnable, seconds * 1000);
    }

    // process the successful result of a webQueryTask request
    public void webQueryResult(String response) {
        if (appState == State.AWAITING_BROADCAST_ID) {
            if (userQueryTask != null) {
                userQueryTask.cancel(true);
                userQueryTask = null;
            }
            broadcastID = extractBroadcastID(response);
            if (broadcastID != null) {
                appState = State.AWAITING_CHAT_ACCESS_TOKEN;
                infoQueryTask = new WebQueryTask();
                infoQueryTask.init(this);
                infoQueryTask.execute(PERISCOPE_BROACAST_INFO_URL + broadcastID);
            }
            else {
                queueMessageToSay(userName + " has no broadcasts");
            }
        }
        else if (appState == State.AWAITING_CHAT_ACCESS_TOKEN) {
            if (infoQueryTask != null) {
                infoQueryTask.cancel(true);
                infoQueryTask = null;
            }
            try {
                JSONObject infoJsonResponse = new JSONObject(response);
                JSONObject bcastJsonObject = infoJsonResponse.getJSONObject(JSON_TAG_BROADCAST);
                String video_state = bcastJsonObject.getString(JSON_TAG_VIDEO_STATE);
                if (video_state.equals("RUNNING")) {
                    chatURLAccessToken = infoJsonResponse.getString(JSON_TAG_URL_CHAT_TOKEN);
                    appState = State.AWAITING_CHAT_ENDPOINT;
                    chatQueryTask = new WebQueryTask();
                    chatQueryTask.init(this);
                    chatQueryTask.execute(PERISCOPE_CHAT_ACCESS_URL + chatURLAccessToken);
                }
                else {
                    queueMessageToSay(userName + " is not live streaming at the moment");
                    schedulePeriscopeUserQuery(secondsToWait);
                }
            }
            catch (JSONException e) {
                queueMessageToSay(userName + " is not live streaming at the moment");
                schedulePeriscopeUserQuery(secondsToWait);
            }
        }
        else if (appState == State.AWAITING_CHAT_ENDPOINT) {
            if (chatQueryTask != null) {
                chatQueryTask.cancel(true);
                chatQueryTask = null;
            }
            try {
                JSONObject infoJsonResponse = new JSONObject(response);
                chatAccessToken = infoJsonResponse.getString(JSON_TAG_CHAT_ACCESS_TOKEN);
                endpointURL = infoJsonResponse.getString(JSON_TAG_ENDPOINT_URL);
                endpointURL += "/chatapi/v1/chatnow";
                if (endpointURL.substring(0, 6).equals("https:")) {
                    endpointURL = endpointURL.replace("https:", "wss:");
                }
                else {
                    endpointURL = endpointURL.replace("http:", "ws:");
                }
                joinJsonMessage = "{\"kind\":2,\"payload\":\"{\\\"kind\\\":1,\\\"body\\\":\\\"{\\\\\\\"room\\\\\\\":\\\\\\\"replace_this\\\\\\\"}\\\"}\"}";
                authJsonMessage = "{\"kind\":3,\"payload\":\"{\\\"access_token\\\":\\\"replace_this\\\"}\"}";
                joinJsonMessage = joinJsonMessage.replace("replace_this", broadcastID);
                authJsonMessage = authJsonMessage.replace("replace_this", chatAccessToken);
                establishWebSocket(endpointURL);
                appState = State.AWAITING_WEBSOCKET_CONNECTION;
            }
            catch (JSONException e) {
                queueMessageToSay("Error retreieving chat server endpoint URL");
                appState = State.AWAITING_USER_REQUEST;
            }
        }
        else if (appState == State.AWAITING_WEBSOCKET_CONNECTION) {
            if (response.equals("connected")) {
                queueMessageToSay("Listening for chat messages");
                appState = State.AWAITING_CHAT_MESSAGES;
            }
            else {
                queueMessageToSay("Error connecting to periscope chat message server");
                appState = State.AWAITING_USER_REQUEST;
            }

        }
        else if (appState == State.AWAITING_CHAT_MESSAGES) {
            try {
                JSONObject chatMessage = new JSONObject(response);
                int kind = chatMessage.getInt("kind");
                if (kind == 1) {
                    response = response.replaceAll("\\\\", "");
                    response = response.replace("\"{", "{");
                    response = response.replace("}\"", "}");
                    extractAndSayMessage(response);

/*
   this is how it should have been done with proper JSON parsing
   but because Periscope sends back data with lots of weird backslashes, we had
   to do the call to extractAndSayMessage, which does raw sring parsing to get its data.
   The current approach is pretty brittle, so we should find out why all the emedded  backslashes

                    chatMessage = new JSONObject(response);
                    JSONObject payload = chatMessage.getJSONObject("payload");
                    JSONObject body = payload.getJSONObject("body");
                    String msg = body.getString("body");
*/
                }
            }
            catch (JSONException e) {
                Log.i(TAG, "chat parse exception:" + e.getMessage());
                Log.i(TAG, "we broke");
            }
        }
    };

    // process the error result of a webQueryTask request
    public void webQueryError(String error) {
        appState = State.AWAITING_USER_REQUEST;
        queueMessageToSay("Got a bad response from periscope: " + error);
    };

    private void extractAndSayMessage(String msg) {
        int firstbody = msg.indexOf("body");
        if (firstbody > 0) {
            int secondbody = msg.indexOf("body", firstbody + 6);
            if (secondbody > 0) {
                Log.d(TAG, "Chat message:" + msg);
                int sentence_start = secondbody + 7;
                int sentence_end = msg.indexOf('"', sentence_start + 1);
                String whatTheySaid = msg.substring(sentence_start, sentence_end);
                int displayname_start = msg.indexOf("display_name", sentence_end);
                if (displayname_start > 0) {
                    int name_start = displayname_start + 15;
                    int name_end = msg.indexOf('"', name_start + 1);
                    String whoSaidIt = msg.substring(name_start, name_end);
                    queueMessageToSay(whoSaidIt + " said: " + whatTheySaid);
                }

            }
        }
    }
    private String extractBroadcastID(String periscopeResponse) {
        int startOfVideoTag = periscopeResponse.indexOf(VIDEO_TAG);
        if (startOfVideoTag > 0) {
            int startOfId = startOfVideoTag + VIDEO_TAG.length();
            int endOfId = periscopeResponse.indexOf('&', startOfVideoTag);
            String idString = periscopeResponse.substring(startOfId, endOfId);
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


    private void establishWebSocket(String chatServerURL) {
        this.mConnection = new WebSocketConnection();

        URI uri;
        try {
            uri = new URI(chatServerURL);
            mConnection.connect(uri, this);
        } catch (URISyntaxException e) {
            String message = e.getLocalizedMessage();
            Log.e(TAG, message);
            webQueryError(message);
        } catch (WebSocketException e) {
            String message = e.getLocalizedMessage();
            Log.e(TAG, message);
            webQueryError(message);
        }
    }

    public void disconnect() {
        mConnection.disconnect();
    }

    //
    // WebSocket Handler callbacks
    @Override
    public void onOpen() {
        String message = "Connection opened to: " + endpointURL;
        Log.d(TAG, message);
        mConnection.sendTextMessage(authJsonMessage);
        mConnection.sendTextMessage(joinJsonMessage);
        webQueryResult("connected");
    }

    @Override
    public void onClose(WebSocketCloseNotification code, String reason) {
        this.mConnection = null;

        queueMessageToSay("Chat server connection closed, attempting to reconnect");
        schedulePeriscopeUserQuery(2);
    }

    @Override
    public void onTextMessage(String payload) {
        webQueryResult(payload);
    }

    @Override
    public void onRawTextMessage(byte[] payload) {
    }

    @Override
    public void onBinaryMessage(byte[] payload) {
    }

}

