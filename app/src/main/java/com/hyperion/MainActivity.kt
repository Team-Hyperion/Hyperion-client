package com.hyperion

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.VideoCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {
    val TAG = MainActivity::class.java.simpleName
    var isRecording = false

    var CAMERA_PERMISSION = Manifest.permission.CAMERA
    var RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    var RC_PERMISSION = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recordFiles = ContextCompat.getExternalFilesDirs(this, Environment.getExternalStorageState())
        val storageDirectory = recordFiles[0]
        val videoRecordingFilePath = "${storageDirectory.absoluteFile}/${System.currentTimeMillis()}_video.mp4"
        val imageCaptureFilePath = "${storageDirectory.absoluteFile}/${System.currentTimeMillis()}_image.jpg"

        if (checkPermissions()) startCameraSession() else requestPermissions()

        video_record.setOnClickListener {
            if (isRecording) {
                isRecording = false
                video_record.text = "Record Video"
                Toast.makeText(this, "Recording Stopped", Toast.LENGTH_SHORT).show()
                camera_view.stopRecording()
            } else {
                isRecording = true
                video_record.text = "Stop Recording"
                Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show()
                recordVideo(videoRecordingFilePath)
            }
        }

        capture_image.setOnClickListener {
            captureImage(imageCaptureFilePath)
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION, RECORD_AUDIO_PERMISSION), RC_PERMISSION)
    }

    private fun checkPermissions(): Boolean {
        return ((ActivityCompat.checkSelfPermission(this, CAMERA_PERMISSION)) == PackageManager.PERMISSION_GRANTED
                && (ActivityCompat.checkSelfPermission(this, CAMERA_PERMISSION)) == PackageManager.PERMISSION_GRANTED)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            RC_PERMISSION -> {
                var allPermissionsGranted = false
                for (result in grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false
                        break
                    } else {
                        allPermissionsGranted = true
                    }
                }
                if (allPermissionsGranted) startCameraSession() else permissionsNotGranted()
            }
        }
    }

    private fun startCameraSession() {
        camera_view.bindToLifecycle(this)
    }

    private fun permissionsNotGranted() {
        AlertDialog.Builder(this).setTitle("Permissions required")
                .setMessage("These permissions are required to use this app. Please allow Camera and Audio permissions first")
                .setCancelable(false)
                .setPositiveButton("Grant") { dialog, which -> requestPermissions() }
                .show()
    }

    private fun recordVideo(videoRecordingFilePath: String) {
        camera_view.startRecording(File(videoRecordingFilePath), ContextCompat.getMainExecutor(this), object: VideoCapture.OnVideoSavedCallback {
            override fun onVideoSaved(file: File) {
                Toast.makeText(this@MainActivity, "Recording Saved", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "onVideoSaved $videoRecordingFilePath")
                runScript(serverIp, serverPort, "V", videoRecordingFilePath)
            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                Toast.makeText(this@MainActivity, "Recording Failed", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onError $videoCaptureError $message")
            }
        })
    }

    private fun captureImage(imageCaptureFilePath: String) {
        camera_view.takePicture(File(imageCaptureFilePath), ContextCompat.getMainExecutor(this), object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Toast.makeText(this@MainActivity, "Image Captured", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "onImageSaved $imageCaptureFilePath")
                runScript(serverIp, serverPort, "P", imageCaptureFilePath)
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(this@MainActivity, "Image Capture Failed", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "onError $exception")
            }
        })
    }


    private var serverIp: String = getServerIP()
    private var serverPort = 34200

    /**
     * Retrieves the local ip address of the device
     *
     * @return The local IPv4 address to be used as the server ip (for demo/local testing purposes)
     */
    private fun getServerIP(): String {
        try {
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("1.1.1.1"), serverPort)
                val wifiManager: WifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val ip: String = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                return ip
                //return socket.localAddress.hostAddress
            }
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            return "-1"
        } catch (e: SocketException) {
            e.printStackTrace()
            return "-1"
        }
    }

    /**
     * Setter for server_ip field
     *
     * @param ip Server IP to be used by this client app when sending out data
     */
    private fun setServerIP(ip: String?) {
        if (ip != null) {
            serverIp = ip
        }
    }

    /**
     * Setter for server_port field
     *
     * @param port Server Port to be used by this client app when sending out data
     */
    private fun setServerPort(port: Int) {
        serverPort = port
    }

    /**
     * Runs the python script that connects the client to the server
     *
     * @param server_ip The server's IPv4 address
     * @param server_port The server's port
     * @param format Either P or V, for sending a picture or video, respectively
     * @param path The absolute path to the required picture or video
     * @throws IOException If an I/O error occurs
     */
    @Throws(IOException::class)
    private fun runScript(server_ip: String, server_port: Int, format: String, path: String) {
        val cmd = arrayOf(
                "python",
                "client.py",
                server_ip,
                server_port.toString(),
                format,
                path
        )
        Runtime.getRuntime().exec(cmd)
    }
}
