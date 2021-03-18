package space.kscience.plotly.fx

import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.SelectionMode
import tornadofx.*

class PlotlyApp : App(PlotlyView::class)

fun main(args: Array<String>) {
    launch<PlotlyApp>(args)
}

class PlotlyView : View("Hello PlotlyFX") {
    private val controller: PlotlyFXController by inject()
    private val hide = SimpleBooleanProperty(false)
    override val root = hbox {
        listview(controller.pages) {
            selectionModel.selectionMode = SelectionMode.SINGLE
            selectionModel.selectedItemProperty().addListener(
                ChangeListener { _, _, value ->
                    hide.value = value != "Dynamic"
                    controller.displayPage(value)
                }
            )
        }
        vbox {
            stackpane {
                webview {
                    engine.isJavaScriptEnabled = true
                    engine.loadWorker.stateProperty().onChange {
                        log.info("WebEngine worker state: $it")
                    }

                    engine.loadWorker.exceptionProperty().onChange {
                        log.severe(it?.stackTraceToString())
                    }

                    engine.loadWorker.workDoneProperty().onChange {
                        log.info("Work done: $it")
                    }

                    engine.setOnError {
                        log.warning(it.message)
                    }

                    engine.setOnAlert {
                        log.info(it.data)
                    }

                    controller.address.onChange {
                        if (it != null) {
                            log.info("Displaying $it")
                            engine.load(it.replace("localhost", "127.0.0.1"))
                        }
                    }
                }
                progressindicator {
                    hiddenWhen(controller.address.isNotEmpty)
                }
            }
            slider(1.0, 100.0, 1.0) {
                hiddenWhen(hide)
                isShowTickLabels = true
                isShowTickMarks = true
                controller.scale.bind(valueProperty())
            }
        }
    }

    init {
        controller.startServer()
    }
}