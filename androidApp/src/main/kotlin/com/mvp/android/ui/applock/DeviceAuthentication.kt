package com.mvp.android.ui.applock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

enum class DeviceAuthResult { Success, Failed, Unavailable }

/**
 * Authentification par le système : empreinte / visage de classe forte, avec repli sur le code,
 * le schéma ou le mot de passe de l'appareil. API système uniquement (pas d'androidx.biometric) :
 * - Android 11+ : BiometricPrompt, biométrie forte ou code de l'appareil ;
 * - Android 8 à 10 : écran de confirmation du verrouillage de l'appareil (code, et empreinte selon le constructeur).
 */
fun isDeviceAuthenticationAvailable(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

/** Renvoie la fonction qui lance l'authentification ; le résultat arrive dans [onResult], sur le main thread. */
@Composable
fun rememberDeviceAuthentication(title: String, onResult: (DeviceAuthResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        currentOnResult(if (result.resultCode == Activity.RESULT_OK) DeviceAuthResult.Success else DeviceAuthResult.Failed)
    }
    return remember(context, title) {
        {
            when {
                !isDeviceAuthenticationAvailable(context) -> currentOnResult(DeviceAuthResult.Unavailable)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> showBiometricPrompt(context, title) { currentOnResult(it) }
                else -> {
                    @Suppress("DEPRECATION") // seule API disponible avant Android 11
                    val intent = context.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(title, null)
                    if (intent == null) currentOnResult(DeviceAuthResult.Unavailable) else credentialLauncher.launch(intent)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun showBiometricPrompt(context: Context, title: String, onResult: (DeviceAuthResult) -> Unit) {
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
        .build()
    prompt.authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(DeviceAuthResult.Success)

            // Annulation, trop d'essais, prompt fermé : l'écran reste verrouillé. Un essai raté isolé
            // (onAuthenticationFailed) n'est pas final, le prompt reste affiché.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(DeviceAuthResult.Failed)
        },
    )
}

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
