package com.UwU.students

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class TrackItem(val name: String, val isSelected: Boolean)

class PlaylistAdapter(private val onTrackClick: (Int) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.TrackVH>() {

    private var tracks: List<TrackItem> = emptyList()
    private var selectedIndex = 0

    fun setTracks(newList: List<TrackItem>) {
        tracks = newList
        notifyDataSetChanged()
    }

    fun setSelected(index: Int) {
        if (selectedIndex != index && index in tracks.indices) {
            notifyItemChanged(selectedIndex)
            selectedIndex = index
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackVH {
        val textView = TextView(parent.context)
        textView.layoutParams = AbsListView.LayoutParams(
            AbsListView.LayoutParams.MATCH_PARENT,
            120
        )
        textView.gravity = Gravity.CENTER_VERTICAL
        textView.setPadding(40, 0, 40, 0)
        textView.textSize = 16f
        textView.setTextColor(0xFFFFFFFF.toInt())
        textView.setBackgroundColor(0xFF111111.toInt())
        return TrackVH(textView)
    }

    override fun onBindViewHolder(holder: TrackVH, position: Int) {
        val track = tracks[position]
        holder.setup(track, position == selectedIndex, onTrackClick)
    }

    override fun getItemCount() = tracks.size

    class TrackVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView = itemView as TextView

        fun setup(track: TrackItem, selected: Boolean, onClick: (Int) -> Unit) {
            textView.text = track.name
            textView.setTextColor(if (selected) 0xFFFFFF00.toInt() else 0xFFFFFFFF.toInt())
            textView.setOnClickListener { onClick(adapterPosition) }
        }
    }
}

class MediaActivity : AppCompatActivity() {

    private lateinit var player: MediaPlayer
    private lateinit var seekBar: SeekBar
    private lateinit var playPauseButton: ImageButton
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var loopButton: ImageButton
    private lateinit var selectFileButton: Button
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var titleText: TextView
    private lateinit var artistText: TextView
    private lateinit var controlsLayout: LinearLayout
    private lateinit var progressLayout: LinearLayout
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var coverCard: MaterialCardView

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false
    private var userSeeking = false
    private var loopOn = false
    private var playlistName: String? = null
    private var playlistUri: Uri? = null
    private var playlistFiles: List<Uri> = emptyList()
    private var currentIndex = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        if (allowed) showFilePicker() else titleText.text = "не предоставлено разрешения"
    }

    private val folderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { loadFolder(it) }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { loadSingleFile(it) }
    }

    private val seekUpdater = object : Runnable {
        override fun run() {
            if (!userSeeking && playing && ::player.isInitialized && player.isPlaying) {
                val currentPos = player.currentPosition
                seekBar.progress = currentPos
                currentTimeText.text = msToTime(currentPos)
            }
            handler.postDelayed(this, 500)
        }
    }

    private val adapter by lazy {
        PlaylistAdapter { index -> playSong(index) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_media)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.layout_main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupViews()
        setupPlayer()
        setupSeekBar()
        setupButtons()
    }

    private fun setupViews() {
        seekBar = findViewById(R.id.seekBar)
        playPauseButton = findViewById(R.id.btn_play_pause)
        prevButton = findViewById(R.id.btn_prev)
        nextButton = findViewById(R.id.btn_next)
        loopButton = findViewById(R.id.btn_loop)
        selectFileButton = findViewById(R.id.btn_select_file)
        currentTimeText = findViewById(R.id.tv_current_time)
        totalTimeText = findViewById(R.id.tv_total_time)
        titleText = findViewById(R.id.tv_title)
        artistText = findViewById(R.id.tv_artist)
        controlsLayout = findViewById(R.id.controls_layout)
        progressLayout = findViewById(R.id.progress_layout)
        coverCard = findViewById(R.id.card_cover)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    private fun setupPlayer() {
        player = MediaPlayer().apply {
            setOnCompletionListener { songFinished() }
        }
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) currentTimeText.text = msToTime(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                player.seekTo(seekBar?.progress ?: 0)
            }
        })
    }

    private fun setupButtons() {
        selectFileButton.setOnClickListener { askForPermission() }
        playPauseButton.setOnClickListener { togglePlay() }
        prevButton.setOnClickListener { previousSong() }
        nextButton.setOnClickListener { nextSong() }
        loopButton.setOnClickListener { toggleLoop() }
    }

    private fun askForPermission() {
        val perm = Manifest.permission.READ_MEDIA_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            showFilePicker()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun showFilePicker() {
        AlertDialog.Builder(this)
            .setTitle("Выбрать музыку")
            .setItems(arrayOf("Один трек", "Плейлист")) { _, choice ->
                when (choice) {
                    0 -> fileLauncher.launch(arrayOf("audio/*"))
                    1 -> folderLauncher.launch(null)
                }
            }
            .setNegativeButton("отмена", null)
            .show()
    }

    private fun loadSingleFile(uri: Uri) {
        try {
            val type = contentResolver.getType(uri) ?: return
            if (!type.startsWith("audio/")) {
                titleText.text = "файл не аудио"
                return
            }

            playlistFiles = listOf(uri)
            playlistUri = null
            currentIndex = 0

            val name = getFileTitle(uri) ?: "Трек"
            playlistName = name
            supportActionBar?.title = name

            adapter.setTracks(listOf(TrackItem(name, true)))

            playSong(0)
            showControls()

        } catch (e: Exception) {
            e.printStackTrace()
            titleText.text = "ошибка"
        }
    }

    private fun loadFolder(folderUri: Uri) {
        try {
            val audioList = getAudioFromFolder(folderUri)
            if (audioList.isEmpty()) {
                supportActionBar?.title = "нет треков"
                titleText.text = "пустая папка"
                return
            }

            playlistUri = folderUri
            playlistFiles = audioList
            currentIndex = 0

            if (playlistUri != null) {
                playlistName = getFolderTitle(folderUri)?.split("/")[1] ?: "Плейлист"
                supportActionBar?.title = playlistName
            }
            adapter.setTracks(
                audioList.mapIndexed { i, uri ->
                    TrackItem(getFileTitle(uri) ?: "Трек ${i + 1}", i == 0)
                }
            )

            playSong(0)
            showControls()

        } catch (e: Exception) {
            e.printStackTrace()
            supportActionBar?.title = "ошибка"
        }
    }

    private fun playSong(index: Int) {
        if (index !in playlistFiles.indices) return
        currentIndex = index
        loadSong(playlistFiles[index])
        adapter.setSelected(index)
    }

    private fun loadSong(uri: Uri) {
        try {
            if (player.isPlaying) player.stop()
            player.reset()

            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                player.setDataSource(fd.fileDescriptor)
                player.prepare()
                val info = getSongInfo(uri)
                updateSongInfo(info)
                startPlaying()
            } ?: run {
                titleText.text = "не открывается файл"
                artistText.text = ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            titleText.text = "ошибка"
            artistText.text = e.message
        }
    }

    private fun getSongInfo(uri: Uri): SongInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                retriever.setDataSource(fd.fileDescriptor)
            } ?: run {
                return SongInfo(
                    title = getFileTitle(uri) ?: "без названия",
                    artist = "",
                    album = "",
                    length = 0L
                )
            }

            SongInfo(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: getFileTitle(uri) ?: "Nameless",
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "Неизвестен",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                length = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            )
        } catch (e: Exception) {
            SongInfo(
                title = getFileTitle(uri) ?: "аудио",
                artist = "",
                album = "",
                length = 0L
            )
        } finally {
            retriever.release()
        }
    }

    private fun updateSongInfo(info: SongInfo) {
        titleText.text = info.title
        artistText.text = info.artist
        if (playlistFiles.size == 1) {
            playlistName = info.title
            supportActionBar?.title = info.title
        }
        seekBar.max = info.length.toInt()
        totalTimeText.text = msToTime(info.length.toInt())
        currentTimeText.text = "0:00"
        seekBar.progress = 0

        val uri = playlistFiles[currentIndex]
        contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            MediaMetadataRetriever().apply {
                setDataSource(fd.fileDescriptor)
                val picture = embeddedPicture

                findViewById<ImageView>(R.id.iv_cover).apply {
                    if (picture != null) {
                        val bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.size)
                        setImageBitmap(bitmap)
                        coverCard.isVisible = true
                    } else {
                        setImageResource(R.drawable.ic_music_note)
                        coverCard.isVisible = true
                    }
                }
                release()
            }
        } ?: run {
            findViewById<ImageView>(R.id.iv_cover).setImageResource(R.drawable.ic_music_note)
            coverCard.isVisible = true
        }
    }

    private fun showControls() {
        controlsLayout.isVisible = true
        progressLayout.isVisible = true
        coverCard.isVisible = true
        loopButton.isVisible = true
    }

    private fun togglePlay() = if (playing) pauseSong() else startPlaying()

    private fun startPlaying() {
        if (::player.isInitialized && !player.isPlaying) {
            player.start()
            playing = true
            playPauseButton.setImageResource(R.drawable.ic_pause)
            startSeekUpdate()
        }
    }

    private fun pauseSong() {
        if (::player.isInitialized && player.isPlaying) {
            player.pause()
            playing = false
            playPauseButton.setImageResource(R.drawable.ic_play_arrow)
            stopSeekUpdate()
        }
    }

    private fun songFinished() {
        playing = false
        playPauseButton.setImageResource(R.drawable.ic_play_arrow)
        seekBar.progress = seekBar.max
        stopSeekUpdate()

        if (playlistFiles.isEmpty()) return

        if (loopOn) {
            playSong(currentIndex)
        } else if (playlistFiles.size > 1) {
            val next = (currentIndex + 1) % playlistFiles.size
            playSong(next)
        }
    }

    private fun startSeekUpdate() {
        handler.removeCallbacks(seekUpdater)
        handler.postDelayed(seekUpdater, 1000)
    }

    private fun stopSeekUpdate() = handler.removeCallbacks(seekUpdater)

    private fun msToTime(ms: Int): String {
        val seconds = ms / 1000
        return String.format("%d:%02d", seconds / 60, seconds % 60)
    }

    override fun onPause() {
        super.onPause()
        stopSeekUpdate()
        if (playing) player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekUpdate()
        if (::player.isInitialized) player.release()
    }

    private fun getAudioFromFolder(folderUri: Uri): List<Uri> {
        val files = mutableListOf<Uri>()
        try {
            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeId)

            contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                    if (isAudio(docUri)) {
                        files.add(docUri)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return files
    }

    private fun isAudio(uri: Uri): Boolean {
        val mime = contentResolver.getType(uri) ?: return false
        return mime.startsWith("audio/") || mime.endsWith("mp3") || mime.endsWith("m4a")
    }

    private fun getFileTitle(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex("_display_name")
                        if (idx != -1) it.getString(idx) else null
                    } else null
                }
            }
            else -> uri.lastPathSegment?.split("/")?.lastOrNull()
        }
    }

    private fun getFolderTitle(uri: Uri): String? {
        return try {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            Uri.decode(documentId.split(":").lastOrNull())
        } catch (e: Exception) {
            "Плейлист"
        }
    }

    private fun previousSong() {
        if (playlistFiles.isEmpty()) return
        val prev = if (currentIndex > 0) currentIndex - 1 else playlistFiles.size - 1
        playSong(prev)
    }

    private fun nextSong() {
        if (playlistFiles.isEmpty()) return
        val next = (currentIndex + 1) % playlistFiles.size
        playSong(next)
    }

    private fun toggleLoop() {
        loopOn = !loopOn
        loopButton.setImageResource(
            if (loopOn) R.drawable.ic_repeat_one
            else R.drawable.ic_repeat
        )
        loopButton.alpha = if (loopOn) 1f else 0.5f
    }

    private fun showPlaylist() {
        if (playlistFiles.isEmpty()) return

        val items = playlistFiles.mapIndexed { i, uri ->
            val info = getSongInfo(uri)
            val name = when {
                !info.title.isNullOrBlank() -> info.title
                else -> getFileTitle(uri) ?: "Трек ${i+1}"
            }
            if (i == currentIndex) "$name" else "     $name"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${playlistName ?: "Плейлист"} (${items.size})")
            .setItems(items) { _, which -> playSong(which) }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_media, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_playlist -> {
                showPlaylist()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    data class SongInfo(
        val title: String,
        val artist: String,
        val album: String,
        val length: Long
    )
}