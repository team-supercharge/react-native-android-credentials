package io.krisztiaan.rn.android.credentials;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.HintRequest;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

public class RNAndroidCredentialsModule extends ReactContextBaseJavaModule
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        ActivityEventListener {
    private static final String TAG = "RNAndroidCredentials";
    private static final int RC_HINT = 23423;
    private static final int RC_READ = 68;
    private static final int RC_SAVE = 469;

    private final ReactApplicationContext reactContext;
    private final GoogleApiClient credentialsApiClient;
    private CredentialRequest credentialRequest;

    private Promise hintPromise;
    private Promise credentialRequestPromise;
    private Promise saveCredentialsPromise;

    public RNAndroidCredentialsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.credentialsApiClient =
                new GoogleApiClient.Builder(reactContext).addConnectionCallbacks(this)
                        .enableAutoManage((FragmentActivity) getCurrentActivity(), this)
                        .addApi(Auth.CREDENTIALS_API)
                        .build();
        reactContext.addActivityEventListener(this);
    }

    private String[] parseStringReadableArray(ReadableArray readableArray) {
        String[] array = new String[readableArray.size()];
        for (int i = 0; i < readableArray.size(); i++) {
            array[i] = readableArray.getString(i);
        }
        return array;
    }

    private CredentialPickerConfig parseCredentialPickerConfig(ReadableMap config) {
        return new CredentialPickerConfig.Builder().setPrompt(config.getInt("prompt"))
                .setShowAddAccountButton(config.getBoolean("showAddAccountButton"))
                .setShowCancelButton(config.getBoolean("showCancelButton"))
                .build();
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

    @ReactMethod
    public void init(
            boolean passwordLoginSupported,
            @Nullable ReadableMap credentialPickerConfig,
            @Nullable ReadableMap credentialHintPickerConfig,
            String identityProvider) {
        credentialRequest =
                new CredentialRequest.Builder()
                        .setPasswordLoginSupported(passwordLoginSupported)
                        .setAccountTypes(identityProvider)
                        .setCredentialPickerConfig(parseCredentialPickerConfig(credentialPickerConfig))
                        .setCredentialPickerConfig(parseCredentialPickerConfig(credentialHintPickerConfig))
                        .build();
    }

    @Override
    public String getName() {
        return "RNAndroidCredentials";
    }

    // TODO: use for https://developers.google.com/identity/smartlock-passwords/android/idtoken-auth
    @ReactMethod
    public void showSignInHint(
            boolean showCancelButton, ReadableMap credentialPickerConfig, Promise promise) {
        HintRequest hintRequest =
                new HintRequest.Builder().setHintPickerConfig(parseCredentialPickerConfig(
                        credentialPickerConfig))
                        .setEmailAddressIdentifierSupported(true)
                        .setAccountTypes(IdentityProviders.GOOGLE)
                        .build();

        PendingIntent intent =
                Auth.CredentialsApi.getHintPickerIntent(credentialsApiClient, hintRequest);
        hintPromise = promise;
        try {
            getCurrentActivity().startIntentSenderForResult(intent.getIntentSender(),
                    RC_HINT,
                    null,
                    0,
                    0,
                    0);
        } catch (IntentSender.SendIntentException | NullPointerException e) {
            Log.e(TAG, "Could not start hint picker Intent", e);
        }
    }

    @ReactMethod
    public void requestCredentials(Promise promise) {
        credentialRequestPromise = promise;

        Auth.CredentialsApi.request(credentialsApiClient, credentialRequest)
                .setResultCallback(new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(@NonNull CredentialRequestResult credentialRequestResult) {
                        if (credentialRequestResult.getStatus().isSuccess()) {
                            credentialRequestPromise.resolve(credentialRequestResult.getCredential());
                        } else {
                            resolveResult(credentialRequestResult.getStatus());
                        }
                    }
                });
    }

    @ReactMethod
    public void saveCredentials(String email, String password, final Promise promise) {
        saveCredentialsPromise = promise;
        Credential credential = new Credential.Builder(email)
                .setPassword(password)
                .build();

        Auth.CredentialsApi.save(credentialsApiClient, credential).setResultCallback(
                new ResultCallback<Result>() {
                    @Override
                    public void onResult(Result result) {
                        Status status = result.getStatus();
                        if (status.isSuccess()) {
                            promise.resolve(status);
                        } else {
                            if (status.hasResolution()) {
                                try {
                                    status.startResolutionForResult(getCurrentActivity(), RC_SAVE);
                                } catch (IntentSender.SendIntentException e) {
                                    promise.reject(e);
                                }
                            } else {
                                promise.reject("406", "Unable to resolve");
                            }
                        }
                    }
                });
    }

    private void resolveResult(Status status) {
        if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED) {
            try {
                status.startResolutionForResult(getCurrentActivity(), RC_READ);
            } catch (IntentSender.SendIntentException e) {
                credentialRequestPromise.reject(e);
            }
        } else {
            credentialRequestPromise.reject(
                    String.valueOf(status.getStatusCode()),
                    status.getStatusMessage());
        }
    }

    @ReactMethod
    public void disableAutoSignin() {
        Auth.CredentialsApi.disableAutoSignIn(credentialsApiClient);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(
            @NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_HINT) {
            if (resultCode == Activity.RESULT_OK) {
                hintPromise.resolve(data.getParcelableExtra(Credential.EXTRA_KEY));
            } else {
                hintPromise.reject("404", "Unable to resolve intent");
            }
        }
        if (requestCode == RC_READ) {
            if (resultCode == Activity.RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                credentialRequestPromise.resolve(credential);
            } else {
                credentialRequestPromise.reject("405", "Credential Read: NOT OK");
            }
        }
        if (requestCode == RC_SAVE) {
            if (resultCode == Activity.RESULT_OK) {
                saveCredentialsPromise.resolve(data.getParcelableExtra(Credential.EXTRA_KEY));
            } else {
                saveCredentialsPromise.reject("408", "SAVE: Canceled by user");
            }
        }
    }
}