import com.fasterxml.jackson.databind.ObjectMapper
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.primitives.TextBoard
import graphics.scenery.utils.SceneryJPanel
import graphics.scenery.utils.extensions.minus
import graphics.scenery.utils.extensions.plus
import graphics.scenery.utils.extensions.times
import graphics.scenery.volumes.TransferFunction
import graphics.scenery.volumes.Volume
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import org.msgpack.jackson.dataformat.MessagePackFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import tpietzsch.example2.VolumeViewerOptions
import java.awt.BorderLayout
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import javax.swing.JFrame
import kotlin.concurrent.thread
import kotlin.math.PI


class SixtyPercentFruitfly : SceneryBase("60percentfruitfly", 1280, 720, wantREPL = true) {
    private lateinit var mainFrame: JFrame

    override fun init() {
        mainFrame = JFrame(applicationName)
        mainFrame.setSize(windowWidth, windowHeight)
        mainFrame.layout = BorderLayout()

        val sceneryPanel = SceneryJPanel()
        mainFrame.add(sceneryPanel, BorderLayout.CENTER)
        mainFrame.isVisible = true

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight, embedIn = sceneryPanel))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            perspectiveCamera(50.0f, windowWidth, windowHeight)
            spatial {
                position = Vector3f(-1.380E+0f,  1.834E+0f, -3.273E+0f)
                rotation = Quaternionf(1.132E-3f,  9.988E-1f, 2.703E-2f, 4.189E-2f)
            }
            scene.addChild(this)
        }

        val b = Box()
        scene.addChild(b)

        Light
            .createLightTetrahedron<PointLight>(spread = 5.0f, intensity = 10.0f)
            .forEach { light -> scene.addChild(light) }

        val vvo = VolumeViewerOptions()
        val drosophila = Volume.fromXML("./droso-royer-autopilot-transposed-bdv/export-norange.xml", hub, vvo)
        drosophila.apply {
            spatial().rotation = spatial().rotation.rotateX(PI.toFloat()/2.0f)
            spatial().scale = Vector3f(5.0f, 15.0f, 5.0f)

            transferFunction = TransferFunction.ramp(0.1f, 0.5f)
            converterSetups.firstOrNull()?.setDisplayRange(10.0, 1000.0)
            multiResolutionLevelLimits = 1 to 2
        }
        scene.addChild(drosophila)

        // TODO: change font to Roboto Slab Thin
        val board = TextBoard()
        board.spatial().position = Vector3f(0.5f, 0.2f, -2.0f)
        board.text = "t=${drosophila.currentTimepoint}"
        board.spatial().scale = Vector3f(0.5f)
        cam.addChild(board)

        val description = TextBoard()
        description.text = "die entwicklung einer fruchtfliege"
        description.spatial().position = Vector3f(-0.7f, -0.3f, -1.0f)
        description.spatial().scale = Vector3f(0.05f)
        description.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        description.backgroundColor = Vector4f(10.0f)
        description.transparent = 0
        cam.addChild(description)

        val subtitle = TextBoard()
        subtitle.text = "vom zellhaufen zur larve"
        subtitle.spatial().position = Vector3f(-0.73f, -0.36f, -1.0f)
        subtitle.spatial().scale = Vector3f(0.05f)
        subtitle.fontColor = Vector4f(0.0f, 0.0f, 0.0f, 1.0f)
        subtitle.backgroundColor = Vector4f(10.0f)
        subtitle.transparent = 0
        cam.addChild(subtitle)

        data class Part(
            val parallel: Boolean = false,
            val condition: () -> Boolean,
            val action: () -> Any,
            val minimumWait: Long,
            var done: Boolean = false
        )

        val choreography = arrayListOf(
            Part(parallel = false, minimumWait = 200,
                condition = { drosophila.currentTimepoint < drosophila.timepointCount-1 },
                action = {
                    drosophila.goToTimepoint(drosophila.currentTimepoint+5)
                    board.text = "t=${drosophila.currentTimepoint} vs ${drosophila.timepointCount}"
                    logger.info("t=${drosophila.currentTimepoint} vs ${drosophila.timepointCount}")

                    if(drosophila.currentTimepoint > 50) {
                        description.text = " warum ist das fuer forscher interessant? "
                        subtitle.text = " fruchtfliegen haben 60% genetischen ueberlapp mit menschen "
                    }
                }),

            Part(parallel = false, minimumWait = 20,
                condition = { drosophila.currentTimepoint > 0 },
                action = {
                    logger.info("playing back")
                    drosophila.goToTimepoint(drosophila.currentTimepoint-5)
                    board.text = "t=${drosophila.currentTimepoint}"
                    description.text = " ... und nochmal von vorn "
                    subtitle.text = " im schnelldurchlauf "
                    Thread.sleep(20)
                }),

            Part(parallel = false, minimumWait = 100,
            condition = { drosophila.currentTimepoint < drosophila.timepointCount-1 },
            action = {
                drosophila.nextTimepoint()
                board.text = "t=${drosophila.currentTimepoint}"

                when (drosophila.currentTimepoint) {
                    1 -> {
                        description.text = " am anfang gibt es nur zellkerne auf der aussenseite "
                        subtitle.text = " dann entwickeln sich zellwaende "
                    }

                    10 -> {
                        description.text = " die gastrulation beginnt "
                        subtitle.text = " einer der wichtigsten entwicklungsschritte (fast) aller lebewesen "
                    }

                    70 -> {
                        description.text = " gehirn-entwicklung startet "
                        subtitle.text = " bei der fruchtfliege passiert das zuerst aussen "
                    }

                    100 -> {
                        description.text = " die segmentierung startet "
                        subtitle.text = " aus den einzelnen segmenten entstehen spaeter z.b. die fluegel "
                    }

                    170 -> {
                        description.text = " was wird denn da gefressen? "
                        subtitle.text = " nein, es wird nichts gefressen -- aber das gehirn wandern nach innen "
                    }

                    210 -> {
                        description.text = " muskelbewegungen beginnen "
                        subtitle.text = " training fuer das larvenstadium "
                    }
                }
                Thread.sleep(500)
            })
        )

        fun ArrayList<Part>.runSuitable(repeat: Boolean = false) {
            val parts = this.groupBy { it.parallel }
            val parallel = parts[true] ?: emptyList()
            val sequential = parts[false] ?: emptyList()

            var runs = sequential.map { 0 }.toTypedArray()

            fun runSequence() {
                sequential.forEachIndexed { i, part ->
                    val result = part.condition.invoke()
                    if (result && !part.done ) {
                        logger.info("Invoking action #$i")
                        part.action.invoke()
                        runs[i]++
                        Thread.sleep(part.minimumWait)

                        return
                    } else {
                        if(runs[i] > 0 && !part.done) {
                            logger.info("Marking #$i as done")
                            part.done = true
                        }
                    }
                }

                if(repeat && runs.all { it > 0 } && sequential.all { it.done }) {
                    // reset states of all parts
                    runs = sequential.map { 0 }.toTypedArray()
                    sequential.forEach { it.done = false }
                }
            }

            while(!Thread.currentThread().isInterrupted) {
                runSequence()
            }
        }

        thread {
            while(renderer?.firstImageReady == false || !scene.initialized) {
                Thread.sleep(500)
            }
            Thread.sleep(2000)

            choreography.runSuitable(repeat = true)
        }

        thread {
            val personData = ConcurrentLinkedDeque<Vector3f>()
            fun add(v: Vector3f) {
                if(personData.size > 10) {
                    personData.removeLast()
                }
                personData.push(v)
            }

            fun avgPos(): Vector3f =
                personData
                    .fold(Vector3f(0.0f)) { lhs: Vector3f, rhs: Vector3f -> lhs + rhs}

            val objectMapper = ObjectMapper(MessagePackFactory())
            val c = ZContext()
            val socket = c.createSocket(SocketType.SUB)
            socket.connect("tcp://127.0.0.1:5569")
            socket.subscribe("")
            logger.info("DepthAI socket connected")

            while(!Thread.currentThread().isInterrupted) {
                val label = socket.recvStr()
                val msg = socket.recv()
                val array = objectMapper.readValue(msg, FloatArray::class.java)

                val accuracy = array[0]
                // position from DepthAI is in mm
                val p = Vector3f(array.sliceArray(1..3)) / 1000.0f

                if(accuracy > 0.70f && label == "person") {
                    logger.info("$label: ${accuracy * 100.0f}%, $p")

                    add(p)
                    drosophila.spatial().apply {
                        val `∂p` = (avgPos() - p)
                        val `∂x` = `∂p`.dot(Vector3f(1.0f, 0.0f, 0.0f))
                        val `∂y` = `∂p`.dot(Vector3f(1.0f, 0.0f, 0.0f))
                        val `∂z` = `∂p`.dot(Vector3f(1.0f, 0.0f, 0.0f))
                        logger.info("∂phi=$`∂p`")
//                        rotation = rotation.rotateX(`∂x`).normalize()
//                        rotation = rotation.rotateY(`∂y`).normalize()
//                        rotation = rotation.rotateZ(`∂z`).normalize()
                        position = Vector3f(0.0f) + `∂p`/50.0f
                    }
                }
            }
        }
    }
}