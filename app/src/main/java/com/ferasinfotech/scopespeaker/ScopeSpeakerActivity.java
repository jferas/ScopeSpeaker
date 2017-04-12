package com.ferasinfotech.scopespeaker;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

public class ScopeSpeakerActivity extends AppCompatActivity {

    WebView      messageView = null;
    WebQueryTask webQueryTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webQueryTask = new WebQueryTask();
        webQueryTask.init(this, this);

        setContentView(R.layout.activity_scope_speaker);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        messageView = (WebView) findViewById(R.id.messageView);
        setMessageView("Enter a Periscope user's name and ScopeSpeaker will attempt to find their current live broadcast, and audibly read the broadcast's chat messages to you.");
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

    // process the successful result of a webQueryTask request
    public void webQueryResult(String response) {
    };

    // process the error result of a webQueryTask request
    public void webQueryError(String error) {
    };

    // put the desired message up on the display
    private void setMessageView(String s) {
        String html_page_string;
        html_page_string = "<html><body>"
                + "<h2><p align=\"justify\">" + s + "</p> " + "</h2></body></html>";
        messageView.loadData(html_page_string, "text/html; charset=utf-8", "UTF-8");
    }
}

