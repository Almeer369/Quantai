package com.qantai.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Qantai for Android.
 *
 * The whole app (Qantai hub + Odysseus execution layer + HUD console UI)
 * lives in assets/index.html and runs on-device inside a WebView.
 *
 * A plain WebView has no Web Speech API, so this activity exposes a small
 * JavaScript bridge ("AndroidBridge") that uses the phone's own
 * SpeechRecognizer for voice orders and TextToSpeech for spoken replies.
 */
public class MainActivity extends Activity {

    private static final int REQ_AUDIO = 101;

    private WebView webView;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean pendingListen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                ttsReady = true;
            }
        });

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // order history is kept in localStorage

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ------------------------------------------------------------------
    // JavaScript bridge (called from the page on a background thread)
    // ------------------------------------------------------------------
    private class Bridge {
        @JavascriptInterface
        public void startListening() {
            runOnUiThread(MainActivity.this::requestListen);
        }

        @JavascriptInterface
        public void speak(final String text) {
            runOnUiThread(() -> {
                if (ttsReady && text != null && !text.isEmpty()) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "qantai");
                }
            });
        }
    }

    private void requestListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingListen = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        beginListening();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_AUDIO) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted && pendingListen) {
            pendingListen = false;
            beginListening();
        } else {
            pendingListen = false;
            sendError("Microphone permission was denied. Allow it in Settings, or type your order.");
        }
    }

    private void beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendError("Speech recognition isn't available on this phone. Please type your order.");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onPartialResults(Bundle partialResults) { }
                @Override public void onEvent(int eventType, Bundle params) { }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        js("window.onNativeSpeechResult(" + JSONObject.quote(list.get(0)) + ")");
                    } else {
                        sendError("Sorry, I couldn't understand that order.");
                    }
                }

                @Override
                public void onError(int error) {
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            sendError("Didn't catch that. Please try again or type your order.");
                            break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                            sendError("Speech recognition needs a connection on this phone. Type your order instead.");
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            sendError("Microphone permission is missing. Allow it in Settings.");
                            break;
                        default:
                            sendError("Voice input failed (code " + error + "). Please type your order.");
                    }
                }
            });
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
    }

    private void sendError(String message) {
        js("window.onNativeSpeechError(" + JSONObject.quote(message) + ")");
    }

    private void js(final String script) {
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
