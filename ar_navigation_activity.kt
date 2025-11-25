package com.uofa.arcampusnav.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.uofa.arcampusnav.databinding.ActivityArNavigationBinding
import com.uofa.arcampusnav.data.models.Building
import com.uofa.arcampusnav.data.api.GoogleMapsService
import com.uofa.arcampusnav.sensors.DeviceSensorManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ARNavigationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityArNavigationBinding
    private lateinit var arSession: Session
    private lateinit var sensorManager: DeviceSensorManager
    private lateinit var mapsService: GoogleMapsService
    
    private var destination: Building? = null
    private var currentBearing: Float = 0f
    private var distanceToDestination: Float = 0f
    
    private val CAMERA_PERMISSION_CODE = 100
    private val LOCATION_PERMISSION_CODE = 101
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get destination from intent
        destination = intent.getParcelableExtra("destination")
        
        // Initialize services
        sensorManager = DeviceSensorManager(this)
        mapsService = GoogleMapsService(getString(R.string.google_maps_api_key))
        
        // Check permissions
        if (checkPermissions()) {
            initializeAR()
            startSensorTracking()
        } else {
            requestPermissions()
        }
        
        setupUI()
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            CAMERA_PERMISSION_CODE
        )
    }
    
    private fun initializeAR() {
        try {
            // Check ARCore availability
            when (ArCoreApk.getInstance().checkAvailability(this)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    arSession = Session(this)
                    
                    // Configure AR session
                    val config = Config(arSession).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    }
                    arSession.configure(config)
                    
                    // Setup ARSceneView
                    binding.arSceneView.setupSession(arSession)
                    
                    Toast.makeText(this, "AR Initialized", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(
                        this,
                        "ARCore not supported on this device",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            when (e) {
                is UnavailableArcoreNotInstalledException -> {
                    Toast.makeText(this, "Please install ARCore", Toast.LENGTH_LONG).show()
                }
                is UnavailableApkTooOldException -> {
                    Toast.makeText(this, "Please update ARCore", Toast.LENGTH_LONG).show()
                }
                is UnavailableSdkTooOldException -> {
                    Toast.makeText(this, "Please update this app", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this, "AR initialization failed", Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }
    
    private fun startSensorTracking() {
        lifecycleScope.launch {
            sensorManager.observeSensorData().collectLatest { sensorData ->
                // Update orientation
                val orientation = sensorManager.calculateOrientation(
                    sensorData.accelerometer,
                    sensorData.magnetometer
                )
                currentBearing = orientation[0]
                
                // Calculate bearing to destination
                destination?.let { dest ->
                    sensorData.gpsLocation?.let { location ->
                        val bearingToDestination = sensorManager.calculateBearing(
                            location.latitude,
                            location.longitude,
                            dest.latitude,
                            dest.longitude
                        )
                        
                        distanceToDestination = dest.getDistance(
                            location.latitude,
                            location.longitude
                        ).toFloat()
                        
                        // Update AR overlay
                        updateAROverlay(bearingToDestination, distanceToDestination)
                    }
                }
            }
        }
    }
    
    private fun updateAROverlay(bearingToDestination: Float, distance: Float) {
        runOnUiThread {
            // Update distance text
            binding.distanceText.text = "${distance.toInt()}m"
            binding.buildingNameText.text = destination?.name ?: ""
            
            // Calculate relative bearing
            val relativeBearing = (bearingToDestination - currentBearing + 360) % 360
            
            // Update arrow rotation
            updateDirectionalArrow(relativeBearing)
            
            // Update compass
            binding.compassView.rotation = -currentBearing
        }
    }
    
    private fun updateDirectionalArrow(relativeBearing: Float) {
        // Create or update AR arrow node
        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.BLUE))
            .thenAccept { material ->
                val arrow = ShapeFactory.makeCylinder(
                    0.05f,
                    2f,
                    Vector3(0f, 0f, 0f),
                    material
                )
                
                val arrowNode = Node().apply {
                    renderable = arrow
                    localPosition = Vector3(0f, -1f, -3f)
                    localRotation = Quaternion.axisAngle(
                        Vector3(0f, 1f, 0f),
                        relativeBearing
                    )
                }
                
                binding.arSceneView.scene.addChild(arrowNode)
            }
    }
    
    private fun setupUI() {
        binding.stopNavigationButton.setOnClickListener {
            finish()
        }
        
        binding.recenterButton.setOnClickListener {
            // Reset AR session
            try {
                arSession.pause()
                arSession.resume()
                Toast.makeText(this, "AR view recentered", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to recenter", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            arSession.resume()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onPause() {
        super.onPause()
        arSession.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        arSession.close()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && 
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeAR()
                startSensorTracking()
            } else {
                Toast.makeText(
                    this,
                    "Permissions required for AR navigation",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
