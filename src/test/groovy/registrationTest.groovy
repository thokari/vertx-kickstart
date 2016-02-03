// TODO make work as in setup classpath correctly
/*
import io.vertx.groovy.ext.unit.TestSuite

def suite = TestSuite.create('registrationTest')

suite.beforeEach({ context ->
    def appConfig = vertx.currentContext().config()
    def async = context.async()
    println "Using configuration: $appConfig"

    vertx.deployVerticle 'groovy:registration', [ config: appConfig.registration ], { res ->
        if (!res.succeeded()) {
            res.cause().printStackTrace()
        }
        async.complete()
    }
})

suite.test('sends token and confirms it', { context ->
    def async = context.async()
    def msg = [
        email: 'some@test.de',
        password: 'secret',
        passwordConfirm: 'secret'
    ]
    vertx.setTimer(1000, {
        vertx.eventBus().send('registration.register', msg, { res ->
            if (res.succeeded()) {
                println res.result()
            } else {
                println res.cause()
            }
            async.complete()
        })
    })
})

suite.run()
*/
