package space.kscience.plotly.server

import org.jetbrains.kotlinx.jupyter.api.HTML
import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.libraries.*
import org.slf4j.LoggerFactory
import space.kscience.plotly.*

@JupyterLibrary
internal class PlotlyServerIntegration : JupyterIntegration() {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val plotlyBundle = ResourceFallbacksBundle(listOf(
        org.jetbrains.kotlinx.jupyter.api.libraries.ResourceLocation(
            "js/plotlykt.js",
            ResourcePathType.CLASSPATH_PATH
        )
    ))

    private val plotlyResource =
        LibraryResource(name = "plotly", type = ResourceType.JS, bundles = listOf(plotlyBundle))

    val port = System.getProperty("kscience.plotly.port")?.toInt() ?: 8882

    @UnstablePlotlyAPI
    override fun Builder.onLoaded() {
        resource(plotlyResource)

        repositories("https://repo.kotlin.link")

        import(
            "space.kscience.plotly.*",
            "space.kscience.plotly.models.*",
            "space.kscience.plotly.server.JupyterPlotlyServer",
            "space.kscience.dataforge.meta.*",
            "kotlinx.html.*"
        )

        render<PlotlyHtmlFragment> {
            HTML(it.toString())
        }

        render<Plot> {
            HTML(JupyterPlotlyServer.renderPlot(it))
        }

        render<PlotlyFragment> {
            HTML(JupyterPlotlyServer.renderFragment(it))
        }

        render<PlotlyPage> {
            HTML(JupyterPlotlyServer.renderPage(it), true)
        }

        onLoaded {
            logger.info("Starting JupyterPlotlyServer at $port")
            val serverStart = JupyterPlotlyServer.start(port)
            logger.info("JupyterPlotlyServer started at $port")
            display(HTML(serverStart.toString()))
        }

        onShutdown {
            logger.info("Stopping JupyterPlotlyServer")
            display(HTML(JupyterPlotlyServer.stop().toString()))
        }
    }

}
