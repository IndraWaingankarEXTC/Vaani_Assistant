package com.vaani.assistant.lens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.vaani.assistant.core.Vlog
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The lens: a full-screen camera that names what it is looking at.
 *
 * Two ways to use it. Live, it boxes and labels objects as you move the phone. For a real answer
 * you drag four corners around the device — or better, around its label — and press Read: the
 * selection is straightened, the model number is read off it, and the two are combined.
 *
 * Everything runs on-device. No frame leaves the phone.
 */
class LensActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var liveLabels: LiveLabelsView
    private lateinit var selector: CornerSelectorView
    private lateinit var status: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultText: TextView
    private lateinit var btnSelect: Button
    private lateinit var btnRead: Button
    private lateinit var btnTorch: Button

    private val vision = VisionEngine()
    private val executor = Executors.newSingleThreadExecutor()
    private var detector: ObjectDetector? = null
    private var camera: Camera? = null
    private var tts: TextToSpeech? = null

    private var frozen: Bitmap? = null
    private var croppedJpeg: ByteArray? = null
    private var selecting = false
    private var torchOn = false
    private var lastAnalyze = 0L

    private val tracker = ObjectTracker()
    private var identifying = false
    private var lastSpokenId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullScreen()
        buildUi()

        tts = TextToSpeech(this) { tts?.language = Locale("en", "IN") }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView)

        liveLabels = LiveLabelsView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    liveLabels.hitTestView(event.x, event.y)?.let { speakAbout(it) }
                }
                false
            }
        }
        root.addView(liveLabels)

        selector = CornerSelectorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            visibility = View.GONE
        }
        root.addView(selector)

        // Title row
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(40), dp(16), dp(8))
        }
        val title = TextView(this).apply {
            text = "Lens"
            setTextColor(Color.parseColor("#7CE3C1"))
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        topBar.addView(title)
        topBar.addView(close)
        root.addView(topBar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // Status line
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = "Point at a device. Drag four corners around it or its label."
            setPadding(dp(16), dp(6), dp(16), dp(6))
        }
        root.addView(status, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply {
            topMargin = dp(96)
        })

        // Result card
        resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE1B1B3A"))
            setPadding(dp(18), dp(14), dp(18), dp(14))
            visibility = View.GONE
        }
        resultText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        resultCard.addView(resultText)
        root.addView(resultCard, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
            bottomMargin = dp(120)
        })

        // Bottom controls
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(28))
        }
        btnSelect = Button(this).apply {
            text = "Select area"
            setOnClickListener { if (selecting) exitSelect() else enterSelect() }
        }
        btnTorch = Button(this).apply {
            text = "Torch"
            setOnClickListener { toggleTorch() }
        }
        btnRead = Button(this).apply {
            text = "Read this"
            visibility = View.GONE
            setOnClickListener { readSelection() }
        }
        bar.addView(btnSelect)
        bar.addView(btnRead)
        bar.addView(btnTorch)
        root.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        setContentView(root)
    }

    // ------------------------------------------------------------------ camera

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }

            detector = runCatching {
                ObjectDetection.getClient(
                    ObjectDetectorOptions.Builder()
                        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                        .enableMultipleObjects()
                        .build()
                )
            }.getOrNull()

            runCatching {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
                status.text = "Point at a device. Drag four corners around it or its label."
            }.onFailure {
                status.text = "The camera would not start: ${it.message}"
                Vlog.e("Lens", "bind failed: ${it.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun analyze(proxy: ImageProxy) {
        if (selecting) {
            proxy.close()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyze < 400) {
            proxy.close()
            return
        }
        lastAnalyze = now

        val upright = runCatching { uprightBitmap(proxy) }.getOrNull()
        val active = detector
        if (upright == null || active == null) {
            proxy.close()
            return
        }
        val width = upright.width
        val height = upright.height

        active.process(InputImage.fromBitmap(upright, 0))
            .addOnSuccessListener { objects ->
                val detections = objects.map {
                    Detection(
                        left = it.boundingBox.left.toFloat(),
                        top = it.boundingBox.top.toFloat(),
                        right = it.boundingBox.right.toFloat(),
                        bottom = it.boundingBox.bottom.toFloat(),
                        label = it.labels.firstOrNull()?.text
                    )
                }
                val tracks = tracker.update(detections, now)
                runOnUiThread { liveLabels.submit(tracks, width, height) }
                identifyNext(upright, width, height)
            }
            .addOnCompleteListener { proxy.close() }
    }

    /**
     * Read one new object per pass, at most. Every object is identified individually, but the
     * recogniser is far too slow to run on all of them every frame — and it does not need to,
     * because a result sticks to the object for as long as it is tracked.
     */
    private fun identifyNext(frame: Bitmap, width: Int, height: Int) {
        if (identifying) return
        val claimed = tracker.claimNextToIdentify(
            minAreaFraction = 0.05f,
            frameArea = width.toFloat() * height
        ) ?: return
        val box = claimed.box
        val left = box.left.toInt().coerceIn(0, width - 1)
        val top = box.top.toInt().coerceIn(0, height - 1)
        val right = box.right.toInt().coerceIn(left + 1, width)
        val bottom = box.bottom.toInt().coerceIn(top + 1, height)

        val crop = runCatching {
            Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        }.getOrNull() ?: return

        identifying = true
        val startedAt = SystemClock.elapsedRealtime()
        vision.read(
            crop,
            onResult = { reading ->
                identifying = false
                tracker.identify(claimed.id, reading, SystemClock.elapsedRealtime())
                runOnUiThread {
                    liveLabels.submit(tracker.current(), width, height)
                    status.text = if (reading.title == Reading.UNKNOWN) {
                        "I can't name that one yet — get closer, or use Select area on its label."
                    } else {
                        "${reading.title} · tap any object to hear it"
                    }
                }
                Vlog.i("Lens", "identified ${reading.title} in ${SystemClock.elapsedRealtime() - startedAt}ms")
            },
            onFailure = {
                identifying = false
                Vlog.e("Lens", "identify failed: $it")
            }
        )
    }

    /** Rotate the analysis frame upright once, so boxes, crops and the preview share coordinates. */
    @ExperimentalGetImage
    private fun uprightBitmap(proxy: ImageProxy): Bitmap? {
        val raw = proxy.toBitmap() ?: return null
        val degrees = proxy.imageInfo.rotationDegrees
        if (degrees == 0) return raw
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return runCatching {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }.getOrDefault(raw)
    }

    /** Tapping an object asks about that one specifically. */
    private fun speakAbout(track: Track) {
        if (track.id == lastSpokenId && !track.identified) return
        lastSpokenId = track.id
        val line = when {
            track.title != null && track.detail != null ->
                "${track.title} — ${track.detail}, about ${(track.confidence * 100).toInt()} percent sure."
            track.title != null -> "${track.title}."
            else -> "That looks like ${track.caption}. I'm still reading it."
        }
        status.text = line
        speak(line)
    }

    // ------------------------------------------------------------------ selecting

    private fun enterSelect() {
        val bitmap = previewView.bitmap
        if (bitmap == null) {
            status.text = "The camera is not ready yet."
            return
        }
        frozen = bitmap
        selecting = true
        liveLabels.visibility = View.GONE
        selector.visibility = View.VISIBLE
        selector.setFrame(bitmap)
        btnSelect.text = "Cancel"
        btnRead.visibility = View.VISIBLE
        resultCard.visibility = View.GONE
        status.text = "Drag the four corners over the device, or over its label."
    }

    private fun exitSelect() {
        selecting = false
        frozen = null
        selector.clearFrame()
        selector.visibility = View.GONE
        liveLabels.visibility = View.VISIBLE
        tracker.reset()
        btnSelect.text = "Select area"
        btnRead.visibility = View.GONE
        status.text = "Point at a device. Drag four corners around it or its label."
    }

    private fun readSelection() {
        val source = frozen ?: return
        val quad = selector.quadInBitmap() ?: return
        if (!Quad.isUsable(quad, frameArea = selector.frameArea())) {
            status.text = "That selection is too small — put the corners around the device or its label."
            return
        }
        val cropped = Perspective.warp(source, quad)
        croppedJpeg = cropped?.let { bmp ->
            java.io.ByteArrayOutputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                stream.toByteArray()
            }
        }
        if (cropped == null) {
            status.text = "I could not straighten that selection."
            return
        }
        status.text = "Reading…"
        btnRead.isEnabled = false
        vision.read(
            cropped,
            onResult = { reading ->
                btnRead.isEnabled = true
                showReading(reading)
            },
            onFailure = { message ->
                btnRead.isEnabled = true
                status.text = "I could not read that: $message"
            }
        )
    }

    private fun showReading(reading: Reading) {
        lastTitle = reading.title.takeIf { it != Reading.UNKNOWN }
        lastJpeg = croppedJpeg
        resultCard.visibility = View.VISIBLE
        resultText.text = reading.bullets().joinToString("\n") { "•  $it" }
        status.text = "Tap Select area to try another angle."
        speak(reading.spoken())
        Vlog.i("Lens", "read: ${reading.title} (${(reading.confidence * 100).toInt()}%)")
    }

    private fun speak(text: String) {
        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lens")
        }
    }

    private fun toggleTorch() {
        torchOn = !torchOn
        val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true
        if (!hasTorch) {
            status.text = "This camera has no torch."
            torchOn = false
            return
        }
        camera?.cameraControl?.enableTorch(torchOn)
        btnTorch.text = if (torchOn) "Torch off" else "Torch"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            status.text = "The lens needs the camera. Grant it and open the lens again."
        }
    }

    override fun onDestroy() {
        runCatching { detector?.close() }
        runCatching { vision.close() }
        runCatching { executor.shutdown() }
        runCatching { tts?.shutdown() }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** What the lens last identified, so "remember this is my keys" knows what "this" is. */
        @Volatile var lastTitle: String? = null
        @Volatile var lastJpeg: ByteArray? = null

        private const val REQ_CAMERA = 900
        const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
        const val WRAP = FrameLayout.LayoutParams.WRAP_CONTENT
    }
}
