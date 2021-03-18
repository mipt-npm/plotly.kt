package space.kscience.plotly.server

import io.ktor.application.*
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.origin
import io.ktor.html.respondHtml
import io.ktor.http.*
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.resource
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.websocket.WebSockets
import io.ktor.websocket.application
import io.ktor.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.html.*
import kotlinx.serialization.json.Json
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.names.Name
import space.kscience.dataforge.names.toName
import space.kscience.plotly.*
import space.kscience.plotly.server.PlotlyServer.Companion.DEFAULT_PAGE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_CLASS
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_DATA_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_NAME_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.OUTPUT_PUSH_ATTRIBUTE
import space.kscience.plotly.server.PlotlyServerConstants.PLOTLY_CONFIG_CLASS
import space.kscience.plotly.server.PlotlyServerConstants.PLOTLY_DATA_CLASS
import java.awt.Desktop
import java.net.URI
import kotlin.collections.set

public enum class PlotlyUpdateMode {
    NONE,
    PUSH,
    PULL
}

private class ServerPlotlyRenderer(
    val baseUrl: Url,
    val updateMode: PlotlyUpdateMode,
    val updateInterval: Long,
    val embedData: Boolean,
    val plotCallback: (plotId: String, plot: Plot) -> Unit,
) : PlotlyRenderer {
    override fun FlowContent.renderPlot(plot: Plot, plotId: String, config: PlotlyConfig): Plot {
        plotCallback(plotId, plot)
        div(OUTPUT_CLASS) {
            id = plotId
            attributes[OUTPUT_NAME_ATTRIBUTE] = id

            if (embedData) {
                script {
                    attributes["class"] = PLOTLY_DATA_CLASS
                    unsafe {
                        +Json.encodeToString(MetaSerializer, plot.config)
                    }
                }
                script {
                    attributes["class"] = PLOTLY_CONFIG_CLASS
                    unsafe {
                        +Json.encodeToString(MetaSerializer, config.toMeta())
                    }
                }
            }

            val dataUrl = baseUrl.copy(
                encodedPath = baseUrl.encodedPath + "/data/$plotId"
            )

            attributes[OUTPUT_DATA_ATTRIBUTE] = dataUrl.toString()

            if (updateMode == PlotlyUpdateMode.PUSH) {
                val wsUrl = baseUrl.copy(
                    protocol = URLProtocol.WS,
                    encodedPath = baseUrl.encodedPath + "/ws/$plotId"
                )
                attributes[OUTPUT_PUSH_ATTRIBUTE] = wsUrl.toString()
            }
        }
        return plot
    }
}

public class PlotlyServer internal constructor(
    private val routing: Routing,
    private val rootRoute: String,
) : Configurable {
    override val config: Config = Config()
    public var updateMode: PlotlyUpdateMode by config.enum(PlotlyUpdateMode.NONE, key = UPDATE_MODE_KEY)
    public var updateInterval: Long by config.long(300, key = UPDATE_INTERVAL_KEY)
    public var embedData: Boolean by config.boolean(false)

    /**
     * a list of headers that should be applied to all pages
     */
    private val globalHeaders: ArrayList<PlotlyHtmlFragment> = ArrayList<PlotlyHtmlFragment>()

    public fun header(block: TagConsumer<*>.() -> Unit) {
        globalHeaders.add(PlotlyHtmlFragment(block))
    }

    public fun page(
        plotlyFragment: PlotlyFragment,
        route: String = DEFAULT_PAGE,
        title: String = "Plotly server page '$route'",
        headers: List<PlotlyHtmlFragment> = emptyList(),
    ) {
        routing.createRouteFromPath(rootRoute).apply {
            val plots = HashMap<String, Plot>()
            route(route) {
                //Update websocket
                webSocket("ws/{id}") {
                    val plotId: String = call.parameters["id"] ?: error("Plot id not defined")

                    application.log.debug("Opened server socket for $plotId")

                    val plot = plots[plotId] ?: error("Plot with id='$plotId' not registered")

                    try {
                        plot.collectUpdates(plotId, this, updateInterval).collect { update ->
                            val json = update.toJson()
                            outgoing.send(Frame.Text(json.toString()))
                        }
                    } catch (ex: Exception) {
                        application.log.debug("Closed server socket for $plotId")
                    }
                }
                //Plots in their json representation
                get("data/{id}") {
                    val id: String = call.parameters["id"] ?: error("Plot id not defined")

                    val plot: Plot? = plots[id]
                    if (plot == null) {
                        call.respond(HttpStatusCode.NotFound, "Plot with id = $id not found")
                    } else {
                        call.respondText(
                            plot.toJsonString(),
                            contentType = ContentType.Application.Json,
                            status = HttpStatusCode.OK
                        )
                    }
                }
                //filled pages
                get {
                    val origin = call.request.origin
                    val url = URLBuilder().apply {
                        protocol = URLProtocol.createOrDefault(origin.scheme)
                        //workaround for https://github.com/ktorio/ktor/issues/1663
                        host = if (origin.host.startsWith("0:")) "[${origin.host}]" else origin.host
                        port = origin.port
                        encodedPath = origin.uri
                    }.build()
                    call.respondHtml {
                        val normalizedRoute = if (rootRoute.endsWith("/")) {
                            rootRoute
                        } else {
                            "$rootRoute/"
                        }

                        head {
                            meta {
                                charset = "utf-8"
                                (globalHeaders + headers).forEach {
                                    it.visit(consumer)
                                }
                                script {
                                    type = "text/javascript"
                                    src = "${normalizedRoute}js/plotlykt.js"
                                }
                            }
                            title(title)
                        }
                        body {
                            val container = ServerPlotlyRenderer(
                                url, updateMode, updateInterval, embedData
                            ) { plotId, plot ->
                                plots[plotId] = plot
                            }
                            with(plotlyFragment) {
                                render(container)
                            }
                        }
                    }
                }
            }
        }
    }

    public fun page(
        route: String = DEFAULT_PAGE,
        title: String = "Plotly server page '$route'",
        headers: List<PlotlyHtmlFragment> = emptyList(),
        content: FlowContent.(renderer: PlotlyRenderer) -> Unit,
    ) {
        page(PlotlyFragment(content), route, title, headers)
    }

    /**
     * Exposes the Ktor application environment to internal logic
     */
    public val application: Application get() = routing.application

    public companion object {
        public const val DEFAULT_PAGE: String = "/"
        public val UPDATE_MODE_KEY: Name = "update.mode".toName()
        public val UPDATE_INTERVAL_KEY: Name = "update.interval".toName()
    }
}


/**
 * Attach plotly application to given server
 */
public fun Application.plotlyModule(route: String = DEFAULT_PAGE): PlotlyServer {
    if (featureOrNull(WebSockets) == null) {
        install(WebSockets)
    }

    if (featureOrNull(CORS) == null) {
        install(CORS) {
            anyHost()
        }
    }


    routing {
        route(route) {
            static {
                resource("js/plotlykt.js")
            }
        }
    }

//    val root: Route = feature(Routing).createRouteFromPath(route)
    return PlotlyServer(feature(Routing), route)
}


/**
 * Configure server to start sending updates in push mode. Does not affect loaded pages
 */
public fun PlotlyServer.pushUpdates(interval: Long = 100): PlotlyServer = apply {
    updateMode = PlotlyUpdateMode.PUSH
    updateInterval = interval
}

/**
 * Configure client to request regular updates from server. Pull updates are more expensive than push updates since
 * they contain the full plot data and server can't decide what to send.
 */
public fun PlotlyServer.pullUpdates(interval: Long = 1000): PlotlyServer = apply {
    updateMode = PlotlyUpdateMode.PULL
    updateInterval = interval
}

/**
 * Start static server (updates via reload)
 */
@OptIn(KtorExperimentalAPI::class)
public fun Plotly.serve(
    scope: CoroutineScope = GlobalScope,
    host: String = "localhost",
    port: Int = 7777,
    block: PlotlyServer.() -> Unit,
): ApplicationEngine = scope.embeddedServer(io.ktor.server.cio.CIO, port, host) {
    install(CallLogging)
    plotlyModule().apply(block)
}.start()


public fun ApplicationEngine.show() {
    val connector = environment.connectors.first()
    val uri = URI("http", null, connector.host, connector.port, null, null, null)
    Desktop.getDesktop().browse(uri)
}

public fun ApplicationEngine.close(): Unit = stop(1000, 5000)