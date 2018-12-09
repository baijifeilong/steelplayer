import javafx.application.Application
import javafx.collections.ObservableList
import org.apache.commons.io.FilenameUtils
import tornadofx.*
import uk.co.caprica.vlcj.component.AudioMediaPlayerComponent
import uk.co.caprica.vlcj.player.MediaPlayer
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

    override val root = tableview(musics) {
        minWidth = 600.0
        minHeight = 400.0
        column("Artist", Music::artist) {
            maxWidth = 200.0
        }
        column("Title", Music::title)
        onUserSelect {
            player.playMedia(it.filename)
        }
    }

    init {
        musics.addAll(mainController.loadMusics())
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
