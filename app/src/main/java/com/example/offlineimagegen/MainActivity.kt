package com.example.offlineimagegen

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGeneratorResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : Activity() {

    private val uiHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var promptBox: EditText
    private lateinit var negativePromptBox: EditText
    private lateinit var seedBox: EditText
    private lateinit var statusText: TextView
    private lateinit var percentText: TextView
    private lateinit var timeText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var resultImage: ImageView
    private lateinit var referencePreview: ImageView
    private lateinit var referenceStatus: TextView
    private lateinit var modelStatusText: TextView

    private lateinit var pickModelBtn: Button
    private lateinit var pickImageBtn: Button
    private lateinit var clearReferenceBtn: Button
    private lateinit var generateBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var clearResultBtn: Button
    private lateinit var fastBtn: Button
    private lateinit var balancedBtn: Button
    private lateinit var qualityBtn: Button
    private lateinit var optimizeBtn: Button
    private lateinit var ultraBtn: Button

    private var generator: ImageGenerator? = null
    private var lastBitmap: Bitmap? = null
    private var referenceBitmap: Bitmap? = null
    private var selectedSteps = 20
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var isBusy = false

    private val modelRoot: File get() = File(filesDir, "sd_model")
    private val doneMarker: File get() = File(filesDir, "sd_model.complete")

    private val colorBg = Color.parseColor("#0F0F1A")
    private val colorCard = Color.parseColor("#1A1A2E")
    private val colorAccent = Color.parseColor("#6C5CE7")
    private val colorAccentLight = Color.parseColor("#A29BFE")
    private val colorSuccess = Color.parseColor("#00B894")
    private val colorWarning = Color.parseColor("#E17055")
    private val colorInfo = Color.parseColor("#0984E3")
    private val colorTextDim = Color.parseColor("#BBBBDD")
    private val colorHint = Color.parseColor("#8888AA")
    private val colorInputBg = Color.parseColor("#1E1E2F")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = colorCard
        buildUi()
        refreshStatus()
        requestBatteryOptimization()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 16) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(colorBg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(40))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(colorAccent, colorAccentLight)
            ).apply { cornerRadius = dp(24).toFloat() }
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        TextView(this).apply {
            text = "DARK JISAN"
            textSize = 30f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            header.addView(this)
        }
        TextView(this).apply {
            text = "Offline • Real • Powerful"
            textSize = 14f
            setTextColor(Color.parseColor("#E0DFFF"))
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            header.addView(this)
        }
        root.addView(header)

        // Model Card
        val modelCard = makeCard("1. Model Setup")
        pickModelBtn = makeBtn("Select Model Folder (sd15_bing)", colorAccent)
        pickModelBtn.setOnClickListener { if (!isBusy) pickModelFolder() }
        modelStatusText = TextView(this).apply {
            text = "No model selected"
            textSize = 13f
            setTextColor(colorTextDim)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        modelCard.addView(pickModelBtn)
        modelCard.addView(modelStatusText)
        root.addView(modelCard)

        // Prompt Card
        val promptCard = makeCard("2. Prompt & Settings")
        TextView(this).apply {
            text = "Prompt"
            textSize = 12f
            setTextColor(colorAccentLight)
            setPadding(0, 0, 0, dp(6))
            promptCard.addView(this)
        }
        promptBox = EditText(this).apply {
            hint = "Describe the image (English recommended)"
            minLines = 3
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(colorInputBg, 12)
            setTextColor(Color.WHITE)
            setHintTextColor(colorHint)
        }
        promptCard.addView(promptBox)

        TextView(this).apply {
            text = "Negative Prompt (optional)"
            textSize = 12f
            setTextColor(colorAccentLight)
            setPadding(0, dp(14), 0, dp(6))
            promptCard.addView(this)
        }
        negativePromptBox = EditText(this).apply {
            hint = "blurry, low quality, bad anatomy..."
            minLines = 2
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(colorInputBg, 12)
            setTextColor(Color.WHITE)
            setHintTextColor(colorHint)
        }
        promptCard.addView(negativePromptBox)

        TextView(this).apply {
            text = "Seed (empty = random)"
            textSize = 12f
            setTextColor(colorAccentLight)
            setPadding(0, dp(14), 0, dp(6))
            promptCard.addView(this)
        }
        seedBox = EditText(this).apply {
            hint = "Random"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(colorInputBg, 12)
            setTextColor(Color.WHITE)
            setHintTextColor(colorHint)
        }
        promptCard.addView(seedBox)

        TextView(this).apply {
            text = "Quality / Steps"
            textSize = 12f
            setTextColor(colorAccentLight)
            setPadding(0, dp(16), 0, dp(8))
            promptCard.addView(this)
        }
        val qRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fastBtn = makeSmallBtn("Fast\n12", colorSuccess)
        balancedBtn = makeSmallBtn("Balanced\n20", colorInfo)
        qualityBtn = makeSmallBtn("Quality\n28", colorAccent)
        optimizeBtn = makeSmallBtn("Optimize\n18", colorWarning)
        ultraBtn = makeSmallBtn("Ultra\n35", Color.parseColor("#FD79A8"))
        fastBtn.setOnClickListener { selectQuality(12, fastBtn) }
        balancedBtn.setOnClickListener { selectQuality(20, balancedBtn) }
        qualityBtn.setOnClickListener { selectQuality(28, qualityBtn) }
        optimizeBtn.setOnClickListener { selectQuality(18, optimizeBtn) }
        ultraBtn.setOnClickListener { selectQuality(35, ultraBtn) }
        qRow.addView(fastBtn); qRow.addView(balancedBtn); qRow.addView(qualityBtn)
        qRow.addView(optimizeBtn); qRow.addView(ultraBtn)
        promptCard.addView(qRow)
        root.addView(promptCard)

        // Reference Card
        val refCard = makeCard("3. Reference Image (Optional)")
        pickImageBtn = makeBtn("Upload Reference Image", Color.parseColor("#2D3436"))
        pickImageBtn.setOnClickListener { if (!isBusy) pickReferenceImage() }
        clearReferenceBtn = makeBtn("Clear Reference", colorWarning)
        clearReferenceBtn.visibility = View.GONE
        clearReferenceBtn.setOnClickListener {
            referenceBitmap = null
            referencePreview.setImageBitmap(null)
            referencePreview.visibility = View.GONE
            clearReferenceBtn.visibility = View.GONE
            referenceStatus.text = "No reference image"
        }
        referencePreview = ImageView(this).apply {
            adjustViewBounds = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(180)).apply { topMargin = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(colorInputBg, 12)
        }
        referenceStatus = TextView(this).apply {
            text = "No reference image"
            textSize = 13f
            setTextColor(colorTextDim)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        refCard.addView(pickImageBtn)
        refCard.addView(clearReferenceBtn)
        refCard.addView(referencePreview)
        refCard.addView(referenceStatus)
        root.addView(refCard)

        // Generate button
        generateBtn = makeBtn("Generate Image", colorAccent)
        generateBtn.textSize = 17f
        generateBtn.setPadding(dp(16), dp(18), dp(16), dp(18))
        generateBtn.setOnClickListener { startGenerate() }
        root.addView(generateBtn)

        // Progress Card
        val progCard = makeCard("Progress")
        percentText = TextView(this).apply {
            text = "0%"
            textSize = 36f
            setTextColor(colorAccentLight)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(14)).apply { topMargin = dp(10) }
        }
        statusText = TextView(this).apply {
            text = "Ready"
            textSize = 14f
            setTextColor(colorTextDim)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        timeText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(colorHint)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        progCard.addView(percentText)
        progCard.addView(progressBar)
        progCard.addView(statusText)
        progCard.addView(timeText)
        root.addView(progCard)

        // Result Card
        val resCard = makeCard("Result")
        resultImage = ImageView(this).apply {
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
            background = rounded(colorInputBg, 12)
        }
        saveBtn = makeBtn("Save to Gallery", colorSuccess)
        saveBtn.isEnabled = false
        saveBtn.alpha = 0.45f
        saveBtn.setOnClickListener { saveToGallery() }
        clearResultBtn = makeBtn("Clear Result", Color.parseColor("#636E72"))
        clearResultBtn.isEnabled = false
        clearResultBtn.alpha = 0.45f
        clearResultBtn.setOnClickListener {
            lastBitmap = null
            resultImage.setImageBitmap(null)
            saveBtn.isEnabled = false
            saveBtn.alpha = 0.45f
            clearResultBtn.isEnabled = false
            clearResultBtn.alpha = 0.45f
        }
        resCard.addView(resultImage)
        resCard.addView(saveBtn)
        resCard.addView(clearResultBtn)
        root.addView(resCard)

        scroll.addView(root)
        setContentView(scroll)
        selectQuality(20, balancedBtn)
    }

    private fun makeCard(title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(colorCard, 20)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }
        TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(colorAccentLight)
            typeface = Typeface.DEFAULT_BOLD
            card.addView(this)
        }
        return card
    }

    private fun makeBtn(text: String, color: Int) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        background = rounded(color, 14)
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun makeSmallBtn(text: String, color: Int) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        background = rounded(color, 12)
        isAllCaps = false
        textSize = 11f
        setPadding(dp(4), dp(10), dp(4), dp(10))
        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(5) }
    }

    private fun selectQuality(steps: Int, selected: Button) {
        selectedSteps = steps
        listOf(fastBtn, balancedBtn, qualityBtn, optimizeBtn, ultraBtn).forEach { it.alpha = 0.45f }
        selected.alpha = 1f
    }

    private fun requestBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (_: Exception) {}
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DarkJisan::Gen")
            wakeLock?.acquire(35 * 60 * 1000L)
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    private fun refreshStatus() {
        if (isModelReady()) {
            modelStatusText.text = "Model ready • Offline mode"
            modelStatusText.setTextColor(colorSuccess)
            statusText.text = "Ready to generate"
        } else {
            modelStatusText.text = "Please select model folder (sd15_bing)"
            modelStatusText.setTextColor(colorWarning)
            statusText.text = "Waiting for model..."
        }
    }

    private fun isModelReady() = doneMarker.exists() && resolveModelDir() != null

    private fun resolveModelDir(): File? {
        if (!modelRoot.isDirectory) return null
        val entries = modelRoot.listFiles() ?: return null
        if (entries.any { it.isFile }) return modelRoot
        val dirs = entries.filter { it.isDirectory }
        if (dirs.isEmpty()) return null
        return dirs.find {
            val n = it.name.lowercase(Locale.getDefault())
            n == "sd15_bing" || n.contains("sd15") || n.contains("bing")
        } ?: dirs[0]
    }

    private fun pickModelFolder() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 42)
    }

    private fun pickReferenceImage() {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 99)
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            42 -> data.data?.let { copyModel(it) }
            99 -> {
                try {
                    contentResolver.openInputStream(data.data!!)?.use {
                        referenceBitmap = BitmapFactory.decodeStream(it)
                        referencePreview.setImageBitmap(referenceBitmap)
                        referencePreview.visibility = View.VISIBLE
                        clearReferenceBtn.visibility = View.VISIBLE
                        referenceStatus.text = "Reference image loaded"
                        referenceStatus.setTextColor(colorSuccess)
                    }
                } catch (_: Exception) {
                    referenceStatus.text = "Failed to load image"
                }
            }
        }
    }

    private fun copyModel(treeUri: Uri) {
        isBusy = true
        setButtons(false)
        statusText.text = "Copying model..."
        worker.execute {
            try {
                val tree = DocumentFile.fromTreeUri(this, treeUri) ?: throw Exception("Folder not opened")
                doneMarker.delete()
                modelRoot.deleteRecursively()
                modelRoot.mkdirs()
                safeCloseGenerator()
                copyDoc(tree, modelRoot)
                doneMarker.writeText("ok")
                post {
                    statusText.text = "Model copied successfully!"
                    modelStatusText.text = "Model ready • Offline mode"
                    modelStatusText.setTextColor(colorSuccess)
                    Toast.makeText(this, "Model ready", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                post { statusText.text = "Copy failed: ${t.message}" }
            } finally {
                isBusy = false
                post { setButtons(true); refreshStatus() }
            }
        }
    }

    private fun copyDoc(src: DocumentFile, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles().forEach { child ->
                val name = (child.name ?: "file").replace("/", "_")
                copyDoc(child, File(dst, name))
            }
        } else {
            post { statusText.text = "Copying: ${src.name}" }
            contentResolver.openInputStream(src.uri)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun startGenerate() {
        if (isBusy) return
        val prompt = promptBox.text.toString().trim()
        if (prompt.isEmpty()) {
            statusText.text = "Please enter a prompt"
            return
        }
        if (!isModelReady()) {
            statusText.text = "Please select model folder first"
            return
        }

        isBusy = true
        setButtons(false)
        saveBtn.isEnabled = false
        saveBtn.alpha = 0.45f
        clearResultBtn.isEnabled = false
        clearResultBtn.alpha = 0.45f
        progressBar.progress = 0
        percentText.text = "0%"
        timeText.text = ""
        acquireWakeLock()

        val startTime = SystemClock.elapsedRealtime()
        val ticker = object : Runnable {
            override fun run() {
                if (!isBusy) return
                val elapsed = (SystemClock.elapsedRealtime() - startTime) / 1000f
                val estimated = selectedSteps * 5.2f
                val percent = ((elapsed / estimated) * 100).toInt().coerceIn(0, 94)
                progressBar.progress = percent
                percentText.text = "$percent%"
                statusText.text = "Generating... $percent%"
                timeText.text = "Elapsed: ${elapsed.toInt()}s"
                uiHandler.postDelayed(this, 650)
            }
        }
        uiHandler.post(ticker)

        worker.execute {
            try {
                val modelDir = resolveModelDir() ?: throw Exception("Model folder not found")
                if (generator == null) {
                    post { statusText.text = "Loading model into GPU...\n${modelDir.name}" }
                    val options = ImageGenerator.ImageGeneratorOptions.builder()
                        .setImageGeneratorModelDirectory(modelDir.absolutePath)
                        .build()
                    generator = ImageGenerator.createFromOptions(this@MainActivity, options)
                }

                val seed = parseSeed()
                val finalPrompt = buildPrompt(prompt)
                var result: ImageGeneratorResult

                if (referenceBitmap != null) {
                    try {
                        post { statusText.text = "Generating with reference..." }
                        val mpImage = BitmapImageBuilder(referenceBitmap).build()
                        result = generator!!.generate(
                            finalPrompt, mpImage,
                            ImageGenerator.ConditionOptions.ConditionType.EDGE,
                            selectedSteps, seed
                        )
                    } catch (e: Exception) {
                        post { statusText.text = "Reference not supported. Using text-only..." }
                        result = generator!!.generate(finalPrompt, selectedSteps, seed)
                    }
                } else {
                    result = generator!!.generate(finalPrompt, selectedSteps, seed)
                }

                val bitmap = BitmapExtractor.extract(result.generatedImage())
                lastBitmap = bitmap
                val totalSec = (SystemClock.elapsedRealtime() - startTime) / 1000

                post {
                    progressBar.progress = 100
                    percentText.text = "100%"
                    resultImage.setImageBitmap(bitmap)
                    saveBtn.isEnabled = true
                    saveBtn.alpha = 1f
                    clearResultBtn.isEnabled = true
                    clearResultBtn.alpha = 1f
                    statusText.text = "Done!"
                    timeText.text = "Took ${totalSec / 60}m ${totalSec % 60}s  •  Seed: $seed"
                    Toast.makeText(this@MainActivity, "Image ready", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                post {
                    statusText.text = "Error:\n${t.message}\n\nTry Fast mode or free RAM"
                    percentText.text = "0%"
                    progressBar.progress = 0
                    safeCloseGenerator()
                }
            } finally {
                isBusy = false
                releaseWakeLock()
                post { setButtons(true) }
            }
        }
    }

    private fun parseSeed(): Int {
        val t = seedBox.text.toString().trim()
        return if (t.isEmpty()) Random.nextInt(1, Int.MAX_VALUE)
        else try { t.toLong().toInt().coerceAtLeast(1) } catch (_: Exception) { Random.nextInt(1, Int.MAX_VALUE) }
    }

    private fun buildPrompt(positive: String): String {
        val neg = negativePromptBox.text.toString().trim()
        return if (neg.isEmpty()) positive else "$positive, $neg"
    }

    private fun safeCloseGenerator() {
        try { generator?.close() } catch (_: Exception) {}
        generator = null
    }

    private fun saveToGallery() {
        val bmp = lastBitmap ?: return
        try {
            val name = "DARK_JISAN_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DARK_JISAN")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            statusText.text = "Saved to Gallery → Pictures/DARK_JISAN"
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            statusText.text = "Save failed: ${t.message}"
        }
    }

    private fun setButtons(enabled: Boolean) {
        val a = if (enabled) 1f else 0.45f
        listOf(pickModelBtn, pickImageBtn, generateBtn, fastBtn, balancedBtn, qualityBtn, optimizeBtn, ultraBtn, clearReferenceBtn).forEach {
            it.isEnabled = enabled
            it.alpha = a
        }
    }

    private fun post(block: () -> Unit) = uiHandler.post { block() }

    override fun onDestroy() {
        super.onDestroy()
        safeCloseGenerator()
        releaseWakeLock()
        worker.shutdown()
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
