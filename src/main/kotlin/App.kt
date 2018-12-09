import javafx.application.Application
import javafx.collections.ObservableList
import javafx.scene.control.Label
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import tornadofx.*
import uk.co.caprica.vlcj.component.AudioMediaPlayerComponent
import uk.co.caprica.vlcj.player.MediaPlayer
import java.io.File
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
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
    private lateinit var musicLabel: Label

    override val root = hbox {
        tableview(musics) {
            minWidth = 800.0
            minHeight = 600.0
            column("Artist", Music::artist) {
                maxWidth = 200.0
            }
            column("Title", Music::title)
            onUserSelect {
                player.playMedia(it.filename)
                val lrcFilename = it.filename.replace(Regex("\\.[^.]+$"), ".lrc")
                println(lrcFilename)
                val lrcText = FileUtils.readFileToString(File(lrcFilename), Charset.forName("GBK"))
                println(lrcText)
                musicLabel.text = lrcText
            }
        }
        label("Lyric show") {
            musicLabel = this
            minWidth = 400.0
        }
    }
}

class MainStylesheet : Stylesheet() {
    init {
        root {
            fontFamily = "Noto Sans CJK SC Medium"
            fontSize = 20.px
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
