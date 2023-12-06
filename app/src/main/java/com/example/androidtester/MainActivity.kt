package com.example.androidtester

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.*
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.CentralProcessor.TickType
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var component: ComponentName

    private val requestEnableAdminContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // User granted device administrator rights
                devicePolicyManager.lockNow()
            }
        }

    private lateinit var cpuUsageTextView: TextView
    private lateinit var cpuHandler: Handler
    private lateinit var cpuExecutor: ScheduledExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize device control components
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        component = ComponentName(this, DeviceAdminReceiver::class.java)

        // Initialize UI components
        val btnTurnOffScreen: Button = findViewById(R.id.btnTurnOffScreen)
        val btnEnableMobileData: Button = findViewById(R.id.btnEnableMobileData)
        val btnDisableMobileData: Button = findViewById(R.id.btnDisableMobileData)
        val btnEnableAirplaneMode: Button = findViewById(R.id.btnEnableAirplaneMode)
        val btnDisableAirplaneMode: Button = findViewById(R.id.btnDisableAirplaneMode)

        // Set click listeners for device control buttons
        btnTurnOffScreen.setOnClickListener { turnOffScreen() }
        btnEnableMobileData.setOnClickListener { enableMobileData() }
        btnDisableMobileData.setOnClickListener { disableMobileData() }
        btnEnableAirplaneMode.setOnClickListener { enableAirplaneMode() }
        btnDisableAirplaneMode.setOnClickListener { disableAirplaneMode() }

        // Initialize CPU usage components
        cpuUsageTextView = findViewById(R.id.cpuUsageTextView)
        cpuHandler = Handler(Looper.getMainLooper())
        cpuExecutor = Executors.newSingleThreadScheduledExecutor()

        // Schedule a task to update CPU usage every second
        cpuExecutor.scheduleAtFixedRate(
            { runOnUiThread { getSystemCpuUsage() } },
            0,
            1,
            TimeUnit.SECONDS
        )

        // Display system resource information
        displayBatteryLevel()
        displayMemoryInfo()
        displayNetworkStatus()
        displayStorageInfo()
        displayDeviceInfo()
    }

    private fun turnOffScreen() {
        if (devicePolicyManager.isAdminActive(component)) {
            devicePolicyManager.lockNow()
        } else {
            // Prompt the user to enable device administrator rights
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable device administrator rights to turn off the screen."
            )
            requestEnableAdminContract.launch(intent)
        }
    }

    private fun enableMobileData() {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val methodName = "setMobileDataEnabled"
            val c = Class.forName(connectivityManager.javaClass.name)
            val method = c.getDeclaredMethod(methodName, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(connectivityManager, true)
            Toast.makeText(this, "Mobile Data Enabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to enable Mobile Data", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun disableMobileData() {
        try {
            val connectivityManager =
                getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val methodName = "setMobileDataEnabled"
            val c = Class.forName(connectivityManager.javaClass.name)
            val method = c.getDeclaredMethod(methodName, Boolean::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(connectivityManager, false)
            Toast.makeText(this, "Mobile Data Disabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to disable Mobile Data", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun enableAirplaneMode() {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", true)
            sendBroadcast(intent)
            Toast.makeText(this, "Airplane Mode Enabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to enable Airplane Mode", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun disableAirplaneMode() {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
            val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            intent.putExtra("state", false)
            sendBroadcast(intent)
            Toast.makeText(this, "Airplane Mode Disabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to disable Airplane Mode", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    private fun getSystemCpuUsage() {
        try {
            val process = Runtime.getRuntime().exec("top -n 1")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("%Cpu") == true) {
                    // Parse the CPU usage information from the line
                    val cpuUsage = line!!.substringAfterLast(" ").toFloat()

                    // Update UI
                    cpuHandler.post {
                        cpuUsageTextView.text = "System CPU Usage: ${String.format("%.2f", cpuUsage)}%"
                    }
                    break
                }
            }

            process.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }





    private fun displayBatteryLevel() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level / scale.toFloat() * 100

        val batteryTextView: TextView = findViewById(R.id.batteryTextView)
        batteryTextView.text = "Battery Level: $batteryPct%"
    }

    private fun displayMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()

        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalStorage = blockSize * availableBlocks

        val memoryTextView: TextView = findViewById(R.id.memoryTextView)
        memoryTextView.text =
            "Total Memory: ${totalMemory / (1024 * 1024)} MB\nFree Memory: ${freeMemory / (1024 * 1024)} MB\nTotal Storage: ${totalStorage / (1024 * 1024)} MB"
    }

    private fun displayNetworkStatus() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo

        val networkStatus = if (activeNetwork != null && activeNetwork.isConnected) {
            "Connected"
        } else {
            "Not Connected"
        }

        val networkTextView: TextView = findViewById(R.id.networkTextView)
        networkTextView.text = "Network Status: $networkStatus"
    }

    private fun displayStorageInfo() {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        val totalStorage = blockSize * availableBlocks

        val storageTextView: TextView = findViewById(R.id.storageTextView)
        storageTextView.text = "Total Storage: ${totalStorage / (1024 * 1024)} MB"
    }

    private fun displayDeviceInfo() {
        val deviceInfoTextView: TextView = findViewById(R.id.deviceInfoTextView)
        deviceInfoTextView.text =
            "Device Info:\nManufacturer: ${Build.MANUFACTURER}\nModel: ${Build.MODEL}\nAndroid Version: ${Build.VERSION.RELEASE}"
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shutdown the executor when the activity is destroyed
        cpuExecutor.shutdown()
    }
}
