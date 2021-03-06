/*
  Offscreen Android Views library for Qt

  Author:
  Sergey A. Galin <sergey.galin@gmail.com>

  Distrbuted under The BSD License

  Copyright (c) 2016, DoubleGIS, LLC.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of the DoubleGIS, LLC nor the names of its contributors
    may be used to endorse or promote products derived from this software
    without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS
  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
  THE POSSIBILITY OF SUCH DAMAGE.
*/


package ru.dublgis.androidhelpers;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
// import android.os.Handler;
// import android.os.Looper;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;


public class VoiceRecognitionListener implements RecognitionListener {
    private long mNativePtr = 0;
    private static final String TAG = "Grym/SpeechRecognizer";
    private Activity mActivity = null;
    private SpeechRecognizer mSpeechRecognizer = null;

    // Bug workarounds
    private boolean mReadyForSpeechReceived = false;
    private boolean mStopListeningCalled = false;

    // From C++
    public void setNativePtr(long nativePtr)
    {
        synchronized(this) {
            mNativePtr = nativePtr;
        }
    }

    // From C++
    public void initialize(final Activity activity)
    {
        mActivity = activity;
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized(this) {
                    try {
                        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mActivity);
                        mSpeechRecognizer.setRecognitionListener(VoiceRecognitionListener.this);
                    } catch (final Exception e) {
                        Log.e(TAG, "Exception while creating SpeechRecognizer:", e);
                        mSpeechRecognizer = null;
                    }
                }
            }
        });
    }

    // From C++
    public void destroySpeechRecognizer() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized(this) {
                    try {
                        if (mSpeechRecognizer != null) {
                            Log.d(TAG, "destroySpeechRecognizer() runnable: destroying...");
                            mSpeechRecognizer.destroy();
                        }
                    } catch (final Exception e) {
                        Log.e(TAG, "Exception while destorying SpeechRecognizer:", e);
                    }
                }
            }
        });
    }

    // From C++
    public void requestLanguageDetails()
    {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "requestLanguageDetails() runnable");
                try {
                    Intent intent = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
                    mActivity.sendOrderedBroadcast(
                        intent
                        , null
                        , new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                // Log.d(TAG, "onReceive(" + intent.toUri(0) + ")");
                                if (getResultCode() != Activity.RESULT_OK) {
                                    return;
                                }
                                ArrayList<CharSequence> hints = getResultExtras(true)
                                   .getCharSequenceArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES);
                                ArrayList<String> result = new ArrayList<String>();
                                int sz = hints.size();
                                result.ensureCapacity(sz);
                                for (int i = 0; i < hints.size(); ++i) {
                                    result.add(hints.get(i).toString());
                                }
                                synchronized(this) {
                                    if (mNativePtr != 0) {
                                        nativeSupportedLanguagesReceived(mNativePtr, result);
                                    }
                                }
                            }
                        }
                        , null
                        , Activity.RESULT_OK
                        , null
                        , null);
                } catch (final Exception e) {
                    Log.e(TAG, "Exception in requestLanguageDetails:", e);
                    mSpeechRecognizer = null;
                }
            }
        });
    }

    // From C++
    public void startListening(final Intent intent)
    {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSpeechRecognizer != null) {
                    Log.d(TAG, "startListening() runnable");
                    mSpeechRecognizer.cancel();
                    mSpeechRecognizer.startListening(intent);
                    mReadyForSpeechReceived = false;
                    mStopListeningCalled = false;
                } else {
                    Log.e(TAG, "startListening: the recognizer is null!");
                }
            }
        });
    }

    // From C++
    public void stopListening()
    {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "stopListening() runnable");
                if (mSpeechRecognizer != null) {
                    mSpeechRecognizer.stopListening();
                    mStopListeningCalled = true;
                } else {
                    Log.e(TAG, "stopListening: the recognizer is null!");
                }
            }
        });
    }

    // From C++
    public void cancel()
    {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "cancel() runnable");
                if (mSpeechRecognizer != null) {
                    mSpeechRecognizer.cancel();
                } else {
                    Log.e(TAG, "cancel: the recognizer is null!");
                }
            }
        });
    }


    @Override
    public void onBeginningOfSpeech()
    {
        Log.v(TAG, "onBeginningOfSpeech");
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnBeginningOfSpeech(mNativePtr);
            }
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer)
    {
        // Unsupported yet
    }

    @Override
    public void onEndOfSpeech()
    {
        Log.v(TAG, "onEndOfSpeech");
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnEndOfSpeech(mNativePtr);
            }
        }
    }

    @Override
    public void onError(int error)
    {
        // Workaround for a bug in Android: https://code.google.com/p/android/issues/detail?id=179293
        if (error == SpeechRecognizer.ERROR_NO_MATCH && !mReadyForSpeechReceived) {
            Log.d(TAG, "onError: working around ERROR_NO_MATCH bug.");
            return;
        }
        // "Client error" after force-stopping the listening is OK.
        if (error == SpeechRecognizer.ERROR_CLIENT && mStopListeningCalled) {
            Log.d(TAG, "onError: ignoring ERROR_CLIENT after listening is stopped.");
            return;
        }
        Log.v(TAG, "onError " + error);
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnError(mNativePtr, error);
            }
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params)
    {
        // Unsupported yet
        // Log.v(TAG, "onEvent " + eventType);
    }

    @Override
    public void onPartialResults(Bundle partialResults)
    {
        Log.v(TAG, "onPartialResults");
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnPartialResults(mNativePtr, partialResults);
            }
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params)
    {
        Log.v(TAG, "onReadyForSpeech");
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnReadyForSpeech(mNativePtr, params);
            }
            mReadyForSpeechReceived = true;
        }
    }

    @Override
    public void onResults(Bundle results)
    {
        Log.v(TAG, "onResults");
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnResults(mNativePtr, results);
            }
        }

    }

    @Override
    public void onRmsChanged(float rmsdB)
    {
        // Log.v(TAG, "onRmsChanged"); - super noisy
        synchronized(this) {
            if (mNativePtr != 0) {
                nativeOnRmsChanged(mNativePtr, rmsdB);
            }
        }

    }

    // Check if there's a voice recognition activity
    public static boolean isVoiceRecognitionActivityAvailable(final Activity activity)
    {
        try {
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            final PackageManager manager = activity.getPackageManager();
            final List<ResolveInfo> infos = manager.queryIntentActivities(intent, 0);
            if (infos != null) {
                final int count = infos.size();
                Log.d(TAG, "isVoiceRecognitionActivityAvailable: found " + count + " apps to handle the request.");
                if (count > 0) {
                    return true;
                }
            }
        } catch (final Throwable e) {
            Log.e(TAG, "isVoiceRecognitionActivityAvailable exception: ", e);
        }
        return false;

    }

    // Called from C++ to start external Activity to recognize speech
    public static boolean startVoiceRecognitionActivity(final Activity activity, final int request_code, final String prompt, final String language_model)
    {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, language_model); // E.g.: RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            if (prompt != null && !prompt.isEmpty()) {
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
            }
            activity.startActivityForResult(intent, request_code);
            return true;
        } catch (final ActivityNotFoundException e) {
            Log.w(TAG, "startVoiceRecognitionActivity: voice recognition activity is not available: ", e);
        } catch (final Throwable e) {
            Log.e(TAG, "startVoiceRecognitionActivity exception: ", e);
        }
        return false;
    }

    // Call this from void Activity.onActivityResult(int requestCode, int resultCode, Intent data)
    // to get recognized voice input. The result_code should be the same as used for call to
    // startVoiceRecognitionActivity(). If the result is not a voice input or nothing is recognized
    // the function returns null.
    // Example:
    //
    // @Override
    // public void onActivityResult(int requestCode, int resultCode, Intent data) {
    //     ...
    //     if (requestCode == 1245 // the same code you used for startVoiceRecognitionActivity()
    //         && resultCode == RESULT_OK)
    //     {
    //         final String text = getVoiceRecognitionActivityResult(data);
    //         if (text != null) {
    //             ... // Pass the text to the application
    //         }
    //     }
    //     ...
    // }
    //
    public static String getVoiceRecognitionActivityResult(final Intent data)
    {
        try {
            final ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                final String result = matches.get(0);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
        } catch (final Throwable e) {
            Log.e(TAG, "getVoiceRecognitionActivityResult exception: ", e);
        }
        return null;
    }

    public native void nativeOnBeginningOfSpeech(long ptr);
    // public native void onBufferReceived(long ptr byte[] buffer);
    public native void nativeOnEndOfSpeech(long ptr);
    public native void nativeOnError(long ptr, int error);
    // public native void nativeOnEvent(long ptr, int eventType, Bundle params);
    public native void nativeOnPartialResults(long ptr, Bundle partialResults);
    public native void nativeOnReadyForSpeech(long ptr, Bundle params);
    public native void nativeOnResults(long ptr, Bundle results);
    public native void nativeOnRmsChanged(long ptr, float rmsdB);
    public native void nativeSupportedLanguagesReceived(long ptr, ArrayList<String> languages);

}



