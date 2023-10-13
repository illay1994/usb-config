package com.lobyco.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Parcelable
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {

    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val usbDevice = intent.getParcelableExtra<Parcelable>(
                UsbManager.EXTRA_DEVICE
            ) as UsbDevice?


            val isAttached = when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> true
                UsbManager.ACTION_USB_DEVICE_DETACHED -> false
                else -> return
            }
            if (isAttached && usbDevice != null) {
                usbManager.checkUsbPermissions(usbDevice)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        usbManager.deviceList.values.firstOrNull()?.let {
            usbManager.checkUsbPermissions(it)
        }
        registerReceiver(
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        )
    }

    fun UsbManager.checkUsbPermissions(usbDevice: UsbDevice) {
        GlobalScope.launch {
            val result = checkUsbPermissions(this@MainActivity, usbDevice)
            withContext(Dispatchers.Main) {
                showText("${if (result) "Permission is granted ✅\n" else "Permission is not granted ❌\n"} " +
                        "ManufacturerName - ${usbDevice.manufacturerName}" +
                        "\nDeviceName - ${usbDevice.deviceName}" +
                        "\nDeviceId - ${usbDevice.deviceId} " +
                        "\nVersion - ${usbDevice.version}" +
                        "\nProductId - ${usbDevice.productId}" +
                        "\nProductName - ${usbDevice.productName}" +
                        "\nVendorId - ${usbDevice.vendorId}" +
                        "\ndeviceClass - ${usbDevice.deviceClass}"+
                        "\ndeviceSubclass - ${usbDevice.deviceSubclass}"
                )
            }
        }
    }

    private suspend fun UsbManager.checkUsbPermissions(
        context: Context,
        usbDevice: UsbDevice
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            if (hasPermission(usbDevice)) {
                continuation.resume(true)
            } else {
                // If we don't have permissions, we register a receiver to get notified when we get them
                val usbReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        context.unregisterReceiver(this)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                            continuation.resume(true)
                        else {
                            continuation.resume(false)
                        }
                    }
                }
                context.registerReceiver(
                    usbReceiver,
                    IntentFilter(ACTION_USB_PERMISSION)
                )

                // And request the permissions
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                )
                requestPermission(usbDevice, permissionIntent)

                // Make sure we also clean stuff up if the coroutine gets cancelled
                continuation.invokeOnCancellation { context.unregisterReceiver(usbReceiver) }
            }
        }


    fun showText(text: String) {
        findViewById<TextView>(R.id.text).text = text
    }
}

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"