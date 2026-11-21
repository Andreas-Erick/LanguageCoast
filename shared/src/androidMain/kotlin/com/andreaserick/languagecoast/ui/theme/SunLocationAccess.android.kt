package com.andreaserick.languagecoast.ui.theme

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.andreaserick.languagecoast.data.SettingsRepository
import com.andreaserick.languagecoast.data.SunLocation
import com.andreaserick.languagecoast.data.logError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

actual val sunLocationAvailable: Boolean = true

/** A permission request the system answers faster than this was answered without showing its dialog. */
private const val NO_DIALOG_MILLIS = 600L

/** A location the phone already has is used if it is younger than this; otherwise a new one is asked for. */
private const val FRESH_MILLIS = 6 * 60 * 60 * 1000L

/** How long the phone is given to find where it is. */
private const val FIX_TIMEOUT_MILLIS = 20_000L

/**
 * Only the approximate location is asked for ([Manifest.permission.ACCESS_COARSE_LOCATION]): the town is all
 * sunrise and sunset need, and from Android 12 the system's question then offers "approximate" only.
 */
@Composable
actual fun rememberSunLocationAccess(): SunLocationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var allowed by remember { mutableStateOf(locationAllowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = locationAllowed(context) }

    var askedAt by rememberSaveable { mutableStateOf(0L) }
    var explainedBefore by rememberSaveable { mutableStateOf(false) }
    fun wouldExplain() =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = locationAllowed(context)
        // As for notifications: a refusal that came at once, with no rationale due, means the system no longer
        // shows its question, and only the app's page in the system's settings can allow it now.
        val answeredAtOnce = SystemClock.elapsedRealtime() - askedAt < NO_DIALOG_MILLIS
        if (!granted && !explainedBefore && !wouldExplain() && answeredAtOnce) openAppDetails(context, activity)
    }

    return SunLocationAccess(
        allowed = allowed,
        request = {
            if (locationAllowed(context)) {
                allowed = true
            } else {
                explainedBefore = wouldExplain()
                askedAt = SystemClock.elapsedRealtime()
                launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    )
}

@Composable
actual fun SunLocationUpdates(active: Boolean) {
    if (!active) return
    val context = LocalContext.current
    val settings = koinInject<SettingsRepository>()
    // Counts the times the app came to the front, which is also when the user returns from the system's
    // question about location; each one reads the location again.
    var fronts by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { fronts++ }
    LaunchedEffect(fronts) {
        if (fronts == 0 || !locationAllowed(context)) return@LaunchedEffect
        try {
            val location = readApproximateLocation(context) ?: return@LaunchedEffect
            SunLocation.rounded(location.latitude, location.longitude)?.let { settings.setSunLocation(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("SunLocation", "The location could not be read", e)
        }
    }
}

private fun locationAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Where the phone roughly is: a location it already has if that is fresh, and otherwise a new one from the
 * network or the fused provider (Android 11 and later). Null while location is switched off on the phone.
 */
@SuppressLint("MissingPermission") // Only called after locationAllowed.
private suspend fun readApproximateLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(manager)) return null
    val providers = manager.getProviders(true)
    val known = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (known != null && System.currentTimeMillis() - known.time < FRESH_MILLIS) return known
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return known
    val provider = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
        LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
        LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
        else -> return known
    }
    val fresh = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
        suspendCancellableCoroutine<Location?> { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
    }
    return fresh ?: known
}

/** Opens the app's page in the system's settings, where its permissions are. */
private fun openAppDetails(context: Context, activity: Activity?) {
    val from = activity ?: context
    try {
        from.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .apply { if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    } catch (e: ActivityNotFoundException) {
        // No settings app answers; there is nothing more the app can do.
    }
}
