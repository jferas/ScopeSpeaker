package com.ferasinfotech.scopespeaker;

import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.SeekBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

import java.net.URI;
import java.net.URISyntaxException;

import android.content.ClipboardManager;
import android.widget.Toast;

public class ScopeSpeakerActivity extends AppCompatActivity implements WebSocketConnectionObserver {

    private static final String TAG = ScopeSpeakerActivity.class.getName();

    private final static String PERISCOPE_URL = "https://www.periscope.tv/";
    private final static String PERISCOPE_BROACAST_INFO_URL = "https://api.periscope.tv/api/v2/accessVideoPublic?broadcast_id=";
    private final static String PERISCOPE_USER_BROADCAST_LIST_URL
            = "https://api.periscope.tv/api/v2/getUserBroadcastsPublic?user_id=replace_with_user_id&all=true&session_id=replace_with_session_id";
    private final static String PERISCOPE_CHAT_ACCESS_URL = "https://api.periscope.tv/api/v2/accessChatPublic?chat_token=";

    private  final static String SESSION_TAG
            = "public&quot;:{&quot;broadcastHistory&quot;:{&quot;token&quot;:{&quot;session_id&quot;:&quot;";

    private final static String USER_TAG = ",&quot;usernames&quot;:{&quot;replace_this&quot;:&quot;";

    private final static String VIDEO_TAG = "https://www.pscp.tv/w/";
    private final static String PSCP_TAG = "pscp://broadcast/";
    private final static String JSON_TAG_BROADCAST = "broadcast";
    private final static String JSON_TAG_BROADCASTS = "broadcasts";
    private final static String JSON_TAG_ID = "id";
    private final static String JSON_TAG_VIDEO_STATE = "state";
    private final static String JSON_TAG_BROADCAST_SOURCE = "broadcast_source";
    private final static String JSON_TAG_USERNAME = "username";
    private final static String JSON_TAG_URL_CHAT_TOKEN = "chat_token";
    private final static String JSON_TAG_CHAT_ACCESS_TOKEN = "access_token";
    private final static String JSON_TAG_ENDPOINT_URL = "endpoint";

    private enum State {AWAITING_USER_REQUEST, AWAITING_BROADCAST_LIST, AWAITING_BROADCAST_ID, AWAITING_CHAT_ACCESS_TOKEN, AWAITING_CHAT_ENDPOINT,
                        AWAITING_WEBSOCKET_CONNECTION, AWAITING_CHAT_MESSAGES};

    private State appState = State.AWAITING_USER_REQUEST;

    private long  lastTimeBackWasPressed = 0;

    private Boolean displaying_messages = true;

    // settings variables for room announcements
    private Boolean saying_joined_messages = false;
    private Boolean saying_left_messages = false;
    private Boolean saying_emojis = false;
    private Boolean saying_translations = true;
    private Boolean saying_display_names = true;

    // settings variables for volume, flow control (high/low water mark in the code 'Q Full' and 'Q Open' on the display)
    //  as well as pause after message delay, and flag the messages are being dropped from the queue
    //  and the length of a string that triggers auto language detection.

    private int     speechVolume = 100;
    private int     nameLength = 12;
    private int     highWaterMark = 10;
    private int     lowWaterMark = 5;
    private int     afterMsgDelay = 5;
    private Boolean droppingMessages = false;
    private int     detectLength = 20;

    // SeekBar widgets for input parameters
    private SeekBar speechVolumeSeekBar = null;
    private SeekBar nameLengthSeekBar = null;
    private SeekBar highWaterMarkSeekBar = null;
    private SeekBar lowWaterMarkSeekBar = null;
    private SeekBar afterMsgDelaySeekBar = null;
    private SeekBar detectLengthSeekBar = null;

    // Text widgets to hold seekbar values
    private TextView speechVolumeText = null;
    private TextView nameLengthText = null;
    private TextView highWaterMarkText = null;
    private TextView lowWaterMarkText = null;
    private TextView afterMsgDelayText = null;
    private TextView detectLengthText = null;

    // state variable indicating whether speech is in progress or not
    private Boolean      speaking = false;

    // state variable indicating if settings view is up on the display or not
    private Boolean      settingsViewIsUp = false;

    // View objects for main view and settings view
    private View         mainView = null;
    private View         settingsView = null;


    private WebView      messageView = null;
    private TTSManager   ttsManager = null;

    private Button       chatActionButton = null;
    private Switch       joinMessagesSwitch = null;
    private Switch       textDisplaySwitch = null;
    private Switch       leftMessagesSwitch = null;
    private Switch       emojiSwitch = null;
    private Switch       translationsSwitch = null;
    private Switch       displayNameSwitch = null;

    private WebQueryTask userQueryTask = null;
    private WebQueryTask infoQueryTask = null;
    private WebQueryTask chatQueryTask = null;

    private TranslatorBackgroundTask translatorBackgroundTask = null;

    // the words "said" and "translated" in the device native language
    //  TODO: when the default language is not english, hit translation service to get these words
    //  TODO: in the default language
    private String saidWord = "said";
    private String translatedWord = "translated";

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

    // broadcast source software (iOS, Android, etc) fetched from URL that gives individual broadcast info
    private String broadcastSource = null;

    // broadcast user name fetched from URL that gives individual broadcast info
    private String broadcastUsername = null;

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
    String       defaultLanguage = null;

    // timer variable for delay between user web query retries
    private Integer     secondsToWait = 30;

    // the shared Periscope URL
    private String sharedUrl = null;

    List<String> cannedBotWords = Arrays.asList("Holla", "Halloo", "Hej", "Regard", "Ciao", "Merhaba");

    List<String> botWords = new ArrayList<>(cannedBotWords);

    List<String> knownBots = new ArrayList<String>();

    String web_response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        super.onCreate(savedInstanceState);

        createTextToSpeechManager();

        setContentView(R.layout.activity_scope_speaker);
        mainView = (View) findViewById(R.id.main_display);
        settingsView = (View) findViewById(R.id.settings_display);
        userNameText = (TextView) findViewById(R.id.username);

        speechVolumeText = (TextView) findViewById(R.id.speech_volume);
        nameLengthText = (TextView) findViewById(R.id.name_length);
        highWaterMarkText = (TextView) findViewById(R.id.high_water_mark);
        lowWaterMarkText = (TextView) findViewById(R.id.low_water_mark);
        afterMsgDelayText = (TextView) findViewById(R.id.after_msg_pause);
        detectLengthText = (TextView) findViewById(R.id.detect_length);

        speechVolumeSeekBar = (SeekBar) findViewById(R.id.speech_volume_seekbar);
        nameLengthSeekBar = (SeekBar) findViewById(R.id.name_length_seekbar);
        highWaterMarkSeekBar = (SeekBar) findViewById(R.id.high_water_mark_seekbar);
        lowWaterMarkSeekBar = (SeekBar) findViewById(R.id.low_water_mark_seekbar);
        afterMsgDelaySeekBar = (SeekBar) findViewById(R.id.after_msg_pause_seekbar);
        detectLengthSeekBar = (SeekBar) findViewById(R.id.detect_length_seekbar);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        messageView = (WebView) findViewById(R.id.messageView);
        chatActionButton = (Button) findViewById(R.id.chat_action);
        textDisplaySwitch = (Switch) findViewById(R.id.toggle_text_display);
        joinMessagesSwitch = (Switch) findViewById(R.id.join_messages);
        leftMessagesSwitch = (Switch) findViewById(R.id.left_messages);
        emojiSwitch = (Switch) findViewById(R.id.emoji_messages);
        translationsSwitch = (Switch) findViewById(R.id.translations);
        displayNameSwitch = (Switch) findViewById(R.id.display_names);


        mainView.setVisibility(View.VISIBLE);
        settingsView.setVisibility(View.GONE);

        // keep keyboard from popping up at ap startup
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // restore the settings
        restoreSettings();

        //attach a listener to check for changes in join messages state
        joinMessagesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                saying_joined_messages = isChecked;
                if (isChecked) {
                    queueMessageToSay("Join messages have been enabled");
                }
                else {
                    queueMessageToSay("Join messages have been disabled");
                }
            }
        });

        //attach a listener to check for changes in left messages state
        leftMessagesSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                saying_left_messages = isChecked;
                if (isChecked) {
                    queueMessageToSay("Left messages have been enabled");
                }
                else {
                    queueMessageToSay("Left messages have been disabled");
                }
            }
        });

        //attach a listener to check for changes in text display state
        textDisplaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                String messageToSay;
                displaying_messages = isChecked;
                if (isChecked) {
                    setMessageView("Text display enabled");
                }
                else {
                    setMessageView("Text display disabled");
                }
            }
        });

        //attach a listener to check for changes in text display state
        emojiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                saying_emojis = isChecked;
                if (isChecked) {
                    setMessageView("Emojis are enabled");
                }
                else {
                    setMessageView("Emojis are disabled");
                }
            }
        });

        //attach a listener to check for changes in text display state
        translationsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                saying_translations = isChecked;
                if (isChecked) {
                    setMessageView("Translations are enabled");
                }
                else {
                    setMessageView("Translations are disabled");
                }
            }
        });

        //attach a listener to check for changes in text display state
        displayNameSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                saying_display_names = isChecked;
                if (isChecked) {
                    setMessageView("Display Names are enabled");
                }
                else {
                    setMessageView("User Names are enabled");
                }
            }
        });

        speechVolumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                speechVolume = progresValue;
                speechVolumeText.setText(Integer.toString(speechVolume));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        nameLengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                nameLength = progresValue;
                nameLengthText.setText(Integer.toString(nameLength));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        highWaterMarkSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                highWaterMark = progresValue;
                highWaterMarkText.setText(Integer.toString(highWaterMark));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        lowWaterMarkSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                lowWaterMark = progresValue;
                lowWaterMarkText.setText(Integer.toString(lowWaterMark));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        afterMsgDelaySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                afterMsgDelay = progresValue;
                afterMsgDelayText.setText(Integer.toString(afterMsgDelay));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        detectLengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                detectLength = progresValue;
                detectLengthText.setText(Integer.toString(detectLength));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        speechVolumeSeekBar.setProgress(speechVolume);
        nameLengthSeekBar.setProgress(nameLength);
        highWaterMarkSeekBar.setProgress(highWaterMark);
        lowWaterMarkSeekBar.setProgress(lowWaterMark);
        afterMsgDelaySeekBar.setProgress(afterMsgDelay);
        detectLengthSeekBar.setProgress(detectLength);

        determineLaunchMethod(intent);

    } // onCreate

    // received a new intent, process the re-launch
    @Override
    public void onNewIntent(Intent newIntent) {
        stopChatProcessing(false);
        determineLaunchMethod(newIntent);
    }

    // app shutdown - destroy allocated objects
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopChatProcessing(false);
        destroyTextToSpeechManager();
    }

    private void determineLaunchMethod(Intent theIntent) {
        String action = theIntent.getAction();
        String type = theIntent.getType();
        sharedUrl = null;
        userNameText.setVisibility(View.VISIBLE);
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                sharedUrl = theIntent.getStringExtra(Intent.EXTRA_TEXT);
                if ((sharedUrl != null) && (sharedUrl.contains("https://www.pscp.tv"))) {
                    // launched via send action intent containing URL of periscope stream .. get broadcast ID from it
                    moveTaskToBack(true);
                    userNameText.setVisibility(View.GONE);
                    chatActionButton.setText("Stop Saying Messages");
                    setMessageView("Launched on request of Periscope via shared broadcast URL");
                    schedulePeriscopeSetupQuery(1);
                }
            } else {
                queueMessageToSay("ScopeSpeaker received something that was not a Periscope broadcast URL");
            }
        }
        else {
            // normal launch put up help text
            displayHelp();
        }
    }

    @Override
    public void onBackPressed() {
        if (settingsViewIsUp) {
            mainView.setVisibility(View.VISIBLE);
            settingsView.setVisibility(View.GONE);
            ttsManager.setVolume(speechVolume);
            settingsViewIsUp = false;
        }
        else if ( (lastTimeBackWasPressed + 5000L) > System.currentTimeMillis() ) {
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

    // skip the current message being said
    public void skipMessage(View v) {
        destroyTextToSpeechManager();
        setMessageView("Skipping message...");
        speaking = false;
        createTextToSpeechManager();
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
        settingsView.setVisibility(View.GONE);
        mainView.setVisibility(View.VISIBLE);
        setMessageView(
                  "As a viewer, use the Periscope 'Share Broadcast' and 'Share To' menu options, choosing ScopeSpeaker to tell it to run in the background to say (and translate) the chat messages of the current stream aloud.<br><br>"
                + "As a Periscope broadcaster, enter your Periscope user name before broadcasting, and tap the 'Say..' button to start ScopeSpeaker listening for your broadcast to begin (it will retry every 30 seconds).  Then put ScopeSpeaker in the background, and start your broadcast.<br><br>"
                + "While ScopeSpeaker is running in the background, it is continuously listening to the chat messages of the Periscope stream, saying them aloud and translating them if necessary.<br><br>"
                + "ScopeSpeaker can also be run in split-screen mode as a companion app to Periscope.<br><br>"
                + "Split screen mode allows ScopeSpeaker settings and preferences to be changed while broadcasting. ScopeSpeaker can also be run on a separate device for that purpose.<br><br>"
                + "The 'Copy To Clipboard' button will cause the current chat messages to be copied to the Android clipboard.<br><br>"
                + "The 'Skip Message' button will immediately cause the current chat message to be aborted and the next message in the queue (if present) will be said.<br><br>"
                + "The 'Change Voice' menu option allows ScopeSpeaker to use any of the voices installed in your Android device. You can install additional voices with a variety of accents via the Android 'Language and Input' / 'Text to Speech' settings.<br><br>"
                + "<u>Preferences:</u><br><br>"
                + "Slide the switches to enable or disable the announcements of users joining or leaving the chats.<br><br>"
                + "The 'Text Display' switch will disable chat message text display (to avoid distractions).<br><br>"
                + "The 'Emojis' switch will disable the pronouncement of emojis in messages.<br><br>"
                + "<u>Settings:</u><br><br>"
                + "The 'Translations' switch will enable or disable the translation of chat messages into the default language of the ScopeSpeaker user's device.<br><br>"
                + "The 'DisplayNames' switch will enable the saying viewers' more human sounding DisplayName instead of their unique UserName.<br><br>"
                + "'Speech Volume' controls the audio level of the spoken messages relative to other audio outputs of the device (such as music).<br><br>"
                + "'Name Length' controls the length (in characters) of the chat message sender's name when spoken (0 means the sender name will not be said).<br><br>"
                + "'Pause' refers to the delay after any message so the broadcaster can say something uninterrupted. The speed at which messages are said can be controlled via Android Text-to-Speech settings.<br><br>"
                + "'Detect Length' is the number of characters that will trigger auto detection of language for translations.  Any message shorter than that will assume the sender's language as indicated by Periscope.<br><br>"
                + "'Queue Full' and 'Queue Open' values control when messages will stop being said (when the queue is deeper than 'Queue Full')."
                + "and when they will resume being said (when the queue gets as small as 'Queue Open'<br><br>"
                + "Translations powered by <a href=\"http://translate.yandex.com/\">Yandex.Translate</a><br><br>"
                + "ScopeSpeaker v0.54<br><br>"
                + "Disclaimer: ScopeSpeaker is a free app, and is provided 'as is'. No guarantee is made related to the consistency of the app's performance with the User’s goals and expectations.");
    }

    // update permanent storage with settings
    private void saveSettings() {
        if (currentVoice == null) {
            currentVoice = ttsManager.getCurrentVoice();
        }
        SharedPreferences settings = getPreferences(0);
        SharedPreferences.Editor editor = settings.edit();
        try {
            speechVolume = Integer.parseInt(speechVolumeText.getText().toString());
            nameLength = Integer.parseInt(nameLengthText.getText().toString());
            highWaterMark = Integer.parseInt(highWaterMarkText.getText().toString());
            lowWaterMark = Integer.parseInt(lowWaterMarkText.getText().toString());
            afterMsgDelay = Integer.parseInt(afterMsgDelayText.getText().toString());
            detectLength = Integer.parseInt(detectLengthText.getText().toString());

            userName = (String) userNameText.getText().toString();

            editor.putInt("speechVolume", speechVolume);
            editor.putInt("nameLength", nameLength);
            editor.putInt("highWaterMark", highWaterMark);
            editor.putInt("lowWaterMark", lowWaterMark);
            editor.putInt("afterMsgDelay", afterMsgDelay);
            editor.putInt("detectLength", detectLength);
            editor.putString("streamLocator", userName);
            editor.putBoolean("sayJoinedMessages", saying_joined_messages);
            editor.putBoolean("sayLeftMessages", saying_left_messages);
            editor.putBoolean("sayEmojis", saying_emojis);
            editor.putBoolean("sayTranslations", saying_translations);
            editor.putBoolean("sayDisplayNames", saying_display_names);
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
        speechVolume = settings.getInt("speechVolume", 100);
        nameLength = settings.getInt("nameLength", 12);
        highWaterMark = settings.getInt("highWaterMark", 10);
        lowWaterMark = settings.getInt("lowWaterMark", 5);
        afterMsgDelay = settings.getInt("afterMsgDelay", 0);
        detectLength = settings.getInt("detectLength", 20);
        userName = settings.getString("streamLocator", "Broadcaster Name");
        saying_joined_messages = settings.getBoolean("sayJoinedMessages", false);
        saying_left_messages = settings.getBoolean("sayLeftMessages", false);
        saying_emojis = settings.getBoolean("sayEmojis", true);
        currentVoice = settings.getString("currentVoice", "unknown");
        saying_translations = settings.getBoolean("sayTranslations", true);
        saying_display_names = settings.getBoolean("sayDisplayNames", true);

        speechVolumeText.setText(Integer.toString(speechVolume));
        nameLengthText.setText(Integer.toString(nameLength));
        highWaterMarkText.setText(Integer.toString(highWaterMark));
        lowWaterMarkText.setText(Integer.toString(lowWaterMark));
        afterMsgDelayText.setText(Integer.toString(afterMsgDelay));
        detectLengthText.setText(Integer.toString(detectLength));

        userNameText.setText(userName);

        joinMessagesSwitch.setChecked(saying_joined_messages);
        leftMessagesSwitch.setChecked(saying_left_messages);
        emojiSwitch.setChecked(saying_emojis);
        translationsSwitch.setChecked(saying_translations);
        displayNameSwitch.setChecked(saying_display_names);
        textDisplaySwitch.setChecked(true);
        displaying_messages = true;
        ttsManager.setVolume(speechVolume);
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
        else if (id == R.id.settings_menu_item) {
            mainView.setVisibility(View.GONE);
            settingsView.setVisibility(View.VISIBLE);
            settingsViewIsUp = true;
        }
        return super.onOptionsItemSelected(item);
    }

    // save the current chat to the Android clipboard
    public void saveChatToClipboard(View v) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(null, chatLog);
        clipboard.setPrimaryClip(clip);
        chatLog = "";
        Toast.makeText(getApplicationContext(), "Chat messages saved to clipboard", Toast.LENGTH_SHORT).show();
    }

    // start or stop chat message processing in response to button press
    public void chatAction(View v) {
        if (appState != State.AWAITING_USER_REQUEST) {
            chatActionButton.setText("Say Messages of");
            userNameText.setVisibility(View.VISIBLE);
            stopChatProcessing(true);
        }
        else {
            chatActionButton.setText("Stop Messages");
            sharedUrl = null;
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
            queueMessageToSay("Chat messages stopped");
        }
        messages.clear();
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
        userName = (String) userNameText.getText().toString().trim();
        queueMessageToSay("Looking for a Periscope live stream by " + userName);
        appState = State.AWAITING_BROADCAST_ID;
        userQueryTask = new WebQueryTask();
        userQueryTask.init(this);
        userQueryTask.execute(PERISCOPE_URL + userName);
    }

    // send a query about the user named in the userNameText text object to the periscope web server
    private void sharedUrlQuery() {
        queueMessageToSay("ScopeSpeaker is acquiring the shared Periscope stream");
        appState = State.AWAITING_BROADCAST_ID;
        userQueryTask = new WebQueryTask();
        userQueryTask.init(this);
        userQueryTask.execute(sharedUrl);
    }

    // schedule a setup query (user or shared URL) query to be run a given number of seconds from now
    private void schedulePeriscopeSetupQuery(int seconds) {
        if (sharedUrl == null) {
            queueMessageToSay("Will check for live streams in " + seconds + " seconds");
        }
        if (handler != null) {
            handler.removeCallbacks(the_runnable);
            handler = null;
        }
        handler = new Handler();
        the_runnable = new Runnable() {
            @Override
            public void run() {
                //Query Periscope for user or shared URL again after 'secondsToWait' seconds (converted to ms)
                handler = null;
                if (sharedUrl != null) {
                    sharedUrlQuery();
                }
                else {
                    userQuery();
                }
            }
        };
        handler.postDelayed(the_runnable, seconds * 1000);
    }

    // process the successful result of a webQueryTask request (this drives app state forward through expected states)
    public void webQueryResult(String response) {
        web_response = response;
        if (appState == State.AWAITING_BROADCAST_ID) {
            if (userQueryTask != null) {
                userQueryTask.cancel(true);
                userQueryTask = null;
            }
            if (sharedUrl != null) {
                broadcastID = extractBroadcastIdFromSharedUrlResponse(response);
            }
            else {
                broadcastID = extractBroadcastIdFromUserResponse(response);
            }
            if (broadcastID != null) {
                Log.i(TAG, "Doing old periscope response parsing");
                appState = State.AWAITING_CHAT_ACCESS_TOKEN;
                infoQueryTask = new WebQueryTask();
                infoQueryTask.init(this);
                infoQueryTask.execute(PERISCOPE_BROACAST_INFO_URL + broadcastID);
            }
            else {
                if (sharedUrl != null) {
                    queueMessageToSay("ScopeSpeaker did not find the shared broadcast");
                }
                else
                {
                    // this code parses the new 'react' based periscope page.. if a colon suddenly appears
                    //  in the "Looking for.." msg on the screen, that indicates the new parse is being done

                    Log.i(TAG, "Doing new periscope response parsing");
                    setMessageView("Looking for a Periscope live stream by: " + userName);
                    String user_session = extractUserSessionFromUserResponse(response);
                    if (user_session != null) {
                        List<String> the_fields = new ArrayList<String>(Arrays.asList(user_session.split(":")));
                        String user_id = the_fields.get(0);
                        String session_id = the_fields.get(1);
                        String s1 = PERISCOPE_USER_BROADCAST_LIST_URL.replace("replace_with_user_id", user_id);
                        String user_broadcast_list_url = s1.replace("replace_with_session_id", session_id);


                        appState = State.AWAITING_BROADCAST_LIST;
                        infoQueryTask = new WebQueryTask();
                        infoQueryTask.init(this);
                        infoQueryTask.execute(user_broadcast_list_url);
                    }
                    else {
                        queueMessageToSay(userName + " has no broadcasts");
                        schedulePeriscopeSetupQuery(secondsToWait);
                    }
                }
            }
        }
        else if (appState == State.AWAITING_BROADCAST_LIST) {
            if (infoQueryTask != null) {
                infoQueryTask.cancel(true);
                infoQueryTask = null;
            }
            try {
                JSONObject bListJsonResponse = new JSONObject(response);
                JSONArray  bListJsonArray = bListJsonResponse.getJSONArray(JSON_TAG_BROADCASTS);
                if (bListJsonArray.length() > 0) {
                    JSONObject bcastJsonObject = bListJsonArray.getJSONObject(0);
                    broadcastID = bcastJsonObject.getString(JSON_TAG_ID);
                    if (broadcastID != null) {
                        appState = State.AWAITING_CHAT_ACCESS_TOKEN;
                        infoQueryTask = new WebQueryTask();
                        infoQueryTask.init(this);
                        infoQueryTask.execute(PERISCOPE_BROACAST_INFO_URL + broadcastID);
                    }
                }
                if (broadcastID == null) {
                    queueMessageToSay(userName + " has no broadcasts");
                    schedulePeriscopeSetupQuery(secondsToWait);
                }
            }
            catch (JSONException e) {
                chatAccessError();
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
                broadcastSource = bcastJsonObject.getString(JSON_TAG_BROADCAST_SOURCE);
                broadcastUsername = bcastJsonObject.getString(JSON_TAG_USERNAME);
                if (video_state.equals("RUNNING")) {
                    chatURLAccessToken = infoJsonResponse.getString(JSON_TAG_URL_CHAT_TOKEN);
                    appState = State.AWAITING_CHAT_ENDPOINT;
                    chatQueryTask = new WebQueryTask();
                    chatQueryTask.init(this);
                    chatQueryTask.execute(PERISCOPE_CHAT_ACCESS_URL + chatURLAccessToken);
                }
                else {
                    chatAccessError();
                }
            }
            catch (JSONException e) {
                chatAccessError();
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
                if (broadcastSource.indexOf("android") >= 0) {
                    queueMessageToSay("Listening for the chat messages of " + broadcastUsername);
                }
                else {
                    queueMessageToSay("Listening to chat messages of " + broadcastUsername);
                }
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
                List<String> msg_fields = new ArrayList<String>(Arrays.asList(message_to_say.split(":")));
                String language_tag = msg_fields.get(0);
                String who_said_it = msg_fields.get(1);
                msg_fields.remove(0);
                msg_fields.remove(0);
                String what_was_said = TextUtils.join(" ", msg_fields);
                if (what_was_said.equals("left") && !saying_left_messages) {
                    return;
                }
                if (what_was_said.equals("joined") && !saying_joined_messages) {
                    return;
                }
                String to_be_said = language_tag + ":" + who_said_it + ":" + what_was_said;
                queueMessageToSay(to_be_said);
            }
        }
    }

    private void chatAccessError() {
        if (sharedUrl == null) {
            queueMessageToSay(userName + " is not live streaming at the moment");
            schedulePeriscopeSetupQuery(secondsToWait);
        }
        else {
            queueMessageToSay("The requested stream is not live and may be a replay");
            userNameText.setVisibility(View.VISIBLE);
        }
    }

    // process the error result of a webQueryTask request .. schedule a new user query
    public void webQueryError(String error) {
        queueMessageToSay("Got a bad response from periscope: " + error);
        /* jjf
        ScopeSpeakerActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                schedulePeriscopeSetupQuery(secondsToWait);
            }
        });
        */
    };

    // append a chat message to the running chat log
    private void appendToChatLog(String chatMessage) {
        android.text.format.DateFormat df = new android.text.format.DateFormat();
        String time_stamp = (String) df.format("yyyy-MM-dd hh:mm:ss a", new java.util.Date());
        chatLog += time_stamp + "  " + chatMessage + "\n";
    }

    // extract a chat message from a JSON packet sent by the Periscope chat server
    private String extractChatMessage(String chatString) {
        String what_they_said = "";
        String who_said_it = "";
        String language_tag = null;
        try {
            JSONObject chatMessage = new JSONObject(chatString);
            int kind = chatMessage.getInt("kind");
            String payloadString = chatMessage.getString("payload");
            JSONObject payload = new JSONObject(payloadString);
            if (kind == 1) {
                try {
                    String bodyString = payload.getString("body");
                    JSONObject outerBody = new JSONObject(bodyString);
                    what_they_said = outerBody.getString("body");
                    String senderString = payload.getString("sender");
                    JSONObject sender = new JSONObject(senderString);
                    String languageString = sender.getString("lang");
                    JSONArray languageArray = new JSONArray(languageString);
                    String chat_message_language = languageArray.getString(0);
                    String display_name = sender.getString("display_name");
                    String user_name = sender.getString("username");
                    if (saying_display_names) {
                        who_said_it = display_name;
                    }
                    else {
                        who_said_it = user_name;
                    }
                    language_tag = "";
                    if (languageArray.length() > 1) {
                        language_tag = "?M";
                    }
                    if ( (language_tag.length() == 0) && (what_they_said.length() > detectLength) ) {
                        language_tag = "?L";
                    }
                    language_tag += chat_message_language;
                    if (what_they_said.equals("joined")) {
                        //Log.i(TAG, "got a textual join message:" + chatString);
                        return null;
                    }
                    if (knownBots.indexOf(user_name) >= 0) {
                        return null;
                    }
                    if (botWords.indexOf(what_they_said) >= 0) {
                        Log.i(TAG, "gonna add to bot list");
                        knownBots.add(user_name);
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
                    String message_for_chatlog = sender.getString("username") + " joined";
                    if (saying_joined_messages) {
                        queueMessageToSay(message_for_chatlog);
                    }
                    else {
                        appendToChatLog(message_for_chatlog);
                    }
                    return null;
                }
                else if (payloadKind == 2) {
                    String message_for_chatlog = sender.getString("username") + " left";
                    if (saying_left_messages) {
                        queueMessageToSay(message_for_chatlog);
                    }
                    else {
                        appendToChatLog(message_for_chatlog);
                    }
                    return null;
                }
            }
        }
        catch (JSONException e) {
            // missing payload is a JSON syntactic error and should be logged
            Log.i(TAG, "chat parse exception when parsing message payload:" + e.getMessage());
            Toast.makeText(getApplicationContext(), "Chat message payload parse error", Toast.LENGTH_SHORT).show();
            queuePriorityMessageToSay("Chat message payload parse error");
            appendToChatLog("Payload parse error: " + chatString);
            return null;
        }
        if ( (who_said_it.length() == 0) || (what_they_said.length() == 0) ) {
            return null;
        }
        return language_tag + ":" + who_said_it + ":" + what_they_said;
    }

    private String removeEmoji(String incoming) {
        // we will store all the non emoji characters in this array list
        ArrayList<Character> nonEmoji = new ArrayList<>();

        // this is where we will store the reasembled name
        String outgoing = "";

        for (int i = 0; i < incoming.length(); i++) {
            // currently emojis don't have a devoted unicode script so they return UNKNOWN
            if (incoming.charAt(i) < 255) {
                nonEmoji.add(incoming.charAt(i));//its not an emoji so we add it
            }
        }
        // we then cycle through rebuilding the string
        for (int i = 0; i < nonEmoji.size(); i++) {
            outgoing += nonEmoji.get(i);
        }
        return outgoing;
    }

    // extract a broadcast ID for the first broadcast of a user's list of broadcasts returned by the user query
    private String extractBroadcastIdFromUserResponse(String periscopeResponse) {
        int startOfVideoTag = periscopeResponse.indexOf(VIDEO_TAG);
        if (startOfVideoTag > 0) {
            int startOfId = startOfVideoTag + VIDEO_TAG.length();
            int endOfId = periscopeResponse.indexOf('&', startOfVideoTag);
            String idString = periscopeResponse.substring(startOfId, endOfId);
            return(idString);
        }
        else {
            return(null);
        }
    }

    // extract a User ID and a session ID from the response to the user query,
    //  return in a colon separated, concatenated string
    private String extractUserSessionFromUserResponse(String periscopeResponse) {
        String actualUserTag = USER_TAG.replace("replace_this", userName);
        int startOfUserTag = periscopeResponse.indexOf(actualUserTag);
        if (startOfUserTag < 0) {
            return(null);
        }
        int startOfId = startOfUserTag + actualUserTag.length();
        int endOfId = periscopeResponse.indexOf('&', startOfId);
        String idString = periscopeResponse.substring(startOfId, endOfId);
        Log.i(TAG, "Parsed user id of:" + idString);

        int startOfSessionTag = periscopeResponse.indexOf(SESSION_TAG);
        if (startOfSessionTag < 0) {
            return (null);
        }
        int startOfSessionId = startOfSessionTag + SESSION_TAG.length();
        int endoFSessionId = periscopeResponse.indexOf('&', startOfSessionId);
        String sessionIdString = periscopeResponse.substring(startOfSessionId, endoFSessionId);
        Log.i(TAG, "Parsed session id of:" + sessionIdString);

        return(idString + ":" + sessionIdString);
    }

    // extract a broadcast ID from the shared URL response
    //
    private String extractBroadcastIdFromSharedUrlResponse(String periscopeResponse) {
        int startOfPscpTag = periscopeResponse.indexOf(PSCP_TAG);
        if (startOfPscpTag > 0) {
            int startOfId = startOfPscpTag + PSCP_TAG.length();
            int endOfId = periscopeResponse.indexOf('"', startOfPscpTag);
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
    public void sayNext() {
        String speak_string = null;

        // exit if we are currently speaking
        if (speaking) {
            return;
        }

        // exit if the ttsManager hasn't initialized itself yet
        if (ttsManager == null) {
            return;
        }

        // exit if there are no messages in the queue
        if (messages.isEmpty()) {
            return;
        }

        // if there was not a setting for current voice (first run) use default from TTS manager
        // Also.. since the ttsManager might not be initialized when settings are restored at start
        // verify that the voice is set to that specified by settings just before we talk

        String active_voice = ttsManager.getCurrentVoice();
        if ( currentVoice.equals("unknown") ) {
            currentVoice = active_voice;
        }
        if (defaultLanguage == null) {
            defaultLanguage = ttsManager.getDefaultLanguage();
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
        Boolean message_processed = false;
        queuedMessageBeingSaid = speak_string;
        int colon_location = speak_string.indexOf(":");
        int question_mark_location = speak_string.indexOf("?");
        if ( (question_mark_location == 0) || ((colon_location > 0) && (colon_location < 6)) ) {
            // message may need to be translated, if device default language doesn't match language tag from chat server message
            String msg_fields[] = speak_string.split(":");
            String language_tag = msg_fields[0];
            String who_said_it = msg_fields[1];
            String what_was_said = msg_fields[2];

            if (saying_translations && (!language_tag.equals(defaultLanguage))) {
                String translation_command = language_tag + "-" + defaultLanguage;
                appendToChatLog(who_said_it + " said before translation(" + translation_command + "): " + what_was_said);
                Log.i(TAG, who_said_it + " said before translation(" + translation_command + "): " + what_was_said);
                TranslatorBackgroundTask translatorBackgroundTask = new TranslatorBackgroundTask(this);
                translatorBackgroundTask.init(this);
                translatorBackgroundTask.execute(who_said_it, what_was_said, translation_command);
            }
            else {
                sayIt(who_said_it, saidWord, what_was_said, "");
                Log.i(TAG, who_said_it + " said(" + language_tag + "): " + what_was_said);
                appendToChatLog(who_said_it + " said(" + language_tag + "): " + what_was_said);
            }
            message_processed = true;
        }

        // message not processed above means it isn't from someone, but is informative from app
        if (!message_processed) {
            Log.i(TAG, speak_string);
            appendToChatLog(speak_string);
            sayIt("", "", speak_string, "");
        }
    }

    // say a string
    public void sayIt(String who, String announce_word, String message_to_say, String additional_screen_info) {
        String speak_string;
        String sayer;
        String announce_phrase = announce_word + ": ";

        if (announce_word.length() == 0) {
            announce_phrase = "";
        }

        if ( (message_to_say != null) && (!saying_emojis) ) {
            speak_string = removeEmoji(message_to_say);
        }
        else {
            speak_string = message_to_say;
        }
        if ( (who.length() != 0) && (!saying_emojis) ) {
            sayer = removeEmoji(who);
        }
        else {
            sayer = who;
        }
        setMessageView(sayer + " " + announce_phrase + speak_string + additional_screen_info);
        if (ttsManager != null) {
            if ( (nameLength == 0) || (sayer.length() == 0) ){
                ttsManager.initQueue(speak_string);
            }
            else {
                String shortend_who = who.substring(0, Math.min(who.length(), nameLength));
                ttsManager.initQueue(shortend_who + " " + announce_word + ": " + speak_string);
            }
        }
    }

    // say a string after translation
    public void sayTranslated(String who_said_it, String what_was_said, String translation_info) {
        translatorBackgroundTask = null;
        Log.i(TAG, "After translation: " + what_was_said);
        appendToChatLog("After translation: " + what_was_said);
        if (what_was_said.equals("joined") || what_was_said.equals("Joined")
                || what_was_said.equals("Participation") || what_was_said.equals("has joined"))  {
            speaking = false;
            queuedMessageBeingSaid = null;
            sayNext();
            return;
        }
        String announce_word = translatedWord;

        if (translation_info.indexOf("?L") >= 0) {
            String source_language = translation_info.split("-")[0].split("L")[1];
            if (source_language.equals(defaultLanguage)) {
                announce_word = saidWord;
            }
        }
        sayIt(who_said_it, announce_word, what_was_said, translation_info);
    }

    // invoked by text to speech object when something is done being said
    // a status string is passed in if something went wrong in outputting the speech, a null passed otherwise
    // if a delay is desired after each message the timer is kicked off here to schedule the next message,
    //   otherwise the next message is kicked off.
    //  Note: speech object doesn't run on UI thread, but the chat logic does, so we have to use 'runOnUiThread' invocation
    public void speechComplete (String speech_status) {
        if (speech_status != null) {
            setMessageView(speech_status);
        }
        if ( (afterMsgDelay != 0) && (sharedUrl == null) ) {
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
        schedulePeriscopeSetupQuery(2);
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

