package space.kscience.plotly.fx

import javafx.beans.value.ObservableIntegerValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import space.kscience.plotly.Plotly
import space.kscience.plotly.layout
import space.kscience.plotly.models.Trace
import space.kscience.plotly.models.invoke
import space.kscience.plotly.plot
import space.kscience.plotly.server.pushUpdates
import space.kscience.plotly.server.serve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun serve(scale: ObservableIntegerValue) = Plotly.serve(port = 7778) {
    embedData = false //Should be set this way to avoid FX browser bug

    page("Static") {container ->
        val x = (0..100).map { it.toDouble() / 100.0 }.toDoubleArray()
        val y1 = x.map { sin(2.0 * PI * it) }.toDoubleArray()
        val y2 = x.map { cos(2.0 * PI * it) }.toDoubleArray()
        val trace1 = Trace(x, y1) {
            name = "sin"
        }
        val trace2 = Trace(x, y2) {
            name = "cos"
        }
        plot (renderer = container){//static plot
            traces(trace1, trace2)
            layout {
                title = "First graph, row: 1, size: 8/12"
                xaxis.title = "x axis name"
                yaxis { title = "y axis name" }
            }
        }
    }

    page("Dynamic") { container ->
        val x = (0..100).map { it.toDouble() / 100.0 }
        val y = x.map { sin(2.0 * PI * it) }

        val trace = Trace(x, y) { name = "sin" }

        val plot = plot("dynamic", renderer = container) {
            traces(trace)
            layout {
                title = "Dynamic plot"
                xaxis.title = "x axis name"
                yaxis.title = "y axis name"
            }
        }

        GlobalScope.launch {
            var time: Long = 0
            while (isActive) {
                delay(10)
                time += 10
                val frequency = scale.get().toDouble()
                val dynamicY = x.map { sin(2.0 * PI * frequency * (it + time.toDouble() / 1000.0)) }
                //trace.y.numbers = dynamicY
                plot.data[0].y.numbers = dynamicY
                plot.layout{
                    xaxis.title = "x axis name (t = $time)"
                }
            }
        }
    }
    pushUpdates(100)
}


