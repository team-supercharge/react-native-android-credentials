
import { NativeModules, Platform } from 'react-native';

let RNAndroidCredentials = {};

export const IdentityProviders = {
    FACEBOOK: "https://www.facebook.com",
    GOOGLE: "https://accounts.google.com",
    LINKEDIN: "https://www.linkedin.com",
    MICROSOFT: "https://login.live.com",
    PAYPAL: "https://www.paypal.com",
    TWITTER: "https://twitter.com",
    YAHOO: "https://login.yahoo.com",
}

export const CredentialPickerPrompt = {
    CONTINUE: 1,
    SIGN_IN: 2,
    SIGN_UP: 3,
}

export const createPickerConfig = (
    showAddAccountButton = false,
    showCancelButton = true,
    prompt = CredentialPickerPrompt.CONTINUE,
) => ({
    showAddAccountButton,
    showCancelButton,
    prompt,
})

if (Platform.OS === 'android') {
    RNAndroidCredentials = NativeModules.RNAndroidCredentials;
}

const NotAndroidCredentials = {
    init: () => {},
    showSignInHint: () => Promise.reject(new Error(`not supported on ${Platform.OS}`)),
    disableAutoSignIn: () => Promise.reject(new Error(`not supported on ${Platform.OS}`)),
    requestCredentials: () => Promise.reject(new Error(`not supported on ${Platform.OS}`)),
    saveCredentials: () => Promise.reject(new Error(`not supported on ${Platform.OS}`)),
}

const AndroidCredentials = {
    init: (
        passwordLoginSupported = true,
        credentialPickerConfig = createPickerConfig(),
        credentialHintPickerConfig = createPickerConfig(),
        ...identityProviders
    ) => RNAndroidCredentials.init(
        passwordLoginSupported,
        credentialPickerConfig,
        credentialHintPickerConfig,
        Array.isArray(identityProviders) ? identityProviders : [ identityProviders ]),
    showSignInHint: (showCancelButton) => Promise.reject(new Error(`not supported on ${Platform.OS} yet`)),
    disableAutoSignIn: () => RNAndroidCredentials.disableAutoSignIn(),
    requestCredentials: (promptIfMore = false) => RNAndroidCredentials.requestCredentials(promptIfMore),
    saveCredentials: (email, password) => RNAndroidCredentials.saveCredentials(email, password),
}

export default Platform.OS === 'android' ? AndroidCredentials : NotAndroidCredentials;
