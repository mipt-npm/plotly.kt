package space.kscience.plotly

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CloseEvent
import org.w3c.dom.ErrorEvent
import org.w3c.dom.HTMLElement
import org.w3c.dom.WebSocket
import org.w3c.dom.url.URL
import org.w3c.fetch.RequestInit
import kotlin.js.json


/**
 * Wait for Plotly library to be loaded
 */
private fun withPlotly(block: PlotlyJs.() -> Unit) {
    if (jsTypeOf(PlotlyJs) != "undefined") { //if already defined, use it
        PlotlyJs.block()
    } else {
        //TODO add fetch
        error("Plotly is not defined")
    }
}

/**
 * Request and parse json from given address
 * @param url {URL}
 * @param callback
 * @return Promise<Json>
 */
private fun getJSON(url: URL, callback: (dynamic) -> Unit) {
    try {
        window.fetch(
            url,
            RequestInit(
                method = "GET",
                headers = json("Accept" to "application/json")
            )
        ).then { response ->
            if (!response.ok) {
                error("Fetch request failed with error: ${response.statusText}")
            } else {
                response.json().then(callback)
            }
        }.catch { error -> console.log(error) }
    } catch (e: Exception) {
        window.alert("Fetch of plot data failed with error: $e")
    }
}

/**
 * Create a plot taking data from given url
 * @param id {string} element id for plot
 * @param from {URL} json server url
 * @param config {object} plotly configuration
 * @return {JSON}
 */
@JsExport
public fun createPlotFrom(id: String, from: URL, config: dynamic = {}) {
    getJSON(from) { json ->
        val element = document.getElementById(id) as HTMLElement
        withPlotly { newPlot(element, json.data, json.layout, config) }
    }
}

/**
 * Update a plot taking data from given url
 * @param id {string} element id for plot
 * @param from {URL} json server url
 * @return {JSON}
 */
@JsExport
public fun updatePlotFrom(id: String, from: URL) {
    getJSON(from) { json ->
        val element = document.getElementById(id) as HTMLElement
        withPlotly { react(element, json.data, json.layout) }
    }
}

/**
 * Start pull updates with regular requests from client side
 * @param id {string}
 * @param from
 * @param millis
 */
@JsExport
public fun startPull(id: String, from: URL, millis: Int) {
    window.setInterval({ updatePlotFrom(id, from) }, millis)
}

/**
 * Start push updates via websocket
 * @param id {string} element id for plot
 * @param ws {URL} a websocket address
 */
@JsExport
public fun startPush(id: String, ws: String) {
    val element = document.getElementById(id) as HTMLElement
    val socket = WebSocket(ws)

    socket.onopen = {
        console.log("[Plotly.kt] A connection for plot with id = $id with server established on $ws")
    }

    socket.onclose = { event ->
        event as CloseEvent
        if (event.wasClean) {
            console.log("The connection with server is closed")
        } else {
            console.log("The connection with server is broken") // Server process is dead
        }
        console.log("Code: ${event.code} case: ${event.reason}")
    }

    socket.onerror = { error ->
        error as ErrorEvent
        console.error("Plotly push update error: " + error.message)
        socket.close()
    }

    socket.onmessage = { event ->
        val json: dynamic = JSON.parse(event.data.toString())
        if (json.plotId === id) {
            if (json.contentType === "layout") {
                withPlotly { relayout(element, json.content) }
            } else if (json.contentType === "trace") {
                val content = json.content
                //This is done to satisfy plotly requirements of arrays-in-arrays for data
                if (content.hasOwnProperty("x")) {
                    content.x = arrayOf(content.x)
                }
                if (content.hasOwnProperty("y")) {
                    content.y = arrayOf(content.y)
                }
                if (content.hasOwnProperty("z")) {
                    content.z = arrayOf(content.z)
                }
                withPlotly { restyle(element, content, json.trace) }
            }
        }
    }

    //gracefully close socket just in case
    window.onbeforeunload = {
        console.log("Gracefully closing socket")
        socket.close()
        null
    }
}
