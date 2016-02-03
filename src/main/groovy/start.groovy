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
                    res2.cause().printStackTrace()
                }
            }
            vertx.setTimer 500, {
                def msg = [
                    email: 'email@test.de',
                    password: 'secret123',
                    passwordConfirm: 'secret123',
                    permissions: [ 'admin' ]
                ]
                vertx.eventBus().send('registration.register', msg, { res2 ->
                    if (res2.succeeded()) {
                        def reply = res2.result().body()
                        println 'REG_REPLY ' + reply
                        def token = reply.token
                        def confirmMsg = [ token: token ]
                        vertx.eventBus().send('registration.confirm', confirmMsg, { res3 ->
                            println 'CONF_REPLY ' + res3.result().body()
                            def loginMsg = [ email: msg.email, password: msg.password ]
                            vertx.eventBus().send('registration.login', loginMsg, { res4 ->
                                println 'LOGIN_REPLY ' + res4.result().body()
                                token = res4.result().body()
                            })
                        })
                    } else {
                        res2.cause().printStackTrace()
                    }
                })
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
