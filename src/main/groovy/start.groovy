import io.vertx.groovy.ext.shell.ShellService
import io.vertx.groovy.core.Future

import static groovy.json.JsonOutput.*

void vertxStart(Future<Void> future) {

    def appConfig = vertx.currentContext().config()
    println "Using configuration: ${prettyPrint(toJson(appConfig))}"

    vertx.deployVerticle 'groovy:registration', [ config: appConfig ], { res1 ->
        if (res1.succeeded()) {
            vertx.deployVerticle 'groovy:httpEndpoint', [ config: appConfig ], { res2 ->
                if (res2.succeeded()) {
                    future.complete()
                } else {
                    future.fail()
                }
            }
        } else {
            res1.cause().printStackTrace()
            future.fail()
        }
    }

    def shellService = ShellService.create vertx, [
        telnetOptions: [
            host: 'localhost',
            port: 4000
        ]
    ]
    shellService.start()
}
