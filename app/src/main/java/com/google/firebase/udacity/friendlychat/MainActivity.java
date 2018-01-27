/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    // JB RemoteConfig FRIENDLY_MSG_LENGTH_KEY
    private static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    // JB: An arbitrary request code value for FirebaseUI Intent
    private static final int RC_SIGN_IN = 123;

    // JB: An arbitrary request code value for PhotoPicker Intent
    private static final int RC_PHOTO_PICKER = 654;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;


    //JB: FirebaseDatabase
    private FirebaseDatabase mFirebaseDatabase;
    //JB: a DatabaseReference for Messages
    private DatabaseReference mMessagesDatabaseReference;
    //JB: Messages ChildEventListener
    private ChildEventListener mMessagesEventListener;


    //JB: FirebaseAuth
    private FirebaseAuth mFirebaseAuth;
    //JB: FirebaseAuth AuthStateListener
    private FirebaseAuth.AuthStateListener mFirebaseAuthStateListener;


    //JB: Firebase Storage
    private FirebaseStorage mFirebaseStorage;
    //JB: Firebase StorageReference for Chat Photos
    private StorageReference mChatPhotosStorageReference;


    //JB: Firebase RemoteConfig
    private FirebaseRemoteConfig mFirebaseRemoteConfig;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        //JB: Initialize Firebase Components
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        //JB: Acquire a DatabaseReference for Messages
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("friendlychat").child("messages");
        //JB: Acquire a StorageReference for Chat Photos
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("friendlychat").child("chat_photos");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // DONE: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"),
                        RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // DONE: Send messages on click
                FriendlyMessage friendlyMessage
                        = new FriendlyMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });

        // JB: Configure Firebase RemoteConfig
        // JB: Enable developer mode to allow for frequent refreshes of the cache
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG).build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        // JB: Set in-app default values from an XML file:
        // mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
        // JB: Set in-app default values from a Map object:
        Map<String, Object> defaultConfigMap = new HashMap<String, Object>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchRemoteConfig();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            //JB: Handle signing out
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // JB: Register FirebaseAuth Listener, which will result in registering MessagesReadListener
        registerAuthStateListener(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // JB: Unegister FirebaseAuth Listener, as well as MessagesReadListener
        registerAuthStateListener(false);
        registerMessagesReadListener(false);

        // JB: Clear the view so that no duplicates show up on resume
        mMessageAdapter.clear();
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        // JB: remove ChildEventListener (already removed on pause)
//        registerMessagesReadListener(false);
//    }

    // JB: Registers AuthStateListener
    private void registerMessagesReadListener(boolean register) {
        if (register) {

            if (mMessagesEventListener == null) {
                // JB: Sync messages into view
                mMessagesEventListener = new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                        mMessageAdapter.add(friendlyMessage);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                    }

                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                };
            }
            mMessagesDatabaseReference.addChildEventListener(mMessagesEventListener);
        } else {
            if (mMessagesEventListener != null) {
                mMessagesDatabaseReference.removeEventListener(mMessagesEventListener);
                mMessagesEventListener = null;
            }
        }
    }

    // JB: Registers AuthStateListener
    private void registerAuthStateListener(boolean register) {
        if (register) {

            if (mFirebaseAuthStateListener == null) {
                // JB: Initialize mFirebaseAuthStateListener
                mFirebaseAuthStateListener = new FirebaseAuth.AuthStateListener() {
                    @Override
                    public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();

                        if (user != null) {
                            // user is signed in
                            onSignedInInitialize(user);
                            // Toast.makeText(MainActivity.this, "You are now signed in!", Toast.LENGTH_SHORT).show();
                        } else {
                            // user is singed out
                            onSignedOutCleanup();
                            startActivityForResult(AuthUI.getInstance()
                                            .createSignInIntentBuilder()
                                            .setIsSmartLockEnabled(false)
                                            .setAvailableProviders(
                                                    Arrays.asList(
                                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                            .build()
                                    , RC_SIGN_IN);
                        }
                    }
                };
            }

            mFirebaseAuth.addAuthStateListener(mFirebaseAuthStateListener);
        } else {
            if (mFirebaseAuthStateListener != null) {
                mFirebaseAuth.removeAuthStateListener(mFirebaseAuthStateListener);
            }
        }
    }

    // JB: Override onActivityResult()
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_SIGN_IN:
                // JB: find out if back button pressed from Login flow to finish the Activity
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Signed in cancelled!", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case RC_PHOTO_PICKER:
                // JB: Send picked image to Firebase Storage and update Messages with file path
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = data.getData();
                    if (selectedImage != null) {

                        // JB: get a reference to store file at friendlychat/chat_photos/<FILENAME>
                        StorageReference storageReference = mChatPhotosStorageReference
                                .child(selectedImage.getLastPathSegment());

                        storageReference.putFile(selectedImage)
                                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        //JB: Send the uploaded image as a message
                                        Uri downloadUrl = taskSnapshot.getDownloadUrl();

                                        FriendlyMessage friendlyMessage
                                                = new FriendlyMessage(null, mUsername,
                                                downloadUrl.toString());

                                        mMessagesDatabaseReference.push().setValue(friendlyMessage);
                                    }
                                });
                    }
                }
                break;
            default:
                return;
        }

    }

    // JB: onSinedInInitialize(
    private void onSignedInInitialize(FirebaseUser user) {
        mUsername = user.getDisplayName();
        registerMessagesReadListener(true);
    }

    // JB: onSinedInInitialize(
    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        registerMessagesReadListener(false);
    }

    // JB: Fetch Firebase RemoteConfig
    private void fetchRemoteConfig() {
        long cacheExpiration = 3600;
        // Allow frequent refreshes of the cache if Dev mode
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        mFirebaseRemoteConfig.activateFetched();
                        applyRetrievedMessageLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "RemoteConfig fetch error", e);
                        applyRetrievedMessageLengthLimit();
                    }
                });
    }

    // JB: Apply message length limit retrieved from Firebase RemoteConfig
    private void applyRetrievedMessageLengthLimit() {
        Long messageLengthLimit = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(messageLengthLimit.intValue())});
        Log.d(TAG, "RemoteConfig applied: "
                + FRIENDLY_MSG_LENGTH_KEY + " = " + messageLengthLimit);
    }
}
