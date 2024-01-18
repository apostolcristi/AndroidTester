package com.example.androidtester

import android.Manifest
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var component: ComponentName
    private lateinit var textViewSignalStrength: TextView
    private lateinit var fileExecutor: ScheduledExecutorService
    private lateinit var handler: Handler
    private lateinit var logFile: File
    private lateinit var toggleTurnOffScreen: ToggleButton
    private lateinit var toggleMobileData: ToggleButton
    private lateinit var toggleAirplaneMode: ToggleButton
    private lateinit  var btnSendSMS:Button
    private lateinit  var btnMakeCall: Button

    private val requestEnableAdminContract =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                devicePolicyManager.lockNow()
            }
        }

    private lateinit var cpuUsageTextView: TextView
    private lateinit var cpuExecutor: ScheduledExecutorService
    private lateinit var cpuHandler: Handler
    private var smsCounter: Int = 0
    private var callCounter: Int = 0
    private val SMS_COUNTER_KEY = "smsCounter"
    private val CALL_COUNTER_KEY = "callCounter"

    companion object {
        private const val SMS_PERMISSION_REQUEST_CODE = 1
        private const val CALL_PERMISSION_REQUEST_CODE = 2
        private const val TOGGLE_STATES_FILE = "toggle_states.txt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadSmsCounter()
        loadCallCounter()


        initializeDeviceControl()
        initializeUI()
        initializeCpuUsage()

        // Display initial system resource information
        displayBatteryLevel()
        displayMemoryInfo()
        displayNetworkStatus()
        displayStorageInfo()
        displayDeviceInfo()

        // Check for SMS permission
        if (!isSmsPermissionGranted()) {
            requestSmsPermission()
        }
        if (!isCallPermissionGranted()) {
            requestCallPermission()
        }

        handler = Handler(Looper.getMainLooper())

        // Initialize the file executor to save data every 30 seconds
        fileExecutor = Executors.newSingleThreadScheduledExecutor()
        fileExecutor.scheduleAtFixedRate(
            { saveActionLogToFile() },
            0,
            30,
            TimeUnit.SECONDS
        )
    }

    private fun initializeDeviceControl() {
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        component = ComponentName(this, DeviceAdminReceiver::class.java)
    }
    private fun getLTELevel(signalStrength: SignalStrength): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            signalStrength.cellSignalStrengths[0].level
        } else {
            signalStrength.level
        }
    }
    private fun initializeUI() {

        setContentView(R.layout.activity_main)

        textViewSignalStrength = findViewById(R.id.textViewSignalStrength)

        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (telephonyManager.phoneType != TelephonyManager.PHONE_TYPE_NONE) {
            val phoneStateListener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    super.onSignalStrengthsChanged(signalStrength)

                    // Get the signal strength level for LTE
                    val level = getLTELevel(signalStrength)

                    // Update the UI with the signal strength level
                    runOnUiThread {
                        textViewSignalStrength.text = "Signal Strength Level: $level"
                    }
                }
            }

            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } else {
            // Handle the case where signal strength information is not available
        }










         toggleTurnOffScreen = findViewById(R.id.toggleTurnOffScreen)
        toggleMobileData= findViewById(R.id.toggleMobileData)
         toggleAirplaneMode = findViewById(R.id.toggleAirplaneMode)
         btnSendSMS= findViewById(R.id.btnSendSMS)
         btnMakeCall = findViewById(R.id.btnMakeCall)



        btnMakeCall.setOnClickListener {

            showCustomCallDialog()
        }
        // Set click listener for btnSendSMS
        btnSendSMS.setOnClickListener {
            showCustomSMSEditorDialog()
        }
        toggleTurnOffScreen.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                turnOffScreen()
                recordButtonClickTimestamp("Turn Off Screen", isChecked)
                Toast.makeText(this, "Screen turned off!", Toast.LENGTH_SHORT).show()
            }
        }

        toggleMobileData.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableMobileData()
                recordButtonClickTimestamp("Enable Mobile Data", isChecked)
                Toast.makeText(this, "Mobile data enabled!", Toast.LENGTH_SHORT).show()
            } else {
                disableMobileData()
                recordButtonClickTimestamp("Disable Mobile Data", isChecked)
                Toast.makeText(this, "Mobile data disabled!", Toast.LENGTH_SHORT).show()
            }
        }

        toggleAirplaneMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableAirplaneMode()
                recordButtonClickTimestamp("Enable Airplane Mode", isChecked)
                Toast.makeText(this, "Airplane mode enabled!", Toast.LENGTH_SHORT).show()
            } else {
                disableAirplaneMode()
                recordButtonClickTimestamp("Disable Airplane Mode", isChecked)
                Toast.makeText(this, "Airplane mode disabled!", Toast.LENGTH_SHORT).show()
            }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            logFile = File(downloadsDir, "activity_log.txt")
            loadToggleStates()
        }
    }


    private fun loadToggleStates() {
        try {
            val toggleStates = logFile.readLines()
            for (line in toggleStates) {
                val parts = line.split(" - ")
                if (parts.size == 2) {
                    val timestamp = parts[0].trim()
                    val action = parts[1].substringBefore(":").trim()
                    val isEnabled = parts[1].substringAfter(":").trim().toBoolean()

                    when (action) {
                        "Turn Off Screen" -> toggleTurnOffScreen.isChecked = isEnabled
                        "Enable Mobile Data" -> toggleMobileData.isChecked = isEnabled
                        "Enable Airplane Mode" -> toggleAirplaneMode.isChecked = isEnabled
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun recordButtonClickTimestamp(action: String, isEnabled: Boolean) {
        val currentTimeMillis = System.currentTimeMillis()
        val formattedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(currentTimeMillis))

        val logEntry = "$formattedTimestamp - $action: ${if (isEnabled) "Enabled" else "Disabled"}\n"

        try {
            // Append the log entry to the file
            FileWriter(logFile, true).use {
                it.append(logEntry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveActionLogToFile() {
        try {
            // Read the current log file content
            val currentLogFileContent = logFile.readText()

            // Create a backup file with a timestamp
            val backupFile = File(
                getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "action_log_backup.txt"
            )

            FileWriter(backupFile, true).use {  // Use 'true' to append to the file
                it.write(currentLogFileContent)
            }

            // Show a toast indicating that the log file has been saved
            handler.post {
                Toast.makeText(
                    this@MainActivity,
                    "Action log appended to backup file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCustomCallDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.custom_call_dialog, null)
        val editTextPhoneNumber: EditText = dialogView.findViewById(R.id.editTextPhoneNumber)
        val btnMakeCallDialog: Button = dialogView.findViewById(R.id.btnCall)

        val alertDialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()

        btnMakeCallDialog.setOnClickListener {
            val phoneNumber = editTextPhoneNumber.text.toString().trim()
            makeCall(phoneNumber)
            alertDialog.dismiss()
        }
    }

    private fun makeCall(phoneNumber: String) {
        callCounter++

        val callIntent = Intent(Intent.ACTION_CALL)
        callIntent.data = Uri.parse("tel:$phoneNumber")

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, start the call
            startActivity(callIntent)
            saveCallCounter()
        } else {
            // Request CALL_PHONE permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST_CODE
            )
        }
    }


    private fun showCustomSMSEditorDialog() {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.custom_sms_dialog, null)

            val editTextPhoneNumber: EditText = dialogView.findViewById(R.id.editTextPhoneNumber)
            val editTextMessage: EditText = dialogView.findViewById(R.id.editTextMessage)
            val btnSend: Button = dialogView.findViewById(R.id.btnSend)

            val dialogBuilder = AlertDialog.Builder(this)
                .setView(dialogView)
                .setTitle("Custom SMS")

            val alertDialog = dialogBuilder.create()

            btnSend.setOnClickListener {
                val phoneNumber = editTextPhoneNumber.text.toString()
                val message = editTextMessage.text.toString()

                if (phoneNumber.isNotEmpty() && message.isNotEmpty()) {
                    sendCustomSMS(phoneNumber, message)
                    alertDialog.dismiss()
                } else {
                    Toast.makeText(this, "Please enter a valid phone number and message", Toast.LENGTH_SHORT).show()
                }
            }

            alertDialog.show()
        }


    private fun initializeCpuUsage() {
        cpuUsageTextView = findViewById(R.id.cpuUsageTextView)
        cpuExecutor = Executors.newSingleThreadScheduledExecutor()

        cpuExecutor.scheduleAtFixedRate(
            { runOnUiThread { getSystemCpuUsage() } },
            0,
            1,
            TimeUnit.SECONDS
        )
    }
    private fun isCallPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Function to request call permission
    private fun requestCallPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CALL_PHONE),
            CALL_PERMISSION_REQUEST_CODE
        )
    }
    private fun isSmsPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_REQUEST_CODE
        )
    }


    // Save the smsCounter value
    private fun saveSmsCounter() {
        val sharedPrefs = getPreferences(Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt(SMS_COUNTER_KEY, smsCounter).apply()
    }

    // Retrieve the smsCounter value
    private fun loadSmsCounter() {
        val sharedPrefs = getPreferences(Context.MODE_PRIVATE)
        smsCounter = sharedPrefs.getInt(SMS_COUNTER_KEY, 0)
    }

    // Save the callCounter value
    private fun saveCallCounter() {
        val sharedPrefs = getPreferences(Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt(CALL_COUNTER_KEY, callCounter).apply()
    }

    // Retrieve the callCounter value
    private fun loadCallCounter() {
        val sharedPrefs = getPreferences(Context.MODE_PRIVATE)
        callCounter = sharedPrefs.getInt(CALL_COUNTER_KEY, 0)
    }
    private fun sendCustomSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsCounter++
            // Create a PendingIntent with FLAG_IMMUTABLE
            val sentPendingIntent = PendingIntent.getBroadcast(
                this,
                smsCounter,
                Intent("SMS_SENT"),
                PendingIntent.FLAG_IMMUTABLE
            )
            Toast.makeText(this, "send SMS", Toast.LENGTH_SHORT).show()
            // Send the SMS
            saveSmsCounter()
            smsManager.sendTextMessage(phoneNumber, null, message, sentPendingIntent, null)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to send SMS", Toast.LENGTH_SHORT).show()
        }
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SMS_PERMISSION_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this, "SMS sent", Toast.LENGTH_SHORT).show()
                }
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                    Toast.makeText(this, "SMS not sent: Generic failure", Toast.LENGTH_SHORT).show()
                }
                SmsManager.RESULT_ERROR_NO_SERVICE -> {
                    Toast.makeText(this, "SMS not sent: No service", Toast.LENGTH_SHORT).show()
                }
                SmsManager.RESULT_ERROR_NULL_PDU -> {
                    Toast.makeText(this, "SMS not sent: Null PDU", Toast.LENGTH_SHORT).show()
                }
                SmsManager.RESULT_ERROR_RADIO_OFF -> {
                    Toast.makeText(this, "SMS not sent: Radio off", Toast.LENGTH_SHORT).show()
                }
            }
        }
        if (requestCode == CALL_PERMISSION_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    Toast.makeText(this, "Call initiated", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Unable to initiate the call", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun turnOffScreen() {
        if (devicePolicyManager.isAdminActive(component)) {
            devicePolicyManager.lockNow()
        } else {
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
        cpuExecutor.shutdown()
           }
}
