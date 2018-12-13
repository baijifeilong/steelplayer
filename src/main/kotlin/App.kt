import javafx.application.Application
import javafx.collections.ObservableList
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.control.Slider
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import tornadofx.*
import uk.co.caprica.vlcj.component.AudioMediaPlayerComponent
import uk.co.caprica.vlcj.player.MediaPlayer
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter
import java.io.File
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.streams.toList

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
    private lateinit var musicLabel: Label

    override val root = vbox {
        hbox {
            minWidth = 800.0
            tableview(musics) {
                hgrow = Priority.ALWAYS
                column("Artist", Music::artist) {
                    hgrow = Priority.NEVER
                    maxWidth = 200.0
                }
                column("Title", Music::title) {
                    hgrow = Priority.ALWAYS
                }
                onUserSelect {
                    player.playMedia(it.filename)
                    player.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                        override fun paused(mediaPlayer: MediaPlayer?) {
                            super.paused(mediaPlayer)
                        }

                        override fun positionChanged(mediaPlayer: MediaPlayer?, newPosition: Float) {
                            super.positionChanged(mediaPlayer, newPosition)
                            slider.value = newPosition.toDouble()
                            println("Position: $newPosition")
                        }
                    })
                    val lrcFilename = it.filename.replace(Regex("\\.[^.]+$"), ".lrc")
                    println(lrcFilename)
                    val lrcText = FileUtils.readFileToString(File(lrcFilename), Charset.forName("GBK"))
                    println(lrcText)
                    musicLabel.text = lrcText
                }
            }
            label("Lyric show") {
                hgrow = Priority.ALWAYS
                musicLabel = this
            }
            button("Click me") {
                setOnMouseClicked {
                    player.setPause(player.isPlaying)
                }
            }
        }

        stackpane {
            progressbar(initialValue = .0) {
                progressBar = this
                maxWidth = Double.MAX_VALUE
            }
            slider(max = 1) {
                slider = this
                maxWidth = Double.MAX_VALUE
            }
        }
    }

    init {
        progressBar.progressProperty().bind(slider.valueProperty())
        progressBar.paddingLeftProperty.bind(progressBar.heightProperty().divide(2))
        progressBar.paddingRightProperty.bind(progressBar.heightProperty().divide(2))

        slider.valueProperty().addListener { observable, oldValue, newValue ->
            if (abs(newValue.toDouble() - oldValue.toDouble()) * player.length > 999) {
                player.position = newValue.toFloat()
            }
        }
    }
}

class MainStylesheet : Stylesheet() {
    init {
        root {
            fontFamily = "Noto Sans CJK SC Medium"
            fontSize = 20.px
            slider {
                track {
                    backgroundColor = MultiValue(arrayOf(Color.TRANSPARENT))
                }
            }
        }
    }
}

class MainController : Controller() {
    fun loadMusics(): ObservableList<Music> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:**.{wma,mp3}")
        return Files.walk(Paths.get("/mnt/d/music/test"))
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
