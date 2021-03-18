package space.kscience.plotly

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import space.kscience.dataforge.meta.*
import space.kscience.dataforge.misc.DFBuilder
import space.kscience.plotly.models.Layout
import space.kscience.plotly.models.Trace

/**
 * The main plot class. The changes to plot could be observed by attaching listener to root [config] property.
 */
@DFBuilder
public class Plot(
    override val config: Config = Config(),
) : Configurable, MetaRepr {

    /**
     * Ordered list ot traces in the plot
     */
    public val data: List<Trace> by config.list(Trace)

    /**
     * Layout specification for th plot
     */
    public val layout: Layout by config.spec(Layout)

    public fun addTrace(trace: Trace) {
        val traceRoot = trace.rootNode
        if (traceRoot is Config) {
            config.append("data", traceRoot)
        } else {
            val traceConfig = Config()
            trace.rootNode?.let { traceConfig.update(it) }
            trace.retarget(traceConfig)
            config.append("data", traceConfig)
        }
    }

    /**
     * Append all traces from [traces] to the plot
     */
    public fun traces(traces: Collection<Trace>) {
        traces.forEach { addTrace(it) }
    }

    /**
     * Append all [traces]
     */
    public fun traces(vararg traces: Trace) {
        traces.forEach { addTrace(it) }
    }

    /**
     * Remove a trace with a given [index] from the plot
     */
    @UnstablePlotlyAPI
    internal fun removeTrace(index: Int) {
        config.remove("data[$index]")
    }

    override fun toMeta(): Meta = config

    override fun toString(): String = "Plot[${hashCode().toUInt()}"
}

private fun Plot.toJson(): JsonObject = buildJsonObject {
    layout.rootNode?.let { put("layout", it.toJson()) }
    put("data", buildJsonArray {
        data.forEach { traceData ->
            traceData.rootNode?.let { add(it.toJson()) }
        }
    })
}

/**
 * Convert a plot to Json representation specified by Plotly `newPlot` command.
 */
public fun Plot.toJsonString(): String = toJson().toString()

/**
 * Configure the layout
 */
public inline fun Plot.layout(block: Layout.() -> Unit) {
    layout.invoke(block)
}

/**
 * Add a generic trace
 */
public inline fun Plot.trace(block: Trace.() -> Unit): Trace {
    val trace = Trace(block)
    traces(trace)
    return trace
}