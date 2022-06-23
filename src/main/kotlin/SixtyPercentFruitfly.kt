import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.SceneryJPanel
import graphics.scenery.volumes.Volume
import org.joml.Vector3f
import java.awt.BorderLayout
import javax.swing.JFrame

class SixtyPercentFruitfly : SceneryBase("60percentfruitfly", 1280, 720) {
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
                position = Vector3f(0.0f, 0.0f, -5.0f)
            }
            scene.addChild(this)
        }

        val b = Box()
        scene.addChild(b)

        Light
            .createLightTetrahedron<PointLight>(spread = 5.0f, intensity = 10.0f)
            .forEach { light -> scene.addChild(light) }

        val drosophila = Volume.fromXML("./droso-royer-autopilot-transposed-bdv/export-norange.xml", hub)
        scene.addChild(drosophila)
    }
}