package space.kscience.plotly.server

import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CORS
import io.ktor.http.URLProtocol
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.url
import io.ktor.websocket.WebSockets
import io.ktor.websocket.application
import io.ktor.websocket.webSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import space.kscience.plotly.*

@UnstablePlotlyAPI
public class JupyterPlotlyServer(
    private val port: Int = 8882,
    private val updateInterval: Long = 50,
    private val parentScope: CoroutineScope = GlobalScope,
) : PlotlyRenderer {

    private var server: ApplicationEngine? = null

    private val plots = HashMap<String, Plot>()

    @OptIn(KtorExperimentalAPI::class)
    public suspend fun start() {
        server = parentScope.embeddedServer(
            io.ktor.server.cio.CIO,
            port,
            parentCoroutineContext = Dispatchers.Default
        ) {
            install(CORS) {
                anyHost()
            }
            install(WebSockets)
            routing {
                static {
                    resources()
                }
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
            }
        }.start(false)
    }

    override fun FlowContent.renderPlot(plot: Plot, plotId: String, config: PlotlyConfig): Plot {
        plots[plotId] = plot
        div {
            id = plotId
            script {
                unsafe {
                    //language=JavaScript
                    +"""
                        (function (){
                            let theCall = function(){
                                makePlot(
                                    '$plotId',
                                    ${plot.data.toJsonString()},
                                    ${plot.layout.toJsonString()},
                                    $config
                                );                        
                            };
    
                            if(typeof Plotly === 'undefined'){
                                if(!window.plotlyCallQueue) {
                                    window.plotlyCallQueue = [];
                                }                            
                                window.plotlyCallQueue.push(theCall)
                            } else {
                                theCall();
                            }
                        }());
                    """.trimIndent()
                }
            }
            if (server?.application?.isActive == true) {
                script {
                    attributes["id"] = "$plotId-push"
                    val wsUrl = url {
                        host = "localhost"
                        port = this@JupyterPlotlyServer.port
                        protocol = URLProtocol.WS
                        encodedPath = "/ws/$plotId"
                    }
                    unsafe {
                        //language=JavaScript
                        +"""
       
                            startPush('$plotId', '$wsUrl');
                            
                        """
                    }
                }
            }
        }
        return plot
    }

    public fun stop() {
        server?.stop(1000, 5000)
    }

    public companion object {

        private fun loadJs(serverUrl: String) = PlotlyHtmlFragment {

            script {
                type = "text/javascript"
                unsafe {
                    //language=JavaScript
                    +"""
                (function() {
                    console.log("Starting up plotly script loader");
                    //initialize LaTeX for Jupyter
                    window.PlotlyConfig = {MathJaxConfig: 'local'};
                    window.startupPlotly = function (){
                        if (window.MathJax){ 
                            MathJax.Hub.Config({
                                SVG: {
                                    font: "STIX-Web"
                                }
                            });
                        }                    
                        console.info("Calling deferred operations in Plotly queue.")
                        if(window.plotlyCallQueue){
                            window.plotlyCallQueue.forEach(function(theCall) {theCall();});
                            window.plotlyCallQueue = [];
                        }
                    }
                })();
                """.trimIndent()
                }
            }

            script {
                type = "text/javascript"
                src = "$serverUrl/js/plotly.min.js"
            }

            script {
                type = "text/javascript"
                val connectorScript = javaClass.getResource("/js/plotlyConnect.js")!!.readText()
                unsafe {
                    +connectorScript
                }
                unsafe {
                    //language=JavaScript
                    +"window.startupPlotly()"
                }
            }
        }

        private var jupyterPlotlyServer: JupyterPlotlyServer? = null

        /**
         * Start a dynamic update server
         */
        public fun start(port: Int = 8882, updateInterval: Long = 50): PlotlyHtmlFragment {
            return if (jupyterPlotlyServer != null) {
                loadJs("//localhost:$port") + PlotlyHtmlFragment {
                    div {
                        style = "color: blue;"
                        +"The server is already running on ${jupyterPlotlyServer?.port}. It must be shut down first to be restarted."
                    }
                }
            } else {
                runBlocking {
                    jupyterPlotlyServer = JupyterPlotlyServer(port, updateInterval).apply {
                        start()
                    }
                }
                loadJs("//localhost:$port")
            }
        }

        /**
         * Stop dynamic update server
         */
        public fun stop(): PlotlyHtmlFragment {
            if (jupyterPlotlyServer == null) {
                return PlotlyHtmlFragment {
                    div {
                        +"Update server is not running"
                    }
                }
            }
            runBlocking(Dispatchers.Default) {
                jupyterPlotlyServer?.stop()
                jupyterPlotlyServer = null
            }
            return PlotlyHtmlFragment {
                div {
                    +"Update server is stopped"
                }
            }
        }

        public fun renderPlot(plot: Plot): String = createHTML().div {
            plot(plot, config = PlotlyConfig {
                responsive = true
            }, renderer = jupyterPlotlyServer ?: StaticPlotlyRenderer)
        }

        public fun renderFragment(fragment: PlotlyFragment): String = createHTML().div {
            with(fragment) {
                render(jupyterPlotlyServer ?: StaticPlotlyRenderer)
            }
        }

        public fun renderPage(page: PlotlyPage): String =
            page.copy(renderer = jupyterPlotlyServer ?: StaticPlotlyRenderer).render()
    }
}