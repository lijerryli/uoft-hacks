package com.example.jerry.heartify;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Error;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerEvent;
import com.spotify.sdk.android.player.Spotify;
import com.spotify.sdk.android.player.SpotifyPlayer;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;

public class MainActivity extends Activity implements
        SpotifyPlayer.NotificationCallback, ConnectionStateCallback
{

    public ImageView albumArt;
    public ImageButton playpause;
    public ImageButton nexttrack;
    public TextView textView;

    private boolean paused = false;

    // TODO: Replace with your client ID
    private static final String CLIENT_ID = "";
    // TODO: Replace with your redirect URI
    private static final String REDIRECT_URI = "";

    // Request code that will be used to verify if the result comes from correct activity
    // Can be any integer
    private static final int REQUEST_CODE = 1337;

    private Player mPlayer;

    private Socket mySocket;
    {
    try {
        mySocket = IO.socket("");
    } catch (URISyntaxException e) {}
    }

    private final Player.OperationCallback mOperationCallback = new Player.OperationCallback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(Error error) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        albumArt = (ImageView) findViewById(R.id.imageView);
        playpause = (ImageButton) findViewById(R.id.imageButton);
        nexttrack = (ImageButton) findViewById(R.id.imageButton3);
        textView = (TextView) findViewById(R.id.textView);

        albumArt.setVisibility(View.VISIBLE);

        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID,
                AuthenticationResponse.Type.TOKEN,
                REDIRECT_URI);
        builder.setScopes(new String[]{"user-read-private", "streaming"});
        AuthenticationRequest request = builder.build();

        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);
        mySocket.on("new message", onNewMessage);
        mySocket.connect();



        playpause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(paused) {
                    mPlayer.resume(mOperationCallback);
                    paused = false;
                } else {
                    mPlayer.pause(mOperationCallback);
                    paused = true;
                }
            }
        });

        nexttrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject o = new JSONObject();
                try {
                    o.put("id", "next");
                    o.put("name", "");
                    o.put("bpm", "");
                    o.put("img_url", "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mySocket.emit("new message", o);

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(this, response.getAccessToken(), CLIENT_ID);
                Spotify.getPlayer(playerConfig, this, new SpotifyPlayer.InitializationObserver() {
                    @Override
                    public void onInitialized(SpotifyPlayer spotifyPlayer) {
                        mPlayer = spotifyPlayer;
                        mPlayer.addConnectionStateCallback(MainActivity.this);
                        mPlayer.addNotificationCallback(MainActivity.this);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("MainActivity", "Could not initialize player: " + throwable.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        // VERY IMPORTANT! This must always be called or else you will leak resources
        Spotify.destroyPlayer(this);
        mySocket.disconnect();
        mySocket.off("new message", onNewMessage);
        super.onDestroy();
    }

    @Override
    public void onPlaybackEvent(PlayerEvent playerEvent) {
        Log.d("MainActivity", "Playback event received: " + playerEvent.name());
        switch (playerEvent) {
            // Handle event type as necessary
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(Error error) {
        Log.d("MainActivity", "Playback error received: " + error.name());
        switch (error) {
            // Handle error type as necessary
            default:
                break;
        }
    }

    @Override
    public void onLoggedIn() {
        Log.d("MainActivity", "User logged in");

        //mPlayer.playUri(null, "spotify:track:2TpxZ7JUBn3uw46aR7qd6V", 0, 0);
    }

    @Override
    public void onLoggedOut() {
        Log.d("MainActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Error e) {
        Log.d("MainActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("MainActivity", "Temporary error occurred");
    }

    @Override
    public void onConnectionMessage(String message) {
        Log.d("MainActivity", "Received connection message: " + message);
    }

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            Log.d("MainActivity", "Listening to new message");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject o = (JSONObject) args[0];
                    String message = null;
                    String song = null;
                    String bpm = null;
                    String img_url = null;
                    try {
                        message = o.getString("id");
                        song = o.getString("name");
                        bpm = o.getString("bpm");
                        img_url = o.getString("img_url");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    Log.d("MainActivity", "Got new message: " + message);
                    Log.d("MainActivity", "Got new song: " + song);
                    Log.d("MainActivity", "Got new bpm: " + bpm);
                    Log.d("MainActivity", "Got new url: " + img_url);

                    if(message != "next" && message != null) {
                        playpause.setVisibility(View.VISIBLE);
                        nexttrack.setVisibility(View.VISIBLE);
                        mPlayer.playUri(null, "spotify:track:" + message, 0, 0);
                        new GetBitmapAsync().execute(img_url);
                        textView.setText("Song: " + song + "\nBPM: " + bpm);
                    }
                }
            });
        }
    };

    class GetBitmapAsync extends AsyncTask<String, Void, Bitmap> {
        protected Bitmap doInBackground(String... src){
            try {
                java.net.URL url = new java.net.URL(src[0]);
                HttpURLConnection connection = (HttpURLConnection) url
                        .openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(input);
                return myBitmap;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(Bitmap result) {
            albumArt.setVisibility(View.VISIBLE);
            albumArt.setImageBitmap(result);
            Log.d("AsyncActivity", "Setting bitmap");
        }
    }
}