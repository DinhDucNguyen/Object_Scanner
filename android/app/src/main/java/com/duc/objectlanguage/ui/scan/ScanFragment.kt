package com.duc.objectlanguage.ui.scan

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentScanBinding
import com.duc.objectlanguage.ui.common.GuestUpsellDialog
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var detectorHelper: ObjectDetectorHelper? = null
    private var latestDetection: DetectionResult? = null
    private var selectedModelName = ObjectDetectorHelper.CUSTOM_MODEL

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(requireContext(), getString(R.string.scan_error_camera_permission), Toast.LENGTH_LONG).show()
    }

    private val requestGalleryPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openGallery()
        else Toast.makeText(requireContext(), getString(R.string.scan_error_gallery_permission), Toast.LENGTH_LONG).show()
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            restartCameraIfPermitted()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            try {
                val imageBytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (imageBytes == null || imageBytes.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.scan_error_read_image), Toast.LENGTH_SHORT).show()
                    restartCameraIfPermitted()
                    return@launch
                }
                val bitmap = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                }
                if (bitmap == null) {
                    Toast.makeText(requireContext(), getString(R.string.scan_error_invalid_image), Toast.LENGTH_SHORT).show()
                    restartCameraIfPermitted()
                    return@launch
                }
                showPreviewDialog(imageBytes, bitmap)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.scan_error_read_image_detail, e.message), Toast.LENGTH_LONG).show()
                Log.e("ScanFragment", "Gallery read error", e)
                restartCameraIfPermitted()
            }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val croppedUri = UCrop.getOutput(result.data!!) ?: return@registerForActivityResult
            try {
                val stream = requireContext().contentResolver.openInputStream(croppedUri)
                val croppedBytes = stream?.readBytes()
                stream?.close()
                if (croppedBytes != null) {
                    val croppedBitmap = BitmapFactory.decodeByteArray(croppedBytes, 0, croppedBytes.size)
                    showPreviewDialog(croppedBytes, croppedBitmap)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.scan_error_read_cropped_image, e.message), Toast.LENGTH_SHORT).show()
                restartCameraIfPermitted()
            }
        } else {
            restartCameraIfPermitted()
        }
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
            requestCameraPermission.launch(Manifest.permission.CAMERA)
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
                    Toast.makeText(requireContext(), getString(R.string.scan_error_gemini_quota), Toast.LENGTH_LONG).show()
                    return@observe
                }

                if (result.source == "gemini_failed" || result.objectCode == "unknown") {
                    viewModel.clearResult()
                    Toast.makeText(requireContext(), getString(R.string.scan_error_not_recognized), Toast.LENGTH_LONG).show()
                    return@observe
                }

                val isPending = result.pendingReview
                binding.resultCard.visibility = View.VISIBLE
                binding.cameraOverlay.visibility = View.GONE
                binding.modelSelectorContainer.visibility = View.GONE
                binding.tvGuestCounter.visibility = View.GONE
                binding.scanFrame.visibility = View.GONE
                binding.cameraControls.visibility = View.GONE
                binding.btnCapture.visibility = View.GONE
                binding.btnGallery.visibility = View.GONE
                binding.tvObjectName.text = result.objectCode.replaceFirstChar { it.uppercase() }
                val source = viewModel.detectionSource.value
                binding.tvCategory.text = if (isPending) {
                    val pending = getString(R.string.scan_pending)
                    if (source != null) "$pending · $source" else pending
                } else {
                    val category = result.categoryName ?: getString(R.string.scan_category_ai)
                    if (source != null) "$category · $source" else category
                }

                val enTrans = result.translations.firstOrNull { it.languageCode == "en" }
                    ?: result.translations.firstOrNull()
                if (enTrans != null) {
                    val sb = StringBuilder()
                    if (!enTrans.partOfSpeech.isNullOrEmpty()) {
                        sb.appendLine(getString(R.string.scan_translation_word, enTrans.wordName, enTrans.partOfSpeech))
                    } else {
                        sb.appendLine(enTrans.wordName)
                    }
                    if (!enTrans.phonetic.isNullOrEmpty()) sb.appendLine(enTrans.phonetic)
                    if (!enTrans.definition.isNullOrEmpty()) sb.appendLine(enTrans.definition)
                    binding.tvTranslations.text = sb.toString().trimEnd()

                    if (!viewModel.isGuest) {
                        binding.btnPlayAudio.visibility = View.VISIBLE
                        binding.btnPlayAudio.setOnClickListener {
                            viewModel.playAudio(enTrans.audioUrl, enTrans.wordName, "en")
                        }
                        if (isPending) {
                            binding.btnAddToCollection.visibility = View.GONE
                        } else {
                            binding.btnAddToCollection.visibility = View.VISIBLE
                            binding.btnAddToCollection.setOnClickListener {
                                viewModel.showAddToCollectionDialog(enTrans.id, requireContext())
                            }
                        }
                    } else {
                        binding.btnPlayAudio.visibility = View.GONE
                        binding.btnAddToCollection.visibility = View.GONE
                    }
                }
            } else {
                binding.resultCard.visibility = View.GONE
                binding.cameraOverlay.visibility = View.VISIBLE
                binding.modelSelectorContainer.visibility = View.VISIBLE
                binding.scanFrame.visibility = View.VISIBLE
                binding.cameraControls.visibility = View.VISIBLE
                binding.btnCapture.visibility = View.VISIBLE
                binding.btnGallery.visibility = View.VISIBLE
                if (viewModel.isGuest) {
                    binding.tvGuestCounter.visibility = View.VISIBLE
                }
            }
        }

        viewModel.examples.observe(viewLifecycleOwner) { items ->
            binding.llExamples.removeAllViews()
            if (items.isNotEmpty()) {
                binding.llExamplesContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(requireContext())
                items.forEachIndexed { i, item ->
                    val itemView = inflater.inflate(R.layout.item_example_sentence, binding.llExamples, false)
                    val tvEn = itemView.findViewById<android.widget.TextView>(R.id.tvEnSentence)
                    val tvVi = itemView.findViewById<android.widget.TextView>(R.id.tvViTranslation)
                    tvEn.text = "${i + 1}. ${item.en}"
                    if (!item.vi.isNullOrBlank()) {
                        tvVi.text = item.vi
                        itemView.setOnClickListener {
                            tvVi.visibility = if (tvVi.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        }
                    }
                    binding.llExamples.addView(itemView)
                }
            } else {
                binding.llExamplesContainer.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                if (viewModel.scanResult.value == null) restartCameraIfPermitted()
            }
        }

        viewModel.addedMsg.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearAddedMsg()
            }
        }

        viewModel.remainingScans.observe(viewLifecycleOwner) { remaining ->
            if (viewModel.isGuest) {
                binding.tvGuestCounter.text = getString(R.string.guest_scan_counter, remaining)
            }
        }

        viewModel.showGuestUpsell.observe(viewLifecycleOwner) { show ->
            if (show) {
                GuestUpsellDialog.show(
                    context = requireContext(),
                    reason = GuestUpsellDialog.Reason.SCAN_LIMIT,
                    onLogin = { findNavController().navigate(R.id.action_scan_to_login) },
                    onRegister = { findNavController().navigate(R.id.action_scan_to_register) }
                )
                viewModel.clearGuestUpsell()
            }
        }

        // Hiển thị counter ngay khi vào màn hình (chỉ với guest)
        if (viewModel.isGuest) {
            binding.tvGuestCounter.visibility = View.VISIBLE
        }

        // ===== Buttons =====

        binding.btnCapture.setOnClickListener {
            captureImage()
        }

        binding.btnGallery.setOnClickListener {
            openGalleryWithPermission()
        }

        binding.btnRetake.setOnClickListener {
            viewModel.clearResult()
            restartCameraIfPermitted()
        }

        binding.modelToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val modelName = selectedModelFromToggle(checkedId)
            if (modelName != selectedModelName || detectorHelper == null) {
                selectedModelName = modelName
                initDetector(modelName)
            }
        }
    }

    private fun selectedModelFromToggle(checkedId: Int = binding.modelToggleGroup.checkedButtonId): String =
        if (checkedId == R.id.btnCocoModel) {
            ObjectDetectorHelper.COCO_MODEL
        } else {
            ObjectDetectorHelper.CUSTOM_MODEL
        }

    private fun initDetector(modelName: String) {
        Log.d("ScanFragment", "Using detector model: ${ObjectDetectorHelper.displayNameForModel(modelName)} ($modelName)")
        detectorHelper?.close()
        detectorHelper = ObjectDetectorHelper(
            context = requireContext(),
            modelName = modelName,
        ) { event ->
            if (event is DetectionEvent.Failure) {
                Log.e("ObjectDetector", "YOLO load failed: ${event.message}")
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            if (_binding == null) return@addListener
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            selectedModelName = selectedModelFromToggle()
            initDetector(selectedModelName)

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.scan_error_camera_detail, e.message), Toast.LENGTH_LONG).show()
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
                        // Convert ImageProxy to Bitmap and keep the captured orientation.
                        val bitmap = imageProxyToBitmap(image)
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()
                        
                        // Apply rotation
                        val rotatedBitmap = rotateBitmap(bitmap, rotation.toFloat())
                        if (rotatedBitmap !== bitmap) {
                            bitmap.recycle()
                        }
                        
                        // Convert sang JPEG bytes
                        val jpegBytes = bitmapToJpegBytes(rotatedBitmap)
                        
                        Log.d("ScanFragment", "Captured: ${rotatedBitmap.width}x${rotatedBitmap.height}, Rotation: $rotation°, Size: ${jpegBytes.size} bytes")
                        
                        // YOLO runs after the user confirms the preview.
                        showPreviewDialog(jpegBytes, rotatedBitmap)
                    } catch (e: Exception) {
                        image.close()
                        Toast.makeText(requireContext(), getString(R.string.scan_error_process_image, e.message), Toast.LENGTH_LONG).show()
                        Log.e("ScanFragment", "Image processing error", e)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(requireContext(), getString(R.string.scan_error_capture, e.message), Toast.LENGTH_LONG).show()
                    Log.e("ScanFragment", "Capture error", e)
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

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
            .setTitle(getString(R.string.scan_preview_title))
            .setView(imageView)
            .setPositiveButton(getString(R.string.scan_preview_scan_now)) { dialog, _ ->
                dialog.dismiss()
                lifecycleScope.launch {
                    val yoloResult = withContext(Dispatchers.Default) {
                        val bmp = previewBitmap ?: BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        detectorHelper?.detectBitmap(bmp)
                    }
                    viewModel.scanWithDetection(yoloResult, imageBytes)
                }
            }
            .setNeutralButton(getString(R.string.scan_preview_crop_again)) { dialog, _ ->
                dialog.dismiss()
                launchCrop(imageBytes)
            }
            .setNegativeButton(getString(R.string.scan_preview_retake)) { dialog, _ ->
                dialog.dismiss()
                restartCameraIfPermitted()
            }
            .setOnCancelListener { restartCameraIfPermitted() }
            .setCancelable(true)
            .show()
    }

    private fun openGalleryWithPermission() {
        // Android 11+ (API 30): GetContent() uses system picker — no runtime permission needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openGallery()
            return
        }
        // Android < 11: needs READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            requestGalleryPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun openGallery() {
        stopCamera()
        pickImageLauncher.launch("image/*")
    }

    private fun stopCamera() {
        runCatching { cameraProvider?.unbindAll() }
        imageCapture = null
    }

    private fun restartCameraIfPermitted() {
        if (_binding == null) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    private fun launchCrop(imageBytes: ByteArray) {
        try {
            val srcFile = File.createTempFile("crop_src_", ".jpg", requireContext().cacheDir)
            srcFile.writeBytes(imageBytes)
            val dstFile = File.createTempFile("crop_dst_", ".jpg", requireContext().cacheDir)
            val options = UCrop.Options().apply {
                setFreeStyleCropEnabled(true)   // kéo tự do 4 cạnh
                setHideBottomControls(false)
                setShowCropGrid(true)
            }
            val intent = UCrop.of(Uri.fromFile(srcFile), Uri.fromFile(dstFile))
                .withMaxResultSize(1024, 1024)
                .withOptions(options)
                .getIntent(requireContext())
            cropLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.scan_error_open_crop, e.message), Toast.LENGTH_SHORT).show()
            viewModel.scanWithDetection(null, imageBytes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        detectorHelper?.close()
        cameraExecutor.shutdown()
        _binding = null
    }
}
