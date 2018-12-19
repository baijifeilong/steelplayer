import com.sun.javafx.scene.control.skin.TableViewSkin
import com.sun.javafx.scene.control.skin.VirtualFlow
import javafx.application.Application
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.ObservableList
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.Text
import javafx.scene.text.TextAlignment
import javafx.scene.text.TextFlow
import javafx.stage.WindowEvent
import javafx.util.Duration
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.Glyph
import tornadofx.*
import uk.co.caprica.vlcj.component.AudioMediaPlayerComponent
import uk.co.caprica.vlcj.player.MediaPlayer
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter
import java.io.File
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.streams.toList

fun parseLyric(text: String): TreeMap<Int, String> {
    val lyricMap = TreeMap<Int, String>()
    val lyricRegex = Pattern.compile("((\\[\\d+:\\d+\\.\\d+])+)([^\\[\\]]+)")
    for (line in text.split(Pattern.compile("[\r\n]+"))) {
        println("[Lyric] $line")
        val matcher = lyricRegex.matcher(line)
        if (!matcher.matches()) continue
        val timePart = matcher.group(1)
        val lyricPart = matcher.group(3).trim()
        if (lyricPart.isEmpty()) continue
        for (i in 0 until timePart.length step 10) {
            val timeString = timePart.substring(i + 1, i + 9)
            val parts = timeString.split(":", ".").map { it.toInt() }
            val millis = parts[0] * 60 * 1000 + parts[1] * 1000 + parts[2]
            lyricMap[millis] = lyricPart
        }
    }

    return lyricMap
}

class Music(artist: String, title: String, filename: String) {
    var artist: String by property(artist)
    var title: String by property(title)
    var filename: String by property(filename)

    override fun toString(): String {
        return "Music($artist:$title)"
    }
}

class MainView : View() {
    private val mainController: MainController by inject()
    private val musics = mainController.loadMusics()
    private val player = mainController.loadPlayer()
    private lateinit var slider: Slider
    private lateinit var progressBar: ProgressBar
    private lateinit var textFlow: TextFlow
    private lateinit var tableView: TableView<Music>
    private var lyricMap: TreeMap<Int, String> = TreeMap()
    private lateinit var scrollPane: ScrollPane
    private lateinit var label: Label
    private lateinit var button: Button

    private fun calculateLyricIndex(lyricMap: TreeMap<Int, String>): Int {
        val position = player.length * player.position
        val beginPosition = lyricMap.keys.first()
        val endPosition = lyricMap.keys.last()

        if (position < beginPosition) return beginPosition
        var lastPosition = beginPosition
        for (k in lyricMap.keys) {
            if (k >= position) {
                return lastPosition
            }
            lastPosition = k
        }
        return endPosition
    }

    private fun play(music: Music) {
        val lrcFilename = music.filename.replace(Regex("\\.[^.]+$"), ".lrc")
        val lrcText = FileUtils.readFileToString(File(lrcFilename), Charset.forName("GBK"))
        lyricMap = parseLyric(lrcText)

        player.playMedia(music.filename)
        title = "${music.artist}:${music.title} - SteelPlayer"
        refreshLyric()
    }

    private fun playNext() {
        val virtualFlow = (tableView.skin as TableViewSkin<*>).children[1] as VirtualFlow<*>
        val visibleRows = virtualFlow.lastVisibleCell.index - virtualFlow.firstVisibleCell.index

        val music = musics.random()
        tableView.selectionModel.select(music)
        tableView.scrollTo(tableView.selectionModel.selectedIndex - (visibleRows / 2))
        play(music)
    }

    fun refreshLyric() {
        if (lyricMap.isEmpty()) return
        println("Refreshing...")
        val position = calculateLyricIndex(lyricMap)
        println("LYRIC POSITION: $position")
        textFlow.children.clear()
        lyricMap.forEach { k, v ->
            textFlow.children.add(Text(v + "\n").apply {
                val rate = max(currentStage!!.width / 1000, 1.0)
                if (k == position) {
                    style {
                        fontSize = 28.px * rate
                        fill = Color.GREEN
                    }
                } else {
                    style {
                        fontSize = 24.px * rate
                    }
                }
            })
        }
        val visibleRows = (scrollPane.heightProperty().get() / textFlow.heightProperty().get()) * lyricMap.size
        scrollPane.vvalue = (lyricMap.keys.indexOf(position).toDouble() - visibleRows / 2) / (lyricMap.size - visibleRows)
    }

    private fun refreshLayout() {
        button.graphic = Glyph.create("FontAwesome|" + if (player.isPlaying) FontAwesome.Glyph.PAUSE else (FontAwesome.Glyph.PLAY)).color(Color.GREEN).size(40.0)
    }

    override val root = vbox {
        minWidth = 900.0
        minHeight = 600.0
        hbox {
            vgrow = Priority.ALWAYS
            tableview(musics) {
                tableView = this
                columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
                prefWidth = .0
                maxWidth = Double.POSITIVE_INFINITY
                hgrow = Priority.ALWAYS
                column("Artist", Music::artist)
                column("Title", Music::title)
                onUserSelect { play(it) }
            }

            scrollpane {
                scrollPane = this
                prefWidth = .0
                maxWidth = Double.POSITIVE_INFINITY
                hgrow = Priority.ALWAYS
                style {
                    fitToWidth = true
                    hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                    vbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
                }
                textflow {
                    textFlow = this
                    text { text = "Lyric show" }
                    style {
                        textAlignment = TextAlignment.CENTER
                    }
                    setOnMouseClicked {
                        if (it.pickResult.intersectedNode is TextFlow) return@setOnMouseClicked
                        val index = ((it.y / this.height) * (lyricMap.size - 1)).roundToInt()
                        val position = lyricMap.keys.toList()[index]
                        println("====position: $position")
                        val percent = position.toFloat() / player.length
                        println(percent)
                        player.position = percent
                    }
                }
            }
        }

        hbox {
            alignment = Pos.CENTER_LEFT
            button {
                button = this
                graphic = Glyph.create("FontAwesome|" + FontAwesome.Glyph.PLAY).color(Color.GREEN).size(40.0)
                action {
                    player.setPause(player.isPlaying)
                }
            }
            button {
                graphic = Glyph.create("FontAwesome|" + FontAwesome.Glyph.STEP_FORWARD).color(Color.GREEN).size(40.0)
                action {
                    playNext()
                }
            }
            stackpane {
                hgrow = Priority.ALWAYS
                progressbar(initialValue = .0) {
                    progressBar = this
                    maxWidth = Double.MAX_VALUE
                }
                slider(max = 1) {
                    slider = this
                    maxWidth = Double.MAX_VALUE
                }
            }
            label("00:00/00:00") {
                label = this
                style {
                    paddingRight = 10
                }
            }
        }
    }

    init {
        title = "Steel Player"
        progressBar.progressProperty().bind(slider.valueProperty())
        progressBar.paddingLeftProperty.bind(progressBar.heightProperty().divide(2))
        progressBar.paddingRightProperty.bind(progressBar.heightProperty().divide(2))

        val tableColumn = TableColumn<Music, Music>("#")
        tableColumn.setCellValueFactory {
            return@setCellValueFactory ReadOnlyObjectWrapper(it.value)
        }
        tableColumn.setCellFactory {
            return@setCellFactory object : TableCell<Music, Music>() {
                override fun updateItem(item: Music?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (this.tableRow != null && item != null) this.tableRow.index.plus(1).toString() else ""
                }
            }
        }
        tableColumn.isSortable = false
        tableColumn.maxWidth = 80.0
        tableColumn.minWidth = 80.0
        tableView.columns.add(0, tableColumn)

        slider.valueProperty().addListener { _, oldValue, newValue ->
            if (abs(newValue.toDouble() - oldValue.toDouble()) * player.length > 500) {
                player.position = newValue.toFloat()
                println("Slider changed: ${newValue.toDouble() - oldValue.toDouble()}")
            }
        }

        primaryStage.widthProperty().onChange {
            refreshLyric()
        }

        player.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            private var lastPosition = -1.0f
            override fun stopped(mediaPlayer: MediaPlayer?) {
                super.stopped(mediaPlayer)
                runLater { playNext() }
            }

            override fun mediaStateChanged(mediaPlayer: MediaPlayer?, newState: Int) {
                super.mediaStateChanged(mediaPlayer, newState)
                runLater { refreshLayout() }
            }

            override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
                super.positionChanged(mediaPlayer, newPosition)
                if (abs(newPosition - lastPosition) * player.length > 100) {
                    println("Position changed: ${newPosition - lastPosition}")
                    lastPosition = newPosition
                    val total = (player.length / 1000).toInt()
                    val current = (total * newPosition).roundToInt()
                    val text = "%02d:%02d/%02d:%02d".format(current / 60, current % 60, total / 60, total % 60)
                    runLater {
                        slider.value = newPosition.toDouble()
                        label.text = text
                        refreshLyric()
                    }
                }
            }
        })

        primaryStage.addEventHandler(WindowEvent.WINDOW_SHOWN) {
            playNext()
            runLater(Duration(200.0)) {
                player.pause()
            }
        }

        primaryStage.setOnCloseRequest {
            player.release()
        }
    }
}

class MainStylesheet : Stylesheet() {

    init {
        root {
            fontFamily = "Noto Sans CJK SC Medium"
            fontSize = 20.px
            tableView {
                focusColor = Color.TRANSPARENT
                faintFocusColor = Color.TRANSPARENT
            }
            scrollPane {
                focusColor = Color.TRANSPARENT
                faintFocusColor = Color.TRANSPARENT
            }
            slider {
                track {
                    backgroundColor = MultiValue(arrayOf(Color.TRANSPARENT))
                }
            }
            button {
                backgroundColor = MultiValue(arrayOf(Color.TRANSPARENT))
                hover {
                    backgroundColor = MultiValue(arrayOf(Color(0.90, 0.90, 0.90, 0.99)))
                }
            }
        }
    }
}

class MainController : Controller() {
    fun loadMusics(): ObservableList<Music> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:**.{wma,mp3}")
        return Files.walk(Paths.get("/Volumes/repo/music/fav"))
                .filter { Files.isRegularFile(it) }
                .filter { matcher.matches(it) }
                .map {
                    val baseName = FilenameUtils.getBaseName(it.toString())
                    val parts = baseName.split("-", limit = 2)
                    Music(parts.first(), parts.last(), it.toString())
                }.toList().observable()
    }

    fun loadPlayer(): MediaPlayer {
        return AudioMediaPlayerComponent().mediaPlayer
    }
}

class App : tornadofx.App(MainView::class, MainStylesheet::class)

fun main(args: Array<String>) {
    Application.launch(App::class.java, *args)
}
