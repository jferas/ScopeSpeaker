package com.ferasinfotech.scopespeaker;

import android.os.Bundle;
import android.support.annotation.BoolRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ScopeSpeakerActivity extends AppCompatActivity {

    private final static String VIDEO_TAG = "https://www.pscp.tv/w/";

    private WebView      messageView = null;
    private WebQueryTask webQueryTask = null;
    private TTSManager   ttsManager = null;

    // storage for queue of messages that are spoken on a FIFO basis
    private final List<String> messages = new ArrayList<>();
    private String       queuedMessageBeingSaid = null;

    // state variable indicating whether speech is in progress or not
    private Boolean      speaking = false;

    TextView userNameText = null;


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
        String username = (String) userNameText.getText().toString();
        queueMessageToSay("Scope Speaker will look for a Periscope live stream by " + username);
        webQueryTask.execute("https://www.periscope.tv/" + username);
    }

    // process the successful result of a webQueryTask request
    public void webQueryResult(String response) {
        queueMessageToSay("Got a good response from periscope that is " + response.length() + " bytes long");
        int start_video_tag = response.indexOf(VIDEO_TAG);
        if (start_video_tag > 0) {
            int start_of_id = start_video_tag + VIDEO_TAG.length();
            int end_of_id = response.indexOf('&', start_video_tag);
            String id_string = response.substring(start_of_id, end_of_id);
            queueMessageToSay("Got a video tag of " + id_string);
        }
    };

    // process the error result of a webQueryTask request
    public void webQueryError(String error) {
        queueMessageToSay("Got a bad response from periscope");
    };

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

