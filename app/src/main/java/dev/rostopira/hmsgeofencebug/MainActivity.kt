package dev.rostopira.hmsgeofencebug

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.huawei.hms.location.Geofence
import com.huawei.hms.location.GeofenceRequest
import com.huawei.hms.location.LocationServices

class MainActivity: Activity() {
    private var isReproduced = false
    private var counter = 0
    private var lastKnownLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 123)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onClick(v: View?) {
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener {
            val wasNull = lastKnownLocation == null
            lastKnownLocation = it
            if (wasNull)
                onClick(null)
        }
        val currentLocation = lastKnownLocation ?: return
        val geofence = Geofence.Builder()
            .setRoundArea(
                currentLocation.latitude,
                currentLocation.longitude,
                300f
            )
            .setConversions(Geofence.ENTER_GEOFENCE_CONVERSION)
            /** According to documentation:
             *  Unique ID. If the unique ID already exists, the new geofence will overwrite the old one.
             *  Bug 1: this is not, what happens
             *  **/
            .setUniqueId(id)
            .setValidContinueTime(Geofence.GEOFENCE_NEVER_EXPIRE)
            .build()
        val request = GeofenceRequest.Builder()
            .createGeofence(geofence)
            .setInitConversions(GeofenceRequest.ENTER_INIT_CONVERSION)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            12345,
            Intent("com.example.Geofencer"),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val locationServices = LocationServices.getGeofenceService(this)
        locationServices.createGeofenceList(request, pendingIntent)
            .addOnSuccessListener {
                /** Create again recursively to reproduce bug **/
                counter++
                Log.w("REGISTERED", counter.toString())
                onClick(null)
            }
            .addOnFailureListener {
                /** Bug reproduced, error: too many geofences **/
                Log.wtf("THIS SHOULDN'T HAPPEN", it)
                if (!isReproduced) {
                    isReproduced = true
                    runOnUiThread {
                        findViewById<TextView>(R.id.reproduced).visibility = View.VISIBLE
                    }
                }
                /** Bug 2: calling deleteGeofenceList doesn't do anything at all
                 *  Error will be thrown again and again, only reinstall of the app helps **/
                locationServices.deleteGeofenceList(mutableListOf(id)).addOnCompleteListener {
                    /** Try to create geofence after deletion **/
                    onClick(null)
                }
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 124)
    }

    companion object {
        const val id = "GEOFENCE_ID"
    }

}