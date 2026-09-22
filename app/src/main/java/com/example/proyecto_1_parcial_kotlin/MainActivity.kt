package com.example.proyecto_1_parcial_kotlin

import android.Manifest
import android.animation.ValueAnimator
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.*
import android.widget.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // --- VARIABLES DE GALERÍA ---
    private val imageUris = ArrayList<Uri>()
    private var currentIndex = 0
    private var showFavoritesOnly = false
    private var favoriteUris = ArrayList<Uri>()

    // Referencias a UI de Galería
    private lateinit var galleryLayout: RelativeLayout
    private lateinit var imageView: ImageView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var iconFavorite: ImageView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gridFavorites: GridView
    private lateinit var singleImageContainer: RelativeLayout

    // --- VARIABLES DE EDITOR ---
    private lateinit var editorLayout: RelativeLayout
    private lateinit var editorContainer: FrameLayout
    private var editorView: PhotoEditorView? = null

    // Solicitud de permisos
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            loadImages()
        } else {
            Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para petición de borrado en Android 11+
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Imagen eliminada", Toast.LENGTH_SHORT).show()
            loadImages()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicialización de layouts principales
        galleryLayout = findViewById(R.id.galleryLayout)
        editorLayout = findViewById(R.id.editorLayout)

        // Mapeo de vistas de galería
        imageView = findViewById(R.id.imageView)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        iconFavorite = findViewById(R.id.iconFavoriteAction)
        gridFavorites = findViewById(R.id.gridFavorites)
        singleImageContainer = findViewById(R.id.singleImageContainer)
        sharedPreferences = getSharedPreferences("GalleryPrefs", MODE_PRIVATE)

        // Eventos de botones de navegación (flechas flotantes)
        btnPrev.setOnClickListener { showPreviousImage() }
        btnNext.setOnClickListener { showNextImage() }

        // Configuración de Gestos (Swipe) en la imagen
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        if (diffX > 0) showPreviousImage() else showNextImage()
                        return true
                    }
                }
                return false
            }
        })
        
        imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Acciones principales de la galería
        findViewById<LinearLayout>(R.id.btnEdit).setOnClickListener {
            if (imageUris.isNotEmpty()) openEditor()
        }

        findViewById<LinearLayout>(R.id.btnFavoriteAction).setOnClickListener {
            if (imageUris.isNotEmpty()) toggleFavorite(imageUris[currentIndex])
        }

        findViewById<LinearLayout>(R.id.btnDelete).setOnClickListener {
            if (imageUris.isNotEmpty()) showDeleteDialog()
        }

        // Abrir visor de imagen desde la cuadrícula (Grid)
        gridFavorites.setOnItemClickListener { _, _, position, _ ->
            gridFavorites.visibility = View.GONE
            singleImageContainer.visibility = View.VISIBLE
            showFavoritesOnly = true
            updateTabUI(isFav = true)
            loadImages(position)
        }

        // Pestaña "Todas las fotos"
        findViewById<View>(R.id.btnTabAll).setOnClickListener {
            showFavoritesOnly = false
            updateTabUI(isFav = false)
            gridFavorites.visibility = View.GONE
            singleImageContainer.visibility = View.VISIBLE
            loadImages()
        }

        // Pestaña "Fotos Favoritas"
        findViewById<View>(R.id.btnTabFavs).setOnClickListener {
            showFavoritesOnly = true
            updateTabUI(isFav = true)
            gridFavorites.visibility = View.VISIBLE
            singleImageContainer.visibility = View.GONE
            loadFavoritesIntoGrid()
        }

        // Preparación del editor
        editorContainer = findViewById(R.id.editorContainer)
        setupEditorControls()

        checkPermissions()
    }

    // Actualiza estilos del Switch (Todas/Favoritos)
    private fun updateTabUI(isFav: Boolean) {
        val cardTabAll = findViewById<LinearLayout>(R.id.btnTabAll).getChildAt(0) as CardView
        val iconTabAll = cardTabAll.getChildAt(0) as ImageView
        val textTabAll = findViewById<LinearLayout>(R.id.btnTabAll).getChildAt(1) as TextView

        val cardTabFavs = findViewById<LinearLayout>(R.id.btnTabFavs).getChildAt(0) as CardView
        val iconTabFavs = cardTabFavs.getChildAt(0) as ImageView
        val textTabFavs = findViewById<LinearLayout>(R.id.btnTabFavs).getChildAt(1) as TextView

        if (isFav) {
            cardTabFavs.setCardBackgroundColor(Color.parseColor("#2D4A45"))
            iconTabFavs.imageTintList = ColorStateList.valueOf(Color.parseColor("#D0E8E1"))
            textTabFavs.setTextColor(Color.WHITE)

            cardTabAll.setCardBackgroundColor(Color.TRANSPARENT)
            iconTabAll.imageTintList = ColorStateList.valueOf(Color.parseColor("#888888"))
            textTabAll.setTextColor(Color.parseColor("#888888"))
        } else {
            cardTabAll.setCardBackgroundColor(Color.parseColor("#2D4A45"))
            iconTabAll.imageTintList = ColorStateList.valueOf(Color.parseColor("#D0E8E1"))
            textTabAll.setTextColor(Color.WHITE)

            cardTabFavs.setCardBackgroundColor(Color.TRANSPARENT)
            iconTabFavs.imageTintList = ColorStateList.valueOf(Color.parseColor("#888888"))
            textTabFavs.setTextColor(Color.parseColor("#888888"))
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        if (gridFavorites.visibility == View.VISIBLE) {
            loadFavoritesIntoGrid()
        }
    }

    // --- LÓGICA DE GALERÍA ---

    // Comprobar y solicitar permisos de almacenamiento
    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            loadImages()
        } else {
            permissionRequest.launch(permissions)
        }
    }

    // Cargar URIs de fotos favoritas en la cuadrícula
    private fun loadFavoritesIntoGrid() {
        favoriteUris.clear()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val favorites = sharedPreferences.getStringSet("favorites", emptySet()) ?: emptySet()
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                if (favorites.contains(uri.toString())) {
                    favoriteUris.add(uri)
                }
            }
        }
        
        // Adapter para renderizar miniaturas
        gridFavorites.adapter = object : BaseAdapter() {
            override fun getCount(): Int = favoriteUris.size
            override fun getItem(position: Int): Any = favoriteUris[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val imgView = (convertView as? ImageView) ?: ImageView(this@MainActivity).apply {
                    layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                
                val uri = favoriteUris[position]
                imgView.setImageDrawable(null)
                Thread {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            contentResolver.loadThumbnail(uri, Size(300, 300), null)
                        } catch (e: Exception) { null }
                    } else null
                    
                    runOnUiThread {
                        if (bitmap != null) imgView.setImageBitmap(bitmap)
                        else imgView.setImageURI(uri)
                    }
                }.start()
                return imgView
            }
        }
    }

    // Cargar fotos al visor principal
    private fun loadImages(targetIndex: Int = 0) {
        imageUris.clear()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val favorites = sharedPreferences.getStringSet("favorites", emptySet()) ?: emptySet()
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                
                if (showFavoritesOnly) {
                    if (favorites.contains(uri.toString())) imageUris.add(uri)
                } else {
                    imageUris.add(uri)
                }
            }
        }

        if (imageUris.isNotEmpty()) {
            currentIndex = if (targetIndex < imageUris.size) targetIndex else 0
            updateImage()
        } else {
            imageView.setImageDrawable(null)
            iconFavorite.setImageResource(R.drawable.ic_heart_empty)
            iconFavorite.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun showPreviousImage() {
        if (imageUris.isNotEmpty()) {
            currentIndex = if (currentIndex > 0) currentIndex - 1 else imageUris.size - 1
            updateImage()
        }
    }

    private fun showNextImage() {
        if (imageUris.isNotEmpty()) {
            currentIndex = if (currentIndex < imageUris.size - 1) currentIndex + 1 else 0
            updateImage()
        }
    }

    // Refresca la foto activa en el visor y el ícono de corazón
    private fun updateImage() {
        if (imageUris.isEmpty()) {
            imageView.setImageDrawable(null)
            iconFavorite.setImageResource(R.drawable.ic_heart_empty)
            iconFavorite.imageTintList = ColorStateList.valueOf(Color.WHITE)
            return
        }

        val currentUri = imageUris[currentIndex]
        imageView.setImageURI(currentUri)

        val favorites = sharedPreferences.getStringSet("favorites", mutableSetOf()) ?: mutableSetOf()
        if (favorites.contains(currentUri.toString())) {
            iconFavorite.setImageResource(R.drawable.ic_heart_filled)
            iconFavorite.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF5252"))
        } else {
            iconFavorite.setImageResource(R.drawable.ic_heart_empty)
            iconFavorite.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    // Guardar o quitar de favoritos (SharedPreferences)
    private fun toggleFavorite(uri: Uri) {
        val favorites = sharedPreferences.getStringSet("favorites", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        val uriStr = uri.toString()

        if (favorites.contains(uriStr)) favorites.remove(uriStr) else favorites.add(uriStr)
        sharedPreferences.edit().putStringSet("favorites", favorites).apply()
        
        updateImage()
        if (gridFavorites.visibility == View.VISIBLE) loadFavoritesIntoGrid()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Foto")
            .setMessage("¿Estás seguro de que quieres eliminar esta foto?")
            .setPositiveButton("Eliminar") { _, _ -> deleteCurrentPhoto() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Proceso seguro de borrado con MediaStore API (Compatible con Android 11+)
    private fun deleteCurrentPhoto() {
        val uri = imageUris[currentIndex]
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intentSender = MediaStore.createDeleteRequest(contentResolver, listOf(uri)).intentSender
                deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } else {
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, "Imagen eliminada", Toast.LENGTH_SHORT).show()
                loadImages()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val recoverableSecurityException = e as? RecoverableSecurityException
                recoverableSecurityException?.userAction?.actionIntent?.intentSender?.let { intentSender ->
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al eliminar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LÓGICA DEL EDITOR ---

    // Ocultar Galería y construir interfaz de Editor
    private fun openEditor() {
        try {
            val uri = imageUris[currentIndex]
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                editorContainer.removeAllViews()
                editorView = PhotoEditorView(this, bitmap)
                editorView?.id = View.generateViewId()
                editorContainer.addView(editorView)

                galleryLayout.visibility = View.GONE
                editorLayout.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "No se pudo cargar la imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir el editor", Toast.LENGTH_SHORT).show()
        }
    }

    // Cerrar Editor y volver a la Galería
    private fun closeEditor() {
        galleryLayout.visibility = View.VISIBLE
        editorLayout.visibility = View.GONE
        editorContainer.removeAllViews()
        editorView = null
    }

    // Inicializar botones, herramientas y barra de grosores del editor
    private fun setupEditorControls() {
        val btnModeDraw = findViewById<Button>(R.id.btnModeDraw)
        val btnModeTransform = findViewById<Button>(R.id.btnModeTransform)

        btnModeDraw.setOnClickListener {
            editorView?.setMode(PhotoEditorView.Mode.DRAW)
            btnModeDraw.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
            btnModeTransform.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
            findViewById<View>(R.id.colorPalette).visibility = View.VISIBLE
            findViewById<View>(R.id.trazoLayout).visibility = View.VISIBLE
        }

        btnModeTransform.setOnClickListener {
            editorView?.setMode(PhotoEditorView.Mode.CROP)
            btnModeTransform.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
            btnModeDraw.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#222222"))
            findViewById<View>(R.id.colorPalette).visibility = View.GONE
            findViewById<View>(R.id.trazoLayout).visibility = View.GONE
        }

        val colors = listOf(
            findViewById<View>(R.id.colorRed) to Color.parseColor("#F44336"),
            findViewById<View>(R.id.colorBlue) to Color.parseColor("#2196F3"),
            findViewById<View>(R.id.colorGreen) to Color.parseColor("#4CAF50"),
            findViewById<View>(R.id.colorYellow) to Color.parseColor("#FFEB3B"),
            findViewById<View>(R.id.colorWhite) to Color.WHITE
        )

        fun selectColor(selectedView: View, color: Int) {
            editorView?.setDrawColor(color)
            colors.forEach { (view, _) -> 
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
            findViewById<View>(R.id.btnEraser).scaleX = 1.0f
            findViewById<View>(R.id.btnEraser).scaleY = 1.0f
            selectedView.scaleX = 1.3f
            selectedView.scaleY = 1.3f
        }

        colors.forEach { (view, color) -> view.setOnClickListener { selectColor(view, color) } }

        findViewById<View>(R.id.btnEraser).setOnClickListener { 
            editorView?.setEraserMode() 
            colors.forEach { (view, _) -> 
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
            it.scaleX = 1.1f
            it.scaleY = 1.1f
        }

        // Slider para tamaño de trazo
        val sliderTrazo = findViewById<SeekBar>(R.id.sliderTrazo)
        sliderTrazo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val stroke = 5f + (progress / 100f) * 95f
                editorView?.setStrokeWidth(stroke)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        selectColor(colors[0].first, colors[0].second)

        // Funciones de acción inferior
        findViewById<View>(R.id.btnUndo).setOnClickListener { editorView?.undo() }
        findViewById<View>(R.id.btnCancel).setOnClickListener { closeEditor() }
        findViewById<View>(R.id.btnSave).setOnClickListener { saveEditedImage() }
    }

    // Guarda imagen resultante creando un archivo nuevo en MediaStore
    private fun saveEditedImage() {
        val view = editorView ?: return
        
        view.isCapturing = true
        val fullBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        canvas.drawColor(Color.BLACK)
        view.draw(canvas)
        view.isCapturing = false

        val rect = view.cropRect
        val cLeft = Math.max(0, rect.left.toInt())
        val cTop = Math.max(0, rect.top.toInt())
        val cRight = Math.min(fullBitmap.width, rect.right.toInt())
        val cBottom = Math.min(fullBitmap.height, rect.bottom.toInt())
        
        val cropW = cRight - cLeft
        val cropH = cBottom - cTop
        
        val finalBitmap = if (cropW > 0 && cropH > 0) {
            Bitmap.createBitmap(fullBitmap, cLeft, cTop, cropW, cropH)
        } else {
            fullBitmap
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Editada_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val itemUri = contentResolver.insert(collection, values)
            if (itemUri != null) {
                contentResolver.openOutputStream(itemUri)?.use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(itemUri, values, null, null)
                }
                Toast.makeText(this, "Guardado como nueva imagen", Toast.LENGTH_SHORT).show()
                closeEditor()
                loadImages()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
        }
    }

    // --- CUSTOM VIEW PARA DIBUJO Y RECORTE ---
    // Clase interna que dibuja el lienzo, los trazos de pincel y el recuadro de recorte
    class PhotoEditorView(context: Context, private val originalBitmap: Bitmap) : View(context) {

        enum class Mode { DRAW, CROP }
        private var currentMode = Mode.DRAW
        var isCapturing = false

        var cropRect = RectF()
        private var imageBounds = RectF()
        private var resizeCorner = -1

        // Zoom Animador
        private var zoomAnimator: ValueAnimator? = null
        private val autoZoomRunnable = Runnable {
            val inverse = Matrix()
            transformMatrix.invert(inverse)
            val cropImageSpace = RectF()
            inverse.mapRect(cropImageSpace, cropRect)

            val padding = 50f
            val targetScreenRect = RectF(padding, padding, width - padding, height - padding)
            val targetMatrix = Matrix()
            targetMatrix.setRectToRect(cropImageSpace, targetScreenRect, Matrix.ScaleToFit.CENTER)

            val targetBounds = RectF()
            val fullImageRect = RectF(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
            targetMatrix.mapRect(targetBounds, fullImageRect)

            val targetCrop = RectF()
            targetMatrix.mapRect(targetCrop, cropImageSpace)

            val startMatrix = Matrix(transformMatrix)
            val startCrop = RectF(cropRect)
            val startBounds = RectF(imageBounds)

            val startValues = FloatArray(9)
            val endValues = FloatArray(9)
            startMatrix.getValues(startValues)
            targetMatrix.getValues(endValues)

            zoomAnimator?.cancel()
            zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                addUpdateListener { anim ->
                    val f = anim.animatedFraction
                    val currentValues = FloatArray(9)
                    for (i in 0..8) currentValues[i] = startValues[i] + (endValues[i] - startValues[i]) * f
                    
                    transformMatrix.setValues(currentValues)
                    cropRect.left = startCrop.left + (targetCrop.left - startCrop.left) * f
                    cropRect.top = startCrop.top + (targetCrop.top - startCrop.top) * f
                    cropRect.right = startCrop.right + (targetCrop.right - startCrop.right) * f
                    cropRect.bottom = startCrop.bottom + (targetCrop.bottom - startCrop.bottom) * f
                    
                    imageBounds.left = startBounds.left + (targetBounds.left - startBounds.left) * f
                    imageBounds.top = startBounds.top + (targetBounds.top - startBounds.top) * f
                    imageBounds.right = startBounds.right + (targetBounds.right - startBounds.right) * f
                    imageBounds.bottom = startBounds.bottom + (targetBounds.bottom - startBounds.bottom) * f
                    
                    invalidate()
                }
                start()
            }
        }

        private val overlayPaint = Paint().apply { color = Color.parseColor("#99000000") }
        private val gridPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val transformMatrix = Matrix()
        private var lastTouchX = 0f
        private var lastTouchY = 0f

        class DrawOp(val path: Path, val color: Int, val isEraser: Boolean, val strokeWidth: Float)
        private val paths = mutableListOf<DrawOp>()
        
        private var currentPath: Path? = null
        private var currentColor = Color.parseColor("#F44336")
        private var isEraser = false
        private var currentStrokeWidth = 24f

        private val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        fun setStrokeWidth(width: Float) { currentStrokeWidth = width }

        fun setMode(mode: Mode) {
            currentMode = mode
            if (mode != Mode.CROP) {
                removeCallbacks(autoZoomRunnable)
                zoomAnimator?.cancel()
            }
            invalidate()
        }

        fun setDrawColor(color: Int) {
            currentColor = color
            isEraser = false
        }

        fun setEraserMode() { isEraser = true }

        fun undo() {
            if (currentMode == Mode.DRAW) {
                if (paths.isNotEmpty()) {
                    paths.removeAt(paths.size - 1)
                    invalidate()
                } else Toast.makeText(context, "No hay trazos", Toast.LENGTH_SHORT).show()
            } else if (currentMode == Mode.CROP) {
                removeCallbacks(autoZoomRunnable)
                zoomAnimator?.cancel()
                
                val w = width
                val h = height
                if (w > 0 && h > 0) {
                    val left = (w - originalBitmap.width) / 2f
                    val top = (h - originalBitmap.height) / 2f
                    transformMatrix.setTranslate(left, top)
                    
                    val scaleX = w.toFloat() / originalBitmap.width
                    val scaleY = h.toFloat() / originalBitmap.height
                    val scale = Math.min(scaleX, scaleY) * 0.9f
                    transformMatrix.postScale(scale, scale, w / 2f, h / 2f)

                    val finalW = originalBitmap.width * scale
                    val finalH = originalBitmap.height * scale
                    val finalLeft = (w - finalW) / 2f
                    val finalTop = (h - finalH) / 2f
                    
                    imageBounds.set(finalLeft, finalTop, finalLeft + finalW, finalTop + finalH)
                    cropRect.set(imageBounds)
                }
                invalidate()
                Toast.makeText(context, "Recorte restaurado", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val fullImageRect = RectF(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat())
            val screenRect = RectF(50f, 50f, w - 50f, h - 50f)
            transformMatrix.setRectToRect(fullImageRect, screenRect, Matrix.ScaleToFit.CENTER)
            transformMatrix.mapRect(imageBounds, fullImageRect)
            cropRect.set(imageBounds)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.save()
            canvas.concat(transformMatrix)
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)

            // Guardar capa para aislar los trazos y que el borrador actúe solo ahí
            canvas.saveLayer(0f, 0f, originalBitmap.width.toFloat(), originalBitmap.height.toFloat(), null)

            for (op in paths) {
                paint.color = op.color
                paint.strokeWidth = op.strokeWidth
                paint.xfermode = if (op.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                canvas.drawPath(op.path, paint)
            }

            currentPath?.let {
                paint.color = currentColor
                paint.strokeWidth = currentStrokeWidth
                paint.xfermode = if (isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                canvas.drawPath(it, paint)
            }

            canvas.restore()
            canvas.restore()

            // Dibujar grid de recorte
            if (currentMode == Mode.CROP && !isCapturing) {
                val w = width.toFloat()
                val h = height.toFloat()
                canvas.drawRect(0f, 0f, w, cropRect.top, overlayPaint)
                canvas.drawRect(0f, cropRect.bottom, w, h, overlayPaint)
                canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint)
                canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, overlayPaint)
                
                canvas.drawRect(cropRect, gridPaint)
                val cellW = cropRect.width() / 3
                val cellH = cropRect.height() / 3
                canvas.drawLine(cropRect.left + cellW, cropRect.top, cropRect.left + cellW, cropRect.bottom, gridPaint)
                canvas.drawLine(cropRect.left + cellW * 2, cropRect.top, cropRect.left + cellW * 2, cropRect.bottom, gridPaint)
                canvas.drawLine(cropRect.left, cropRect.top + cellH, cropRect.right, cropRect.top + cellH, gridPaint)
                canvas.drawLine(cropRect.left, cropRect.top + cellH * 2, cropRect.right, cropRect.top + cellH * 2, gridPaint)
                
                canvas.drawCircle(cropRect.left, cropRect.top, 20f, gridPaint)
                canvas.drawCircle(cropRect.right, cropRect.top, 20f, gridPaint)
                canvas.drawCircle(cropRect.left, cropRect.bottom, 20f, gridPaint)
                canvas.drawCircle(cropRect.right, cropRect.bottom, 20f, gridPaint)
            }
        }

        // Lógica de manipulación de toque (Dibujo o Modificación de Recorte)
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (currentMode == Mode.CROP) {
                val x = event.x
                val y = event.y
                val touchRadius = 80f
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        removeCallbacks(autoZoomRunnable)
                        zoomAnimator?.cancel()
                        resizeCorner = -1
                        if (Math.hypot((x - cropRect.left).toDouble(), (y - cropRect.top).toDouble()) < touchRadius) resizeCorner = 0
                        else if (Math.hypot((x - cropRect.right).toDouble(), (y - cropRect.top).toDouble()) < touchRadius) resizeCorner = 1
                        else if (Math.hypot((x - cropRect.right).toDouble(), (y - cropRect.bottom).toDouble()) < touchRadius) resizeCorner = 2
                        else if (Math.hypot((x - cropRect.left).toDouble(), (y - cropRect.bottom).toDouble()) < touchRadius) resizeCorner = 3
                        else if (cropRect.contains(x, y)) resizeCorner = 4
                        
                        lastTouchX = x
                        lastTouchY = y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        
                        when (resizeCorner) {
                            0 -> { cropRect.left += dx; cropRect.top += dy }
                            1 -> { cropRect.right += dx; cropRect.top += dy }
                            2 -> { cropRect.right += dx; cropRect.bottom += dy }
                            3 -> { cropRect.left += dx; cropRect.bottom += dy }
                            4 -> { cropRect.offset(dx, dy) }
                        }
                        
                        if (cropRect.width() < 100f) {
                            if (resizeCorner == 0 || resizeCorner == 3) cropRect.left = cropRect.right - 100f
                            else if (resizeCorner == 1 || resizeCorner == 2) cropRect.right = cropRect.left + 100f
                        }
                        if (cropRect.height() < 100f) {
                            if (resizeCorner == 0 || resizeCorner == 1) cropRect.top = cropRect.bottom - 100f
                            else if (resizeCorner == 2 || resizeCorner == 3) cropRect.bottom = cropRect.top + 100f
                        }

                        // Asegurar límites del cuadro al rectángulo original
                        if (cropRect.left < imageBounds.left) cropRect.left = imageBounds.left
                        if (cropRect.top < imageBounds.top) cropRect.top = imageBounds.top
                        if (cropRect.right > imageBounds.right) cropRect.right = imageBounds.right
                        if (cropRect.bottom > imageBounds.bottom) cropRect.bottom = imageBounds.bottom

                        if (resizeCorner == 4) {
                            if (cropRect.left < imageBounds.left) cropRect.offset(imageBounds.left - cropRect.left, 0f)
                            if (cropRect.right > imageBounds.right) cropRect.offset(imageBounds.right - cropRect.right, 0f)
                            if (cropRect.top < imageBounds.top) cropRect.offset(0f, imageBounds.top - cropRect.top)
                            if (cropRect.bottom > imageBounds.bottom) cropRect.offset(0f, imageBounds.bottom - cropRect.bottom)
                        }
                        
                        lastTouchX = x
                        lastTouchY = y
                        invalidate()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        postDelayed(autoZoomRunnable, 2000)
                    }
                }
                return true
            }

            if (currentMode == Mode.DRAW) {
                val inverse = Matrix()
                transformMatrix.invert(inverse)
                val pts = floatArrayOf(event.x, event.y)
                inverse.mapPoints(pts)
                val x = pts[0]
                val y = pts[1]

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        currentPath = Path()
                        currentPath?.moveTo(x, y)
                        lastTouchX = x
                        lastTouchY = y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (currentPath != null) {
                            currentPath?.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2)
                            lastTouchX = x
                            lastTouchY = y
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (currentPath != null) {
                            currentPath?.lineTo(x, y)
                            paths.add(DrawOp(currentPath!!, currentColor, isEraser, currentStrokeWidth))
                            currentPath = null
                            invalidate()
                        }
                    }
                }
            }
            return true
        }
    }
}