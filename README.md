# react-native-android-credentials

## Getting started

`$ npm install react-native-android-credentials --save`

### Mostly automatic installation

`$ react-native link react-native-android-credentials`

### Manual installation

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
- Add `import io.krisztiaan.rn.android.credentials.RNAndroidCredentialsPackage;` to the imports at the top of the file
- Add `new RNAndroidCredentialsPackage()` to the list returned by the `getPackages()` method

2. Append the following lines to `android/settings.gradle`:
    ```gradle
    include ':react-native-android-credentials'
    project(':react-native-android-credentials').projectDir =
      new File(rootProject.projectDir, '../node_modules/react-native-android-credentials/android')
    ```

3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
    ```gradle
      compile project(':react-native-android-credentials')
    ```

## Usage

```javascript
import AndroidCredentials, { IdentityProviders, CredentialPickerPrompt }
  from 'react-native-android-credentials';

AndroidCredentials.init();
```
