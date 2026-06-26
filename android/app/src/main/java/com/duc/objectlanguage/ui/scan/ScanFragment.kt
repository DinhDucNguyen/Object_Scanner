package com.duc.objectlanguage.ui.scan

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.DialogScanPreviewBinding
import com.duc.objectlanguage.databinding.FragmentScanBinding
import com.duc.objectlanguage.ui.collection.SaveToCollectionBottomSheet
import com.duc.objectlanguage.ui.common.GuestUpsellDialog
import com.duc.objectlanguage.ui.common.addPulseFeedback
import com.duc.objectlanguage.ui.common.addScaleFeedback
import com.duc.objectlanguage.ui.common.performLightHaptic
import com.duc.objectlanguage.ui.common.playSuccessBounce
import com.duc.objectlanguage.utils.DefinitionFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ScanViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var detectorHelper: ObjectDetectorHelper? = null
    private val detectorLock = Any()
    private var latestDetection: DetectionResult? = null
    private var selectedModelName = ObjectDetectorHelper.COCO_MODEL
    private var lastScannedBitmap: Bitmap? = null

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
                if (e is CancellationException) throw e
                Toast.makeText(requireContext(), getString(R.string.scan_error_read_image_detail, e.message), Toast.LENGTH_LONG).show()
                Log.e("ScanFragment", "Gallery read error", e)
                restartCameraIfPermitted()
            }
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val croppedUri = UCrop.getOutput(data) ?: return@registerForActivityResult
            try {
                val croppedBytes = requireContext().contentResolver.openInputStream(croppedUri)?.use { it.readBytes() }
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
            binding.btnCapture.isEnabled = !loading
            if (loading) {
                hideScanError()
                binding.loadingContainer.alpha = 0f
                binding.loadingContainer.scaleX = 0.82f
                binding.loadingContainer.scaleY = 0.82f
                binding.loadingContainer.visibility = View.VISIBLE
                binding.loadingContainer.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                    .start()
            } else {
                binding.loadingContainer.animate()
                    .alpha(0f).scaleX(0.88f).scaleY(0.88f)
                    .setDuration(180)
                    .withEndAction {
                        binding.loadingContainer.visibility = View.GONE
                        binding.loadingContainer.scaleX = 1f
                        binding.loadingContainer.scaleY = 1f
                        binding.loadingContainer.alpha = 1f
                    }
                    .start()
            }
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                if (result.source == "gemini_quota_exceeded") {
                    viewModel.clearResult()
                    showScanError(
                        getString(R.string.scan_error_title_quota),
                        getString(R.string.scan_error_gemini_quota)
                    )
                    return@observe
                }

                if (result.source == "gemini_failed" || result.objectCode == "unknown") {
                    viewModel.clearResult()
                    showScanError(
                        getString(R.string.scan_error_title_not_recognized),
                        getString(R.string.scan_error_not_recognized)
                    )
                    return@observe
                }

                val isPending = result.pendingReview

                // ── Bước 1: set data & visibility trước (view còn ẩn) ──

                if (lastScannedBitmap != null) {
                    binding.ivScannedImage.setImageBitmap(lastScannedBitmap)
                    binding.ivScannedImage.visibility = View.VISIBLE
                } else {
                    binding.ivScannedImage.visibility = View.GONE
                }

                val source = viewModel.detectionSource.value
                binding.tvDetectionSource.apply {
                    text = if (isPending) {
                        val pending = getString(R.string.scan_pending)
                        if (source != null) "$pending · $source" else pending
                    } else {
                        source ?: getString(R.string.scan_category_ai)
                    }
                    visibility = View.VISIBLE
                }

                val enTrans = result.translations.firstOrNull { it.languageCode == "en" }
                    ?: result.translations.firstOrNull()

                if (enTrans != null) {
                    binding.tvObjectName.text = enTrans.wordName.replaceFirstChar { it.uppercase() }
                    binding.tvPhonetic.apply {
                        text = enTrans.phonetic ?: ""
                        visibility = if (enTrans.phonetic.isNullOrEmpty()) View.GONE else View.VISIBLE
                    }
                    binding.tvPartOfSpeech.apply {
                        text = if (!enTrans.partOfSpeech.isNullOrEmpty()) "(${enTrans.partOfSpeech})" else ""
                        visibility = if (enTrans.partOfSpeech.isNullOrEmpty()) View.GONE else View.VISIBLE
                    }
                    val def = enTrans.definition ?: ""
                    if (def.isNotEmpty()) {
                        binding.cardDefinition.visibility = View.VISIBLE
                        val colonIndex = def.indexOf(':')
                        if (colonIndex != -1) {
                            binding.tvDefinitionTitle.text = def.substring(0, colonIndex).trim()
                            binding.tvDefinitionDesc.apply {
                                text = def.substring(colonIndex + 1).trim()
                                visibility = View.VISIBLE
                            }
                        } else {
                            binding.tvDefinitionTitle.text = def
                            binding.tvDefinitionDesc.visibility = View.GONE
                        }
                    } else {
                        binding.cardDefinition.visibility = View.GONE
                    }
                    if (!viewModel.isGuest) {
                        binding.btnPlayAudio.visibility = View.VISIBLE
                        binding.btnPlayAudio.addPulseFeedback()
                        binding.btnPlayAudio.setOnClickListener {
                            viewModel.playAudio(enTrans.audioUrl, enTrans.wordName, "en")
                        }
                        if (isPending) {
                            binding.btnAddToCollection.visibility = View.GONE
                            binding.tvPendingCollectionHint.visibility = View.VISIBLE
                        } else {
                            binding.tvPendingCollectionHint.visibility = View.GONE
                            binding.btnAddToCollection.visibility = View.VISIBLE
                            binding.btnAddToCollection.setOnClickListener {
                                binding.btnAddToCollection.playSuccessBounce()
                                SaveToCollectionBottomSheet.newInstance(enTrans.id)
                                    .show(childFragmentManager, "save_to_collection")
                            }
                        }
                    } else {
                        binding.btnPlayAudio.visibility = View.GONE
                        binding.btnAddToCollection.visibility = View.GONE
                        binding.tvPendingCollectionHint.visibility = if (isPending) View.VISIBLE else View.GONE
                    }
                } else {
                    binding.tvObjectName.text = result.objectCode.replaceFirstChar { it.uppercase() }
                    binding.tvPhonetic.visibility = View.GONE
                    binding.tvPartOfSpeech.visibility = View.GONE
                    binding.cardDefinition.visibility = View.GONE
                    binding.btnPlayAudio.visibility = View.GONE
                    binding.btnAddToCollection.visibility = View.GONE
                    binding.tvPendingCollectionHint.visibility = if (isPending) View.VISIBLE else View.GONE
                }

                showAliases(result.aliases)

                // ── Bước 2: animate SAU khi data đã set xong ──
                hideCameraControls()
                showResultWithAnimation()
            } else {
                hideResultAndShowCamera()
            }
        }

        viewModel.examples.observe(viewLifecycleOwner) { items ->
            binding.llExamples.removeAllViews()
            if (items.isNotEmpty()) {
                binding.llExamplesContainer.visibility = View.VISIBLE
                val inflater = LayoutInflater.from(requireContext())
                items.forEachIndexed { i, item ->
                    val itemView = inflater.inflate(R.layout.item_example_sentence, binding.llExamples, false)
                    val tvIndex = itemView.findViewById<android.widget.TextView>(R.id.tvIndex)
                    val tvEn = itemView.findViewById<android.widget.TextView>(R.id.tvEnSentence)
                    val tvVi = itemView.findViewById<android.widget.TextView>(R.id.tvViTranslation)
                    
                    tvIndex.text = getString(R.string.format_example_index, i + 1)
                    tvEn.text = getString(R.string.format_quoted_text, item.en)
                    if (!item.vi.isNullOrBlank()) {
                        tvVi.text = item.vi
                        tvVi.visibility = View.GONE
                        itemView.setOnClickListener {
                            tvVi.visibility = if (tvVi.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        }
                    } else {
                        tvVi.visibility = View.GONE
                        itemView.setOnClickListener(null)
                    }
                    binding.llExamples.addView(itemView)
                }
            } else {
                binding.llExamplesContainer.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                when (err) {
                    getString(R.string.scan_error_gemini_quota) -> showScanError(
                        getString(R.string.scan_error_title_quota),
                        err
                    )
                    getString(R.string.scan_error_not_recognized) -> showScanError(
                        getString(R.string.scan_error_title_not_recognized),
                        err
                    )
                    else -> Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                }
                viewModel.clearError()
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

        binding.btnCapture.addScaleFeedback(scale = 0.88f)
        binding.btnCapture.setOnClickListener {
            hideScanError()
            binding.btnCapture.performLightHaptic()
            playShutterFlash()
            captureImage()
        }

        binding.btnGallery.setOnClickListener {
            hideScanError()
            openGalleryWithPermission()
        }

        binding.btnScanErrorGallery.setOnClickListener {
            hideScanError()
            openGalleryWithPermission()
        }

        binding.btnScanErrorRetake.setOnClickListener {
            binding.btnScanErrorRetake.performLightHaptic()
            hideScanError()
            viewModel.clearResult()
            restartCameraIfPermitted()
        }

        binding.btnRetake.addScaleFeedback()
        binding.btnRetake.setOnClickListener {
            binding.btnRetake.performLightHaptic()
            hideScanError()
            viewModel.clearResult()
            restartCameraIfPermitted()
        }

        binding.btnScanOptions.setOnClickListener {
            binding.btnScanOptions.performLightHaptic()
            showModelOptionsSheet()
        }
    }

    private fun initDetector(modelName: String) {
        synchronized(detectorLock) {
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
    }

    private fun showModelOptionsSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_scan_model_options, binding.root, false)
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.radioModelGroup)
        radioGroup.check(
            if (selectedModelName == ObjectDetectorHelper.COCO_MODEL) {
                R.id.radioCocoModel
            } else {
                R.id.radioCustomModel
            }
        )
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val modelName = if (checkedId == R.id.radioCustomModel) {
                ObjectDetectorHelper.CUSTOM_MODEL
            } else {
                ObjectDetectorHelper.COCO_MODEL
            }
            if (modelName != selectedModelName || detectorHelper == null) {
                selectedModelName = modelName
                initDetector(modelName)
            }
            sheet.dismiss()
        }
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCloseModelOptions)
            .setOnClickListener { sheet.dismiss() }
        sheet.setContentView(view)
        sheet.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> syncScanOptionsIcon(1f)
                    BottomSheetBehavior.STATE_HIDDEN,
                    BottomSheetBehavior.STATE_COLLAPSED -> syncScanOptionsIcon(0f)
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_HALF_EXPANDED,
                    BottomSheetBehavior.STATE_SETTLING -> Unit
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                syncScanOptionsIcon(slideOffset.coerceIn(0f, 1f))
            }
        })
        sheet.setOnDismissListener { resetScanOptionsIcon() }
        sheet.show()
    }

    private fun syncScanOptionsIcon(progress: Float) {
        binding.btnScanOptions.animate().cancel()
        val scale = 1f - (0.06f * progress)
        binding.btnScanOptions.rotation = 180f * progress
        binding.btnScanOptions.scaleX = scale
        binding.btnScanOptions.scaleY = scale
    }

    private fun resetScanOptionsIcon() {
        binding.btnScanOptions.animate().cancel()
        binding.btnScanOptions.animate()
            .rotation(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(180)
            .setInterpolator(DecelerateInterpolator())
            .start()
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
                        
                        val framedBitmap = cropBitmapToPreviewAspect(rotatedBitmap)
                        if (framedBitmap !== rotatedBitmap) {
                            rotatedBitmap.recycle()
                        }

                        // Convert sang JPEG bytes
                        val jpegBytes = bitmapToJpegBytes(framedBitmap)
                        
                        // YOLO runs after the user confirms the preview.
                        showPreviewDialog(jpegBytes, framedBitmap)
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

    private fun showScanError(title: String, message: String) {
        binding.tvScanErrorTitle.text = title
        binding.tvScanErrorMessage.text = message
        binding.scanErrorCard.alpha = 0f
        binding.scanErrorCard.translationY = 18f
        binding.scanErrorCard.visibility = View.VISIBLE
        binding.scanErrorCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideScanError() {
        binding.scanErrorCard.visibility = View.GONE
        binding.scanErrorCard.alpha = 1f
        binding.scanErrorCard.translationY = 0f
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
        val bitmap = previewBitmap ?: BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        lastScannedBitmap = bitmap

        val dialogBinding = DialogScanPreviewBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext()).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            setOnCancelListener { restartCameraIfPermitted() }
        }

        dialogBinding.previewImage.setImageBitmap(bitmap)
        fitPreviewImageToBitmap(dialogBinding.previewImage, bitmap)
        dialogBinding.btnScanNow.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val yoloResult = withContext(Dispatchers.Default) {
                    val bmp = previewBitmap ?: BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    synchronized(detectorLock) {
                        detectorHelper?.detectBitmap(bmp)
                    }
                }
                viewModel.scanWithDetection(yoloResult, imageBytes, selectedModelName)
            }
        }
        dialogBinding.btnCropAgain.setOnClickListener {
            dialog.dismiss()
            launchCrop(imageBytes)
        }
        dialogBinding.btnRetakePreview.setOnClickListener {
            dialog.dismiss()
            restartCameraIfPermitted()
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.62f)
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).roundToInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun fitPreviewImageToBitmap(imageView: View, bitmap: Bitmap) {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val maxWidth = (metrics.widthPixels * 0.72f).roundToInt()
        val maxHeight = (metrics.heightPixels * 0.48f).roundToInt()
        val minWidth = (220f * density).roundToInt()
        val minHeight = (260f * density).roundToInt()

        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        var targetHeight = maxHeight
        var targetWidth = (targetHeight * aspect).roundToInt()
        if (targetWidth > maxWidth) {
            targetWidth = maxWidth
            targetHeight = (targetWidth / aspect).roundToInt()
        }

        imageView.layoutParams = imageView.layoutParams.apply {
            width = targetWidth.coerceAtLeast(minWidth).coerceAtMost(maxWidth)
            height = targetHeight.coerceAtLeast(minHeight).coerceAtMost(maxHeight)
        }
    }

    private fun cropBitmapToPreviewAspect(bitmap: Bitmap): Bitmap {
        val previewWidth = binding.previewView.width
        val previewHeight = binding.previewView.height
        if (previewWidth <= 0 || previewHeight <= 0) return bitmap

        val targetAspect = previewWidth.toFloat() / previewHeight.toFloat()
        val sourceAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (abs(targetAspect - sourceAspect) < 0.01f) return bitmap

        val cropWidth: Int
        val cropHeight: Int
        if (sourceAspect > targetAspect) {
            cropHeight = bitmap.height
            cropWidth = (bitmap.height * targetAspect).roundToInt().coerceIn(1, bitmap.width)
        } else {
            cropWidth = bitmap.width
            cropHeight = (bitmap.width / targetAspect).roundToInt().coerceIn(1, bitmap.height)
        }

        val left = ((bitmap.width - cropWidth) / 2f).roundToInt()
        val top = ((bitmap.height - cropHeight) / 2f).roundToInt()
        return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
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
            viewModel.scanWithDetection(null, imageBytes, selectedModelName)
        }
    }

    private fun showResultWithAnimation() {
        binding.resultCard.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 180f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(380)
                .setInterpolator(DecelerateInterpolator(2.2f))
                .start()
        }

        // Ảnh: scale từ nhỏ + fade in, có overshoot nhẹ
        binding.ivScannedImage.apply {
            scaleX = 0.82f
            scaleY = 0.82f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(160)
                .setDuration(320)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }

        // Tên từ: slide lên + fade
        binding.tvObjectName.apply {
            alpha = 0f
            translationY = 24f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(260)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Phonetic + definition card: stagger thêm 80ms
        listOf(binding.tvPhonetic, binding.tvPartOfSpeech, binding.cardDefinition).forEachIndexed { i, v ->
            v.apply {
                alpha = 0f
                translationY = 16f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(340L + i * 80L)
                    .setDuration(240)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun hideCameraControls() {
        val cameraViews = listOf(
            binding.cameraOverlay, binding.btnScanOptions,
            binding.cameraControls,
            binding.btnCapture, binding.btnGallery, binding.tvGuestCounter
        )
        cameraViews.forEach { v ->
            v.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction { v.visibility = View.GONE; v.alpha = 1f }
                .start()
        }
    }

    private fun hideResultAndShowCamera() {
        binding.llAliasesContainer.visibility = View.GONE
        binding.llAliasChips.removeAllViews()
        binding.resultCard.animate()
            .alpha(0f)
            .translationY(120f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.resultCard.visibility = View.GONE
                binding.resultCard.translationY = 0f
                binding.resultCard.alpha = 1f

                val cameraViews = mutableListOf(
                    binding.cameraOverlay, binding.btnScanOptions,
                    binding.cameraControls,
                    binding.btnCapture, binding.btnGallery
                )
                if (viewModel.isGuest) cameraViews.add(binding.tvGuestCounter)
                cameraViews.forEach { v ->
                    v.alpha = 0f
                    v.visibility = View.VISIBLE
                    v.animate().alpha(1f).setDuration(260).start()
                }
            }
            .start()
    }

    private fun playShutterFlash() {
        val flash = binding.shutterFlash
        flash.visibility = View.VISIBLE
        flash.alpha = 0.85f
        flash.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction { flash.visibility = View.INVISIBLE }
            .start()
    }

    private fun showAliases(aliases: List<String>) {
        binding.llAliasChips.removeAllViews()
        if (aliases.isEmpty()) {
            binding.llAliasesContainer.visibility = View.GONE
            return
        }
        aliases.forEach { name ->
            val chip = TextView(requireContext()).apply {
                text = name
                setTextColor(resources.getColor(R.color.primary, requireContext().theme))
                textSize = 13f
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt()
                )
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_surface)
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = (8 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            binding.llAliasChips.addView(chip)
        }
        binding.llAliasesContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        synchronized(detectorLock) {
            detectorHelper?.close()
            detectorHelper = null
        }
        cameraExecutor.shutdown()
        _binding = null
    }
}

