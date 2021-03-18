package space.kscience.plotly.server

internal object PlotlyServerConstants {
    public const val OUTPUT_CLASS: String = "plotly-output"

    public const val PLOTLY_DATA_CLASS: String = "plotly-output-data"
    public const val PLOTLY_CONFIG_CLASS: String = "plotly-output-config"

    public const val OUTPUT_DATA_ATTRIBUTE: String = "data-output-fetch"
    public const val OUTPUT_PUSH_ATTRIBUTE: String = "data-output-ws"

    public const val OUTPUT_NAME_ATTRIBUTE: String = "data-output-name"
    public const val OUTPUT_ENDPOINT_ATTRIBUTE: String = "data-output-endpoint"
}