package com.relais.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var adapter: BluetoothAdapter? = null
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false
    private val executor = Executors.newSingleThreadExecutor()

    // Mode Wi-Fi
    private var wifiMode = false
    private lateinit var ipField: EditText
    private lateinit var pinField: EditText
    private lateinit var btnMode: Button
    private val netExecutor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null
    private var buttonsMask = 0

    // Descripteur HID : clavier (rapport 1) + souris (rapport 2)
    private val descriptor = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
        0xC0.toByte(),
        0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
        0x09, 0x01, 0xA1.toByte(), 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
        0x95.toByte(), 0x03, 0x75, 0x01, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x01,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
        0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03, 0x81.toByte(), 0x06,
        0xC0.toByte(), 0xC0.toByte()
    )

    // Codes de touches USB (position physique, comme un clavier QWERTY).
    // Sur un PC AZERTY, W/A/S/D donnent Z/Q/S/D : c'est voulu.
    private val KEY_W = 0x1A
    private val KEY_A = 0x04
    private val KEY_S = 0x16
    private val KEY_D = 0x07
    private val KEY_SPACE = 0x2C

    // Codes "scancode" pour Windows (mode Wi-Fi), meme positions de touches
    private val SC_W = 0x11
    private val SC_A = 0x1E
    private val SC_S = 0x1F
    private val SC_D = 0x20
    private val SC_SPACE = 0x39

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@MainActivity.registered = registered
            runOnUiThread {
                setStatus(if (registered) "Relais actif. Fais 2 puis 3." else "Relais inactif")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        host = device
                        setStatus("Connecté à ${device.name ?: "PC"}")
                    }
                    BluetoothProfile.STATE_CONNECTING -> setStatus("Connexion en cours...")
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (host == device) host = null
                        setStatus("Déconnecté")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        ipField = findViewById(R.id.ipField)
        pinField = findViewById(R.id.pinField)
        btnMode = findViewById(R.id.btnMode)

        val prefs = getSharedPreferences("relais", MODE_PRIVATE)
        ipField.setText(prefs.getString("ip", ""))
        prefs.getString("pin", null)?.let { pinField.setText(it) }

        btnMode.setOnClickListener {
            wifiMode = !wifiMode
            btnMode.text = if (wifiMode) "Mode : Wi-Fi" else "Mode : Bluetooth"
            if (wifiMode) {
                prefs.edit()
                    .putString("ip", ipField.text.toString().trim())
                    .putString("pin", pinField.text.toString().trim())
                    .apply()
                setStatus("Wi-Fi : lance le programme sur le PC, puis ecris son IP")
            } else {
                setStatus("Bluetooth : fais 1, 2, 3")
            }
        }

        adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null) {
            setStatus("Pas de Bluetooth : utilise le mode Wi-Fi")
        }

        findViewById<Button>(R.id.btnActivate).setOnClickListener { activate() }
        findViewById<Button>(R.id.btnVisible).setOnClickListener { makeVisible() }
        findViewById<Button>(R.id.btnPc).setOnClickListener { choosePc() }

        setupTouchpad(findViewById(R.id.touchpad))
        setupClick(R.id.btnLeft, 1)
        setupClick(R.id.btnRight, 2)
        setupKey(R.id.keyUp, KEY_W, SC_W)
        setupKey(R.id.keyLeft, KEY_A, SC_A)
        setupKey(R.id.keyDown, KEY_S, SC_S)
        setupKey(R.id.keyRight, KEY_D, SC_D)
        setupKey(R.id.keySpace, KEY_SPACE, SC_SPACE)

        askPermissions()
    }

    private fun setStatus(text: String) { status.text = text }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            val need = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 1)
        }
    }

    // Étape 1 : activer le mode clavier/souris Bluetooth
    private fun activate() {
        val a = adapter ?: return
        if (!a.isEnabled) {
            setStatus("Active le Bluetooth du téléphone d'abord")
            return
        }
        val ok = a.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "Relais", "Clavier et souris", "Relais",
                    BluetoothHidDevice.SUBCLASS1_COMBO, descriptor
                )
                val started = hid?.registerApp(sdp, null, null, executor, callback) ?: false
                if (!started) runOnUiThread {
                    setStatus("Impossible d'activer Relais sur ce téléphone")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = null
                    registered = false
                }
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) setStatus("Ce téléphone ne permet pas le mode clavier Bluetooth")
    }

    // Étape 2 : rendre le téléphone visible pour l'appairage avec le PC
    private fun makeVisible() {
        val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        startActivity(i)
    }

    // Étape 3 : choisir le PC déjà appairé
    private fun choosePc() {
        val h = hid
        if (h == null || !registered) {
            setStatus("Fais d'abord l'étape 1 (Activer)")
            return
        }
        val devices = adapter?.bondedDevices?.toList().orEmpty()
        if (devices.isEmpty()) {
            setStatus("Aucun appareil appairé. Appaire le PC dans les réglages Bluetooth.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Choisis ton PC")
            .setItems(devices.map { it.name ?: it.address }.toTypedArray()) { _, which ->
                h.connect(devices[which])
                setStatus("Connexion en cours...")
            }
            .show()
    }

    // ---- Envoi des commandes (Bluetooth ou Wi-Fi) ----

    private fun sendNet(msg: String) {
        val ip = ipField.text.toString().trim()
        if (ip.isEmpty()) return
        val pin = pinField.text.toString().trim()
        netExecutor.execute {
            try {
                val sock = socket ?: DatagramSocket().also { socket = it }
                val data = "$pin $msg".toByteArray()
                sock.send(DatagramPacket(data, data.size, InetAddress.getByName(ip), 5005))
            } catch (e: Exception) {
                // on ignore : le PC n'est peut-etre pas encore lance
            }
        }
    }

    private fun keyDown(usb: Int, scan: Int) {
        if (wifiMode) { sendNet("K $scan down"); return }
        val h = hid ?: return
        val d = host ?: return
        val report = ByteArray(8)
        report[2] = usb.toByte()
        h.sendReport(d, 1, report)
    }

    private fun keyUp(scan: Int) {
        if (wifiMode) { sendNet("K $scan up"); return }
        val h = hid ?: return
        val d = host ?: return
        h.sendReport(d, 1, ByteArray(8))
    }

    private fun mouseMove(dx: Int, dy: Int) {
        if (wifiMode) { sendNet("M $dx $dy"); return }
        val h = hid ?: return
        val d = host ?: return
        val report = byteArrayOf(
            buttonsMask.toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            0
        )
        h.sendReport(d, 2, report)
    }

    private fun mouseButton(mask: Int, down: Boolean) {
        if (wifiMode) { sendNet("B $mask ${if (down) "down" else "up"}"); return }
        buttonsMask = if (down) buttonsMask or mask else buttonsMask and mask.inv()
        val h = hid ?: return
        val d = host ?: return
        h.sendReport(d, 2, byteArrayOf(buttonsMask.toByte(), 0, 0, 0))
    }

    private fun setupTouchpad(pad: View) {
        var lastX = 0f
        var lastY = 0f
        var downTime = 0L
        var moved = 0f
        pad.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.x; lastY = e.y
                    downTime = e.eventTime; moved = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((e.x - lastX) * 1.5f).toInt()
                    val dy = ((e.y - lastY) * 1.5f).toInt()
                    moved += Math.abs(e.x - lastX) + Math.abs(e.y - lastY)
                    lastX = e.x; lastY = e.y
                    if (dx != 0 || dy != 0) mouseMove(dx, dy)
                }
                MotionEvent.ACTION_UP -> {
                    // Un petit tap = clic gauche
                    if (e.eventTime - downTime < 200 && moved < 20f) {
                        mouseButton(1, true)
                        mouseButton(1, false)
                    }
                }
            }
            true
        }
    }

    private fun setupClick(id: Int, mask: Int) {
        findViewById<Button>(id).setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> mouseButton(mask, true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mouseButton(mask, false)
            }
            true
        }
    }

    private fun setupKey(id: Int, usb: Int, scan: Int) {
        findViewById<Button>(id).setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> keyDown(usb, scan)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> keyUp(scan)
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        hid?.unregisterApp()
        adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
    }
}
