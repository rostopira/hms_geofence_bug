package dev.rostopira.hmsgeofencebug

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.huawei.hms.location.Geofence
import com.huawei.hms.location.GeofenceRequest
import com.huawei.hms.location.LocationServices
import kotlin.random.Random

class MainActivity: Activity() {
    private var isReproduced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 123)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 124)
    }

    fun onClick(v: View?) {
        v?.visibility = View.GONE
        val geofence = Geofence.Builder()
            .setRoundArea(
                Random.nextDouble(-90.0, 90.0),
                Random.nextDouble(-90.0, 90.0),
                Random.nextFloat() * 100
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

    companion object {
        const val id = "GEOFENCE_ID"
    }

}