package com.ferasinfotech.scopespeaker;

import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;

import android.content.ClipboardManager;
import android.widget.Toast;

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

    private State appState = State.AWAITING_USER_REQUEST;

    private long  lastTimeBackWasPressed = 0;

    private Boolean displaying_messages = true;

    // settings variables for room announcements
    private Boolean saying_joined_messages = false;
    private Boolean saying_left_messages = true;

    // settings variables for flow control (high/low water mark in the code 'Q Full' and 'Q Open' on the display)

    private int     highWaterMark = 10;
    private int     lowWaterMark = 5;
    private int     afterMsgDelay = 5;
    private Boolean droppingMessages = false;

    // text widgets for input parameters
    private TextView highWaterMarkText = null;
    private TextView lowWaterMarkText = null;
    private TextView afterMsgDelayText = null;

    // state variable indicating whether speech is in progress or not
    private Boolean      speaking = false;

    private WebView      messageView = null;
    private TTSManager   ttsManager = null;

    private Button       chatActionButton = null;
    private Button       joinMessagesButton = null;
    private Button       textDisplayButton = null;
    private Button       leftMessagesButton = null;

    private WebQueryTask userQueryTask = null;
    private WebQueryTask infoQueryTask = null;
    private WebQueryTask chatQueryTask = null;

    // timer variables
    private Handler     handler = null;
    private Runnable    the_runnable = null;
    private Handler     pause_handler = null;
    private Runnable    pause_runnable = null;


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

    // string to hold log of chat
    private String chatLog = "";

    // Voice variables
    List<String> availableVoices = null;
    String       currentVoice = null;

    // settings variables
    private Integer     secondsToWait = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createTextToSpeechManager();
        setContentView(R.layout.activity_scope_speaker);
        userNameText = (TextView) findViewById(R.id.username);
        highWaterMarkText = (TextView) findViewById(R.id.high_water_mark);
        lowWaterMarkText = (TextView) findViewById(R.id.low_water_mark);
        afterMsgDelayText = (TextView) findViewById(R.id.after_msg_pause);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        messageView = (WebView) findViewById(R.id.messageView);
        chatActionButton = (Button) findViewById(R.id.chat_action);
        textDisplayButton = (Button) findViewById(R.id.toggle_text_display);
        joinMessagesButton = (Button) findViewById(R.id.join_messages);
        leftMessagesButton = (Button) findViewById(R.id.left_messages);

        displayHelp();


        // keep keyboard from popping up at ap startup
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // restore the settings
        restoreSettings();

        /**** Some test code to allow JSON parsing to be tested from data in the clipboard
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        String response = clip.getItemAt(0).getText().toString();
        String result = extractChatMessage(response);
        setMessageView(result);
        *********/
    }

    // app shutdown - destroy allocated objects
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopChatProcessing(false);
        destroyTextToSpeechManager();
    }

    @Override
    public void onBackPressed() {
        if ( (lastTimeBackWasPressed + 5000L) > System.currentTimeMillis() ) {
            super.onBackPressed();
        }
        else {
            lastTimeBackWasPressed = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(),
                    "Press 'Back' again to shutdown ScopeSpeaker, " +
                    "or press the 'Home' button to run ScopeSpeaker in the background behind Periscope",
                    Toast.LENGTH_LONG).show();
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

    // display help text
    private void displayHelp() {
        setMessageView("ScopeSpeaker v0.31<br><br>"
                + "This is software under development and may have defects.. no warranty is expressed or implied.<br><br>"
                + "Enter your Periscope username and ScopeSpeaker will find your current "
                + "live stream when you are broadcasting, and run it in the background to read your viewers' chat messages aloud.<br><br>"
                + "You can also run ScopeSpeaker in split-screen mode as a companion app to Periscope, so you can change the preferences (see below) "
                + "while broadcasting.<br><br>"
                + "<u>Preferences:</u><br>"
                + "The 'Copy' button will cause the current chat messages to be copied to the Android clipboard.<br><br>"
                + "Tap the buttons to enable or disable the announcements of users joining or leaving the chats.<br><br>"
                + "The 'Disable Text' button will disable chat message text display (some jurisdictions fine for text on screen)<br><br>"
                + "'Queue Full' and 'Queue Open' values control when messages will stop being said (when the queue is deeper than 'Queue Full') "
                + "and when they will resume being said (when the queue gets as small as 'Queue Open'<br><br>"
                + "'Pause' refers to the delay after any message so the broadcaster can say something uninterrupted");
    }

    // update permanent storage with settings
    private void saveSettings() {
        if (currentVoice == null) {
            currentVoice = ttsManager.getCurrentVoice();
        }
        SharedPreferences settings = getPreferences(0);
        SharedPreferences.Editor editor = settings.edit();
        try {
            highWaterMark = Integer.parseInt(highWaterMarkText.getText().toString());
            lowWaterMark = Integer.parseInt(lowWaterMarkText.getText().toString());
            afterMsgDelay = Integer.parseInt(afterMsgDelayText.getText().toString());
            userName = (String) userNameText.getText().toString();

            editor.putInt("highWaterMark", highWaterMark);
            editor.putInt("lowWaterMark", lowWaterMark);
            editor.putInt("afterMsgDelay", afterMsgDelay);
            editor.putString("streamLocator", userName);
            editor.putBoolean("sayJoinedMessages", saying_joined_messages);
            editor.putBoolean("sayLeftMessages", saying_left_messages);
            editor.putString("currentVoice", currentVoice);
            editor.apply();
        }
        catch (NumberFormatException e) {
            Toast.makeText(getApplicationContext(), "Number formatting problem saving settings", Toast.LENGTH_SHORT).show();
        }
    }

    // restore settings from permanent storage
    private void restoreSettings() {
        SharedPreferences settings = getPreferences(0);
        highWaterMark = settings.getInt("highWaterMark", 10);
        lowWaterMark = settings.getInt("lowWaterMark", 5);
        afterMsgDelay = settings.getInt("afterMsgDelay", 0);
        userName = settings.getString("streamLocator", "Broadcaster Name");
        saying_joined_messages = settings.getBoolean("sayJoinedMessages", false);
        saying_left_messages = settings.getBoolean("sayLeftMessages", true);
        currentVoice = settings.getString("currentVoice", "unknown");

        highWaterMarkText.setText(Integer.toString(highWaterMark));
        lowWaterMarkText.setText(Integer.toString(lowWaterMark));
        afterMsgDelayText.setText(Integer.toString(afterMsgDelay));
        userNameText.setText(userName);

        if (saying_joined_messages) {
            joinMessagesButton.setText("Disable Joins");
        }
        else {
            joinMessagesButton.setText("Enable Joins");
        }

        if (saying_left_messages) {
            leftMessagesButton.setText("Disable Lefts");
        }
        else {
            leftMessagesButton.setText("Enable Lefts");
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
        if (id == R.id.help_menu_item) {
            displayHelp();
        }
        else if (id == R.id.change_voice_menu_item) {
            popupVoiceList();
        }
        return super.onOptionsItemSelected(item);
    }

    // toggle whether join messages are said or not
    public void toggleJoinMessages(View v) {
        String messageToSay;

        if (saying_joined_messages) {
            saying_joined_messages = false;
            joinMessagesButton.setText("Enable Joins");
            messageToSay = "Join messages have been disabled";
        }
        else {
            saying_joined_messages = true;
            joinMessagesButton.setText("Disable Joins");
            messageToSay = "Join messages have been enabled";
        }
        queueMessageToSay(messageToSay);
    }

    // toggle whether left messages are said or not
    public void toggleLeftMessages(View v) {
        String messageToSay;

        if (saying_left_messages) {
            saying_left_messages = false;
            leftMessagesButton.setText("Enable Lefts");
            messageToSay = "Left messages have been disabled";
        }
        else {
            saying_left_messages = true;
            leftMessagesButton.setText("Disable Lefts");
            messageToSay = "Left messages have been enabled";
        }
        queueMessageToSay(messageToSay);
    }

    // save the current chat to the Android clipboard
    public void saveChatToClipboard(View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(null, chatLog);
        clipboard.setPrimaryClip(clip);
        chatLog = "";
        Toast.makeText(getApplicationContext(), "Chat messages saved to clipboard", Toast.LENGTH_SHORT).show();
    }

    // toggle wheter any messages are displayed in the text window
    public void toggleTextDisplay(View v) {
        if (displaying_messages) {
            displaying_messages = false;
            setMessageView("");
            textDisplayButton.setText("Enable Text");
        }
        else {
            displaying_messages = true;
            setMessageView("Message display enabled");
            textDisplayButton.setText("Disable Text");
        }
    }

    // start or stop chat message processing in response to button press
    public void chatAction(View v) {
        if (appState != State.AWAITING_USER_REQUEST) {
            chatActionButton.setText("Say Periscope Messages of");
            stopChatProcessing(true);
        }
        else {
            chatActionButton.setText("Stop Saying Messages");
            startChatProcessing();
        }
    }

    // stop the chat message processing
    private void stopChatProcessing(Boolean makeAnnouncement) {
        saveSettings();
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
        if (pause_handler != null) {
            pause_handler.removeCallbacks(pause_runnable);
            pause_handler = null;
        }
        if ( (mConnection != null) && (mConnection.isConnected()) ) {
            disconnect();
        }
        if (makeAnnouncement) {
            messages.clear();
            queueMessageToSay("Chat messages stopped");
        }
        appState = State.AWAITING_USER_REQUEST;
    }

    // start the process of getting the chat messages for the specified user's live broadcast
    private void startChatProcessing() {
        int high_water_mark_orig = highWaterMark;
        int low_water_mark_orig = lowWaterMark;
        int after_msg_delay_orig = afterMsgDelay;
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
        if (pause_handler != null) {
            pause_handler.removeCallbacks(pause_runnable);
            pause_handler = null;
        }
        try {
            highWaterMark = Integer.parseInt(highWaterMarkText.getText().toString());
            lowWaterMark = Integer.parseInt(lowWaterMarkText.getText().toString());
            afterMsgDelay = Integer.parseInt(afterMsgDelayText.getText().toString());
            if (lowWaterMark < highWaterMark) {
                userQuery();
            }
            else {
                Toast.makeText(getApplicationContext(), "Low water mark must be less than high water mark", Toast.LENGTH_SHORT).show();
                highWaterMark = high_water_mark_orig;
                lowWaterMark = low_water_mark_orig;
                highWaterMarkText.setText(Integer.toString(highWaterMark));
                lowWaterMarkText.setText(Integer.toString(lowWaterMark));
            }
        }
        catch (NumberFormatException e) {
            Toast.makeText(getApplicationContext(), "Illegal values entered", Toast.LENGTH_SHORT).show();
            highWaterMark = high_water_mark_orig;
            lowWaterMark = low_water_mark_orig;
            afterMsgDelay = after_msg_delay_orig;
            highWaterMarkText.setText(Integer.toString(highWaterMark));
            lowWaterMarkText.setText(Integer.toString(lowWaterMark));
            afterMsgDelayText.setText(Integer.toString(afterMsgDelay));
        }
    }

    // send a query about the user named in the userNameText text object to the periscope web server
    private void userQuery() {
        userName = (String) userNameText.getText().toString();
        queueMessageToSay("Looking for a Periscope live stream by " + userName);
        appState = State.AWAITING_BROADCAST_ID;
        userQueryTask = new WebQueryTask();
        userQueryTask.init(this);
        userQueryTask.execute(PERISCOPE_URL + userName);
    }

    // schedule a user query to be run a given number of seconds from now
    private void schedulePeriscopeUserQuery(int seconds) {
        queueMessageToSay("Will check for live streams in " + seconds + " seconds");
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

    // process the successful result of a webQueryTask request (this drives app state forward through expected states)
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
                schedulePeriscopeUserQuery(secondsToWait);
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

            // For debugging only .. save raw chat message to chat log that can be sent to the clipboard
            //appendToChatLog("Raw chat message: " + response);

            // extract the name of the sender and the message they sent, and form a message to say
            String message_to_say = extractChatMessage(response);

            if (message_to_say != null) {
                appendToChatLog(message_to_say);
                queueMessageToSay(message_to_say);
            }
        }
    };

    // process the error result of a webQueryTask request .. schedule a new user query
    public void webQueryError(String error) {
        queueMessageToSay("Got a bad response from periscope: " + error);
        ScopeSpeakerActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                schedulePeriscopeUserQuery(secondsToWait);
            }
        });
    };

    // append a chat message to the running chat log
    private void appendToChatLog(String chatMessage) {
        android.text.format.DateFormat df = new android.text.format.DateFormat();
        String time_stamp = (String) df.format("yyyy-MM-dd hh:mm:ss a", new java.util.Date());
        chatLog += time_stamp + "  " + chatMessage + "\n";
    }

    // extract a chat message from a JSON packet sent by the Periscope chat server
    private String extractChatMessage(String chatString) {
        try {
            JSONObject chatMessage = new JSONObject(chatString);
            int kind = chatMessage.getInt("kind");
            String payloadString = chatMessage.getString("payload");
            JSONObject payload = new JSONObject(payloadString);
            String what_they_said = "";
            String who_said_it = null;
            if (kind == 1) {
                try {
                    String bodyString = payload.getString("body");
                    JSONObject outerBody = new JSONObject(bodyString);
                    what_they_said = outerBody.getString("body");
                    String senderString = payload.getString("sender");
                    JSONObject sender = new JSONObject(senderString);
                    who_said_it = sender.getString("username");
                    if (what_they_said.equals("joined")) {
                        //Log.i(TAG, "got a textual join message:" + chatString);
                        return null;
                    }
                } catch (JSONException e) {
                    // missing inner tags is not an error but is not a good chat message
                    return null;
                }
            }
            else if (kind == 2) {
                int payloadKind = payload.getInt("kind");
                String senderString = payload.getString("sender");
                JSONObject sender = new JSONObject(senderString);
                if (payloadKind == 1) {
                    if (saying_joined_messages) {
                        who_said_it = sender.getString("username");
                        what_they_said = "joined";
                        //Log.i(TAG, "got an encoded join message:" + chatString);
                    }
                    else {
                        String message_for_chatlog = sender.getString("username") + " joined";
                        appendToChatLog(message_for_chatlog);
                    }
                }
                else if (payloadKind == 2) {
                    if (saying_left_messages) {
                        who_said_it = sender.getString("username");
                        what_they_said = "left";
                        //Log.i(TAG, "got an encoded left message:" + chatString);
                    }
                    else {
                        String message_for_chatlog = sender.getString("username") + " left";
                        appendToChatLog(message_for_chatlog);
                    }
                }
            }

            String chat_message = null;
            if (who_said_it != null) {
                if ( (what_they_said.equals("left")) || (what_they_said.equals("joined"))) {
                    chat_message = who_said_it + " " + what_they_said;
                } else {
                    chat_message = who_said_it + " said: " + what_they_said;
                }
            }
            return chat_message;
        }
        catch (JSONException e) {
            // missing payload is a JSON syntactic error and should be logged
            Log.i(TAG, "chat parse exception when parsing message payload:" + e.getMessage());
            Toast.makeText(getApplicationContext(), "Chat message payload parse error", Toast.LENGTH_SHORT).show();
            queuePriorityMessageToSay("Chat message payload parse error");
            appendToChatLog("Payload parse error: " + chatString);
            return null;
        }
    }

    // extract a broadcast ID for the first broadcast of a user's list of broadcasts returned by the user query
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


    // puts a message at the front of the queue to be heard immediately (or as soon as current speech finishes)
    private void queuePriorityMessageToSay(String msg) {
        messages.add(0, msg);
        if (!speaking) {
            sayNext();
        }
    }

    // queues a message that will be dequeued in sayNext method
    private void queueMessageToSay(String msg) {
        if (droppingMessages) {
            return;
        }

        messages.add(msg);
        int queue_size = messages.size();

        if (queue_size == highWaterMark) {
            // we've fallen behine and need to stop saying messages, put fall behind msg at the front of the queue so it is heard immediately
            String the_message = "Scope Speaker un-said queue has " + highWaterMark
                    + " messages, new messages won't be said till queue is down to " + lowWaterMark;
            messages.add(0, the_message);
            appendToChatLog(the_message);
            Toast.makeText(getApplicationContext(), the_message,
                    Toast.LENGTH_SHORT).show();
            droppingMessages = true;
        }
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

        if (ttsManager == null) {
            return;
        }

        // if there was not a setting for current voice (first run) use default from TTS manager
        // Also.. since the ttsManager might not be initialized when settings are restored at start
        // verify that the voice is set to that specified by settings just before we talk

        String active_voice = ttsManager.getCurrentVoice();
        if ( currentVoice.equals("unknown") ) {
            currentVoice = active_voice;
        }

        // if the selected voice is the first index (Use all voices) then switch to next voice in list
        if (!currentVoice.equals(active_voice)) {
            String new_voice = currentVoice;
            if (availableVoices == null) {
                availableVoices = ttsManager.getAvailableVoicesForLanguage();
            }
            if (availableVoices.get(0).equals(currentVoice)) {
                Integer vi = availableVoices.indexOf(active_voice);
                vi++;
                if (vi >= availableVoices.size()) {
                    vi = 1;
                }
                new_voice = availableVoices.get(vi);
            }
            ttsManager.setVoice(new_voice);
        }

        if (!messages.isEmpty()) {
            speaking = true;
            speak_string = messages.get(0);
            messages.remove(0);
            if ( (droppingMessages) && (messages.size()) == lowWaterMark) {
                // we're crossing back to the low water mark, allow saying new messages, announce we're doing so, and put msg back in queue
                droppingMessages = false;
                messages.add(0, speak_string);
                speak_string = "Scope Speaker has recovered the un-said message queue down to " + lowWaterMark + ", new messages will resume being said";
                appendToChatLog(speak_string);
                Toast.makeText(getApplicationContext(), speak_string, Toast.LENGTH_SHORT).show();
            }
            queuedMessageBeingSaid = speak_string;
            setMessageView(speak_string);
            if (ttsManager != null) {
                ttsManager.initQueue(speak_string);
            }
        }
    }


    // invoked by text to speech object when something is done being said
    // if a delay is desired after each message the timer is kicked off here to schedule the next message,
    //   otherwise the next message is kicked off.
    //  Note: speech object doesn't run on UI thread, but the chat logic does, so we have to use 'runOnUiThread' invocation
    public void speechComplete () {
        if (afterMsgDelay != 0) {
            SystemClock.sleep(afterMsgDelay * 1000);
        }
        ScopeSpeakerActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                speaking = false;
                queuedMessageBeingSaid = null;
                sayNext();
            }
        });
    }


    // put the desired message up on the display
    private void setMessageView(String s) {
        String html_page_string;
        if (displaying_messages) {
            html_page_string = "<html><body>"
                    + "<h2><p align=\"justify\">" + s + "</p> " + "</h2></body></html>";
        }
        else {
            html_page_string = "<html><html>";
        }
        messageView.loadData(html_page_string, "text/html; charset=utf-8", "UTF-8");
    }

    // popup a choice list of possible voices to use
    private void popupVoiceList() {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(ScopeSpeakerActivity.this);
        //builderSingle.setIcon(R.drawable.ic_launcher);
        builderSingle.setTitle("Current Voice:" + currentVoice);

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(ScopeSpeakerActivity.this,
                android.R.layout.select_dialog_singlechoice);

        availableVoices = ttsManager.getAvailableVoicesForLanguage();
        arrayAdapter.addAll(availableVoices);

        builderSingle.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                currentVoice = arrayAdapter.getItem(which);
                Log.e(TAG, "Selected:" + currentVoice);
                dialog.dismiss();
            }
        });
        builderSingle.show();
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
        mConnection = null;
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

