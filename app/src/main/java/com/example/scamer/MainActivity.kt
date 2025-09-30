package com.example.scamer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var qrCodeImageUri: Uri? = null
    private var qrCodeUrl: String? = null
    private var qrCodeBitmap: Bitmap? = null

    private val cameraLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                qrCodeBitmap = it // Зберігаємо зображення для подальшого використання
                imageView.setImageBitmap(it)
                processQRCode(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        imageView = findViewById(R.id.imageView)
        val captureButton = findViewById<Button>(R.id.captureButton)
        val sendButton = findViewById<Button>(R.id.sendButton)

        captureButton.setOnClickListener { checkPermissionsAndLaunchCamera() }
        sendButton.setOnClickListener { shareQRCodeImage() }
    }

    private fun checkPermissionsAndLaunchCamera() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            launchCamera()
        }
    }

    private fun launchCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            cameraLauncher.launch(cameraIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Camera app not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processQRCode(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner: BarcodeScanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qrCode = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                if (qrCode != null) {
                    qrCodeUrl = qrCode.url?.url ?: qrCode.displayValue
                    qrCodeUrl?.let {
                        // Відкриваємо URL лише після збереження зображення
                        saveImage(bitmap)
                        openUrlInBrowser(it)
                    } ?: run {
                        Toast.makeText(this, "No URL found in the QR code", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No QR code found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openUrlInBrowser(url: String) {
        val modifiedUrl = "http://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(modifiedUrl))
        startActivity(intent)
    }

    private fun saveImage(bitmap: Bitmap) {
        try {
            val file = File(filesDir, "qrcode_image.jpg")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 10, out)
            out.flush()
            out.close()
            qrCodeImageUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareQRCodeImage() {
        if (qrCodeImageUri == null || qrCodeUrl == null) {
            Toast.makeText(this, "No image or QR code URL to share", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, qrCodeImageUri)
            putExtra(Intent.EXTRA_TEXT, "Посилання з QR-коду: $qrCodeUrl")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Share via..."))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No compatible app found", Toast.LENGTH_SHORT).show()
        }
    }

    // Зберігаємо стан зображення та URL під час зміни конфігурації або повернення з іншого додатку
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("qrCodeBitmap", qrCodeBitmap)
        outState.putString("qrCodeUrl", qrCodeUrl)
        qrCodeImageUri?.let { outState.putString("qrCodeImageUri", it.toString()) }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        qrCodeBitmap = savedInstanceState.getParcelable("qrCodeBitmap")
        qrCodeUrl = savedInstanceState.getString("qrCodeUrl")
        qrCodeImageUri = savedInstanceState.getString("qrCodeImageUri")?.let { Uri.parse(it) }

        // Відновлюємо зображення та URL, якщо вони існують
        qrCodeBitmap?.let { imageView.setImageBitmap(it) }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 101
    }
}
