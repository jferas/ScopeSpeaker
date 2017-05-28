package com.ferasinfotech.scopespeaker;

/**
 * Created by jferas on 12/12/16.
 */

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

/**
 * Created by Nilanchala
 * http://www.stacktips.com
 */
public class TTSManager {

    private TextToSpeech mTts = null;
    private boolean isLoaded = false;
    private ScopeSpeakerActivity scopeSpeakerActivity = null;
    private Context the_context = null;
    private Bundle params = null;
    private Set<Voice> mVoices = null;
    private Set<Voice> matchedVoices = null;

    public void init(Context context, ScopeSpeakerActivity ssa) {
        try {
            mTts = new TextToSpeech(context, onInitListener);
            scopeSpeakerActivity = ssa;
            the_context = context;
            params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private TextToSpeech.OnInitListener onInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                int result = mTts.setLanguage(Locale.US);
                isLoaded = true;

                mVoices = mTts.getVoices();
                List<String> englishVoices = getAvailableVoicesForLanguage("en");

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("tts", "This Language is not supported");
                    Toast.makeText(the_context, "This Language is not supported", Toast.LENGTH_LONG).show();
                }
                mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onDone(String utteranceId) {
					/*
					 * When myTTS is done speaking the current "audio file",
					 * call playAudio on the next audio file.
					 */
                        if (utteranceId.equals("utteranceId")) {
                            mTts.stop();
                            scopeSpeakerActivity.speechComplete();
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                    }

                    @Override
                    public void onStart(String utteranceId) {
                    }
                });
            } else {
                Log.e("tts", "Initialization Failed!");
            }
        }
    };

    public void shutDown() {
        mTts.shutdown();
    }

    public Boolean isSpeaking() { return mTts.isSpeaking(); }

    public void addQueue(String text) {
        if (isLoaded)
            mTts.speak(text, TextToSpeech.QUEUE_ADD, params, "utteranceId");
        else {
            Log.e("tts", "TTS Not Initialized");
            Log.e("tts", "Text to be said was:" + text);
            Toast.makeText(the_context, "TTS Not Initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public void initQueue(String text) {

        if (isLoaded)
            mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId");
        else {
            Log.e("tts", "TTS Not Initialized");
            Log.e("tts", "Text to be said was:" + text);
            Toast.makeText(the_context, "TTS Not Initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public List<String> getAvailableVoicesForLanguage(String language) {
        matchedVoices = new HashSet<Voice>();
        List<String>matchedNames = new ArrayList<String>();
        for (Voice theVoice : mVoices) {
            if (theVoice.getName().indexOf(language + "-") == 0) {
                Boolean isInstalled = !theVoice.getFeatures().contains("notInstalled");
                if (isInstalled) {
                    matchedVoices.add(theVoice);
                    matchedNames.add(theVoice.getName());
                }
            }

        }
        return matchedNames;
    }
}
