package io.krisztiaan.rn.android.credentials;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;

public class RNAndroidCredentialsModule extends ReactContextBaseJavaModule
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ActivityEventListener {
    private static final String TAG = "RNAndroidCredentialsMod";

    private static final int RC_READ = 68;
    private static final int RC_SAVE = 469;

    private static final Gson gson = new Gson();

    private final ReactApplicationContext reactContext;
    private final GoogleApiClient credentialsApiClient;
    private CredentialRequest credentialRequest;

    private Promise credentialRequestPromise;
    private Promise saveCredentialsPromise;
    private Promise initPromise;

    public RNAndroidCredentialsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;

        credentialsApiClient = new GoogleApiClient.Builder(reactContext)
                .addConnectionCallbacks(this)
                .addApi(Auth.CREDENTIALS_API)
                .build();
        credentialsApiClient.connect();

        reactContext.addActivityEventListener(this);
    }

    private static WritableMap toReact(Object o) {
        try {
            if (o instanceof List) {
                throw new JSONException("Expected non-list object! Use toReactArray instead!");
            }
            return jsonToReact(new JSONObject(gson.toJson(o)));
        } catch (JSONException jse) {
            return Arguments.createMap();
        }
    }

    private static WritableArray toReactArray(List list) {
        try {
            return jsonToReact(new JSONArray(gson.toJson(list)));
        } catch (JSONException jse) {
            return Arguments.createArray();
        }
    }

    private static WritableMap jsonToReact(JSONObject jsonObject) throws JSONException {
        WritableMap writableMap = Arguments.createMap();
        Iterator iterator = jsonObject.keys();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            Object value = jsonObject.get(key);
            if (value instanceof Float || value instanceof Double) {
                writableMap.putDouble(key, jsonObject.getDouble(key));
            } else if (value instanceof Number) {
                writableMap.putInt(key, jsonObject.getInt(key));
            } else if (value instanceof String) {
                writableMap.putString(key, jsonObject.getString(key));
            } else if (value instanceof JSONObject) {
                writableMap.putMap(key, jsonToReact(jsonObject.getJSONObject(key)));
            } else if (value instanceof JSONArray) {
                writableMap.putArray(key, jsonToReact(jsonObject.getJSONArray(key)));
            } else if (value == JSONObject.NULL) {
                writableMap.putNull(key);
            }
        }
        return writableMap;
    }

    private static WritableArray jsonToReact(@NonNull JSONArray jsonArray) throws JSONException {
        WritableArray writableArray = Arguments.createArray();
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof Float || value instanceof Double) {
                writableArray.pushDouble(jsonArray.getDouble(i));
            } else if (value instanceof Number) {
                writableArray.pushInt(jsonArray.getInt(i));
            } else if (value instanceof String) {
                writableArray.pushString(jsonArray.getString(i));
            } else if (value instanceof JSONObject) {
                writableArray.pushMap(jsonToReact(jsonArray.getJSONObject(i)));
            } else if (value instanceof JSONArray) {
                writableArray.pushArray(jsonToReact(jsonArray.getJSONArray(i)));
            } else if (value == JSONObject.NULL) {
                writableArray.pushNull();
            }
        }
        return writableArray;
    }

    private static String[] parseStringReadableArray(ReadableArray readableArray) {
        String[] array = new String[readableArray.size()];
        for (int i = 0; i < readableArray.size(); i++) {
            array[i] = readableArray.getString(i);
        }
        return array;
    }

    private static CredentialPickerConfig parseCredentialPickerConfig(ReadableMap config) {
        return new CredentialPickerConfig.Builder().setPrompt(config.getInt("prompt"))
                .setShowAddAccountButton(config.getBoolean("showAddAccountButton"))
                .setShowCancelButton(config.getBoolean("showCancelButton"))
                .build();
    }

    private static WritableMap parseCredential(Credential credential) {
        WritableMap result = Arguments.createMap();
        result.putString("accountType", credential.getAccountType());
        result.putString("familyName", credential.getFamilyName());
        result.putString("generatedPassword", credential.getGeneratedPassword());
        result.putString("givenName", credential.getGivenName());
        result.putString("id", credential.getId());
        result.putString("name", credential.getName());
        result.putString("password", credential.getPassword());
        if (credential.getProfilePictureUri() != null)
            result.putString("profilePicture", credential.getProfilePictureUri().toString());

        return result;
    }

    private boolean checkIsClientOk(@Nullable Promise promise) {
        if (credentialsApiClient == null) {
            if (promise != null) promise.reject("500", "GoogleApiClient is null!");
            return false;
        }
        if (!credentialsApiClient.isConnected()) {
            if (promise != null) promise.reject("500", "GoogleApiClient not connected!");
            return false;
        }
        return true;
    }

    @ReactMethod
    public void init(
            boolean passwordLoginSupported,
            @Nullable ReadableMap credentialPickerConfig,
            @Nullable ReadableMap credentialHintPickerConfig,
            ReadableArray identityProviders) {
        credentialRequest =
                new CredentialRequest.Builder()
                        .setPasswordLoginSupported(passwordLoginSupported)
                        .setAccountTypes(parseStringReadableArray(identityProviders))
                        .setCredentialPickerConfig(parseCredentialPickerConfig(credentialPickerConfig))
                        .setCredentialPickerConfig(parseCredentialPickerConfig(credentialHintPickerConfig))
                        .build();
    }

    @Override
    public String getName() {
        return "RNAndroidCredentials";
    }

    @ReactMethod
    public void requestCredentials(final boolean promptIfMore, Promise promise) {
        if (!checkIsClientOk(promise) || promise == null) {
            return;
        }

        credentialRequestPromise = promise;

        Auth.CredentialsApi.request(credentialsApiClient, credentialRequest)
                .setResultCallback(new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(@NonNull CredentialRequestResult credentialRequestResult) {
                        if (credentialRequestResult.getStatus().isSuccess()) {
                            credentialRequestPromise.resolve(parseCredential(credentialRequestResult.getCredential()));
                        } else {
                            resolveResult(promptIfMore, credentialRequestResult.getStatus());
                        }
                    }
                });
    }

    @ReactMethod
    public void saveCredentials(String email, String password, final Promise promise) {
        if (!checkIsClientOk(promise) || promise == null) {
            return;
        }

        saveCredentialsPromise = promise;
        Credential credential = new Credential.Builder(email)
                .setPassword(password)
                .build();

        Auth.CredentialsApi.save(credentialsApiClient, credential).setResultCallback(
                new ResultCallback<Result>() {
                    @Override
                    public void onResult(@NonNull Result result) {
                        Status status = result.getStatus();
                        if (status.isSuccess()) {
                            saveCredentialsPromise.resolve(null);
                        } else {
                            if (status.hasResolution()) {
                                try {
                                    status.startResolutionForResult(getCurrentActivity(), RC_SAVE);
                                } catch (IntentSender.SendIntentException e) {
                                    saveCredentialsPromise.reject(e);
                                    saveCredentialsPromise = null;
                                }
                            } else {
                                promise.reject("406", "Unable to resolve");
                            }
                        }
                    }
                });
    }

    private void resolveResult(boolean promptIfMore, Status status) {
        if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED && promptIfMore) {
            try {
                status.startResolutionForResult(getCurrentActivity(), RC_READ);
            } catch (IntentSender.SendIntentException e) {
                credentialRequestPromise.reject(e);
                credentialRequestPromise = null;
            }
        } else {
            credentialRequestPromise.reject(
                    String.valueOf(status.getStatusCode()),
                    status.getStatusMessage());
        }
    }

    @ReactMethod
    public void disableAutoSignIn(Promise promise) {
        if (!checkIsClientOk(promise) || promise == null) {
            return;
        }
        Auth.CredentialsApi.disableAutoSignIn(credentialsApiClient);
        promise.resolve(null);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient Connected");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient Connection Suspended, Reconnecting...");
        credentialsApiClient.connect();
    }

    @Override
    public void onConnectionFailed(
            @NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient Connection Failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_READ && credentialRequestPromise != null) {
            if (resultCode == Activity.RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                credentialRequestPromise.resolve(parseCredential(credential));
            } else {
                credentialRequestPromise.reject("405", "Credential Read: NOT OK");
            }
        }
        if (requestCode == RC_SAVE && saveCredentialsPromise != null) {
            if (resultCode == Activity.RESULT_OK) {
                Object o = null;
                if (data != null) {
                    parseCredential((Credential) data.getParcelableExtra(Credential.EXTRA_KEY));
                }
                saveCredentialsPromise.resolve(o);
            } else {
                saveCredentialsPromise.reject("408", "SAVE: Canceled by user");
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}
