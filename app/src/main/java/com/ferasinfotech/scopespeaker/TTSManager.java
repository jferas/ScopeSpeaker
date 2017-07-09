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
import java.util.HashMap;
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
    private String defaultLanguage = null;

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
                Locale theLocale = null;
                int result = mTts.setLanguage(Locale.US);
                isLoaded = true;

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    theLocale = mTts.getDefaultVoice().getLocale();
                } else{
                    theLocale = mTts.getDefaultLanguage();
                }
                defaultLanguage = theLocale.getDisplayLanguage().substring(0,2).toLowerCase();
                Log.e("tts", "The default language is:" + defaultLanguage);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mVoices = mTts.getVoices();
                }

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
        if (isLoaded) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mTts.speak(text, TextToSpeech.QUEUE_ADD, params, "utteranceId");
            }
            else {
                HashMap<String, String> map = new HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId");
                mTts.speak(text, TextToSpeech.QUEUE_ADD, map);
            }
        }
        else {
            Log.e("tts", "TTS Not Initialized");
            Log.e("tts", "Text to be said was:" + text);
            Toast.makeText(the_context, "TTS Not Initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public void initQueue(String text) {
        if (isLoaded) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId");
            }
            else {
                HashMap<String, String> map = new HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId");
                mTts.speak(text, TextToSpeech.QUEUE_FLUSH, map);
            }
        }
        else {
            Log.e("tts", "TTS Not Initialized");
            Log.e("tts", "Text to be said was:" + text);
            Toast.makeText(the_context, "TTS Not Initialized", Toast.LENGTH_SHORT).show();
        }
    }

    public List<String> getAvailableVoicesForLanguage() {
        List<String>matchedNames = new ArrayList<String>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            matchedVoices = new HashSet<Voice>();
            matchedNames.add("Use All Voices");
            for (Voice theVoice : mVoices) {
                if (theVoice.getName().indexOf(defaultLanguage + "-") == 0) {
                    Boolean isInstalled = !theVoice.getFeatures().contains("notInstalled");
                    if (isInstalled) {
                        matchedVoices.add(theVoice);
                        matchedNames.add(theVoice.getName());
                    }
                }

            }
        }
        else {
            matchedNames.add("Not supported in this Android version");
        }
        return matchedNames;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getCurrentVoice() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            return mTts.getVoice().getName();
        }
        else {
            return "Default";
        }
    }

    public void setVoice(String new_voice) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Boolean done = false;
            Iterator<Voice> iterator = mVoices.iterator();
            while ((iterator.hasNext()) && !done) {
                Voice thisVoice = iterator.next();
                if (thisVoice.getName().equals(new_voice)) {
                    mTts.setVoice(thisVoice);
                    done = true;
                }
            }
        }
    }
}
