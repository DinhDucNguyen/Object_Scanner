package com.duc.objectlanguage.ui.scan

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.databinding.FragmentScanBinding
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(requireContext(), "Cần quyền camera", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        // ===== Observers =====

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingContainer.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnCapture.isEnabled = !loading
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                if (result.source == "gemini_quota_exceeded") {
                    viewModel.clearResult()
                    Toast.makeText(requireContext(), "Gemini đã hết quota, vui lòng thử lại sau.", Toast.LENGTH_LONG).show()
                    return@observe
                }

                if (result.source == "gemini_failed" || result.objectCode == "unknown") {
                    viewModel.clearResult()
                    Toast.makeText(requireContext(), "Không nhận diện được vật thể, vui lòng chụp rõ hơn.", Toast.LENGTH_LONG).show()
                    return@observe
                }

                binding.resultCard.visibility = View.VISIBLE
                binding.cameraOverlay.visibility = View.GONE
                binding.btnCapture.visibility = View.GONE
                binding.tvObjectName.text = result.objectCode.replaceFirstChar { it.uppercase() }
                binding.tvCategory.text = result.categoryName ?: "Nhận diện bằng AI"

                // Hiển thị translations
                val sb = StringBuilder()
                result.translations.forEach { t ->
                    val flag = getFlagEmoji(t.languageCode ?: "")
                    sb.appendLine("$flag ${t.languageName}: ${t.wordName}")
                    if (!t.phonetic.isNullOrEmpty()) sb.appendLine("   🔤 ${t.phonetic}")
                    if (!t.definition.isNullOrEmpty()) sb.appendLine("   📖 ${t.definition}")
                    sb.appendLine()
                }
                binding.tvTranslations.text = sb.toString().trimEnd()

                // Buttons cho từng translation
                if (result.translations.isNotEmpty()) {
                    val first = result.translations[0]
                    binding.btnPlayAudio.visibility = View.VISIBLE
                    binding.btnPlayAudio.setOnClickListener {
                        viewModel.playAudio(first.wordName, first.languageCode ?: "en")
                    }
                    binding.btnAddLearn.visibility = View.VISIBLE
                    binding.btnAddLearn.setOnClickListener {
                        viewModel.addToLearning(first.id)
                    }
                }
            } else {
                binding.resultCard.visibility = View.GONE
                binding.cameraOverlay.visibility = View.VISIBLE
                binding.btnCapture.visibility = View.VISIBLE
            }
        }

        viewModel.examples.observe(viewLifecycleOwner) { sentences ->
            if (sentences.isNotEmpty()) {
                binding.tvExamples.visibility = View.VISIBLE
                binding.tvExamples.text = "📝 Ví dụ:\n" + sentences.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            } else {
                binding.tvExamples.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.addedMsg.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearAddedMsg()
            }
        }

        // ===== Buttons =====

        binding.btnCapture.setOnClickListener {
            captureImage()
        }

        binding.btnRetake.setOnClickListener {
            viewModel.clearResult()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureImage() {
        val capture = imageCapture ?: return

        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        // ✅ FIX: Convert ImageProxy sang Bitmap (handle rotation)
                        val bitmap = imageProxyToBitmap(image)
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()
                        
                        // Apply rotation
                        val rotatedBitmap = rotateBitmap(bitmap, rotation.toFloat())
                        
                        // Convert sang JPEG bytes
                        val jpegBytes = bitmapToJpegBytes(rotatedBitmap)
                        
                        Log.d("ScanFragment", "Captured: ${rotatedBitmap.width}x${rotatedBitmap.height}, Rotation: $rotation°, Size: ${jpegBytes.size} bytes")
                        
                        // Hiển thị preview
                        showPreviewDialog(jpegBytes, rotatedBitmap)
                    } catch (e: Exception) {
                        image.close()
                        Toast.makeText(requireContext(), "Lỗi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("ScanFragment", "Image processing error", e)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Lỗi chụp: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("ScanFragment", "Capture error", e)
                }
            }
        )
    }

    /**
     * ✅ HÀM MỚI: Convert ImageProxy (YUV) sang Bitmap
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * ✅ HÀM MỚI: Rotate bitmap theo EXIF orientation
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * ✅ HÀM MỚI: Convert Bitmap sang JPEG bytes
     */
    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * ✅ HÀM MỚI: Hiển thị preview ảnh và dialog xác nhận
     * User có thể: Quét ngay, Crop lại, hoặc Chụp lại
     */
    private fun showPreviewDialog(imageBytes: ByteArray, previewBitmap: Bitmap? = null) {
        // Dùng bitmap đã có hoặc decode từ bytes
        val bitmap = previewBitmap ?: BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Tạo ImageView cho preview
        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(32, 32, 32, 32)
        }

        // Tạo dialog
        AlertDialog.Builder(requireContext())
            .setTitle("📸 Vật thể có rõ ràng không?")
            .setView(imageView)
            .setPositiveButton("✅ Quét ngay") { dialog, _ ->
                dialog.dismiss()
                // Gọi API chỉ khi user confirm
                viewModel.scanImage(imageBytes)
            }
            .setNeutralButton("✂️ Crop lại") { dialog, _ ->
                dialog.dismiss()
                // TODO: Mở crop activity (cần thêm UCrop library)
                // Tạm thời fallback về quét luôn
                Toast.makeText(requireContext(), "Tính năng crop đang phát triển. Quét ảnh gốc...", Toast.LENGTH_SHORT).show()
                viewModel.scanImage(imageBytes)
            }
            .setNegativeButton("🔄 Chụp lại") { dialog, _ ->
                dialog.dismiss()
                // Không làm gì, user quay lại camera
            }
            .setCancelable(true)
            .show()
    }

    /**
     * ✅ HÀM BỔ SUNG: Compress ảnh trước khi gửi API (giảm thời gian upload)
     */
    private fun compressImage(imageBytes: ByteArray, quality: Int = 85): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val stream = ByteArrayOutputStream()
        
        // Resize xuống max 800x800 nếu ảnh quá lớn
        val maxSize = 800
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        val resized = if (ratio < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else bitmap
        
        resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun getFlagEmoji(langCode: String): String = when (langCode) {
        "vi" -> "🇻🇳"
        "en" -> "🇺🇸"
        "ja" -> "🇯🇵"
        "ko" -> "🇰🇷"
        "zh" -> "🇨🇳"
        "fr" -> "🇫🇷"
        else -> "🌐"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
