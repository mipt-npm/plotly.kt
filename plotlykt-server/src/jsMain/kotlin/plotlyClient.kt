import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import org.w3c.dom.*
import org.w3c.dom.url.URL
import space.kscience.dataforge.meta.Config
import space.kscience.dataforge.meta.MetaSerializer
import space.kscience.plotly.*
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_CLASS
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_DATA_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_ENDPOINT_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_NAME_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_PUSH_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.PLOTLY_CONFIG_CLASS
import space.kscience.plotly.server.PlotlyServerConstants.PLOTLY_DATA_CLASS

private fun whenDocumentLoaded(block: Document.() -> Unit): Unit {
    if (document.readyState == DocumentReadyState.COMPLETE) {
        block(document)
    } else {
        document.addEventListener("DOMContentLoaded", { block(document) })
    }
}

private fun resolveName(element: Element): String? {
    val attribute = element.attributes[OUTPUT_NAME_ATTRIBUTE]
    return attribute?.value
}

private fun Element.getEmbeddedData(className: String): String? = getElementsByClassName(className)[0]?.innerHTML

private fun resolveEndpoint(element: Element?): String {
    if (element == null) return window.location.href
    val attribute = element.attributes[OUTPUT_ENDPOINT_ATTRIBUTE]
    return attribute?.value ?: resolveEndpoint(element.parentElement)
}

public fun main(): Unit = whenDocumentLoaded {
    val element = document.body ?: error("Document does not have a body")
    val elements = element.getElementsByClassName(OUTPUT_CLASS)
    console.info("Finished search for outputs. Found ${elements.length} items")
    elements.asList().forEach { child ->
        val id = child.id
        val name = resolveName(child) ?: id

        console.info("Found Plotly output with name $name in element $id")
        if (!child.classList.contains(OUTPUT_CLASS)) error("The element $child is not an output element")

        val plot: Plot? = child.getEmbeddedData(PLOTLY_DATA_CLASS)?.let {
            val config = Json.decodeFromString(Config.ConfigSerializer, it)
            Plot(config)
        }

        val plotlyConfig = child.getEmbeddedData(PLOTLY_CONFIG_CLASS)?.let {
            val meta = Json.decodeFromString(MetaSerializer, it)
            PlotlyConfig.read(meta)
        }

        plot?.let {
            child.plot(plot, plotlyConfig ?: PlotlyConfig())
        }

        child.attributes[OUTPUT_DATA_ATTRIBUTE]?.let { attr ->
            val dataUrl = if (attr.value.isBlank() || attr.value == "auto") {
                val endpoint = resolveEndpoint(child)
                console.info("Vision server is resolved to $endpoint")
                URL(endpoint).apply {
                    pathname += "/data"
                }
            } else {
                URL(attr.value)
            }.apply {
                searchParams.append("name", name)
            }
            updatePlotFrom(id, dataUrl)
        }

        child.attributes[OUTPUT_PUSH_ATTRIBUTE]?.let { attr ->
            val wsUrl = if (attr.value.isBlank() || attr.value == "auto") {
                val endpoint = resolveEndpoint(child)
                console.info("Vision server is resolved to $endpoint")
                URL(endpoint).apply {
                    pathname += "/ws"
                }
            } else {
                URL(attr.value)
            }.apply {
                protocol = "ws"
                searchParams.append("name", name)
            }

            console.info("Updating Plotly data from $wsUrl")

            startPush(id, wsUrl.toString())
        }
    }
}
