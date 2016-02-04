import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.groovy.ext.auth.jwt.JWTAuth
import io.vertx.groovy.ext.web.handler.JWTAuthHandler
import io.vertx.groovy.ext.web.handler.StaticHandler

def appConfig = vertx.currentContext().config()
def jwtAuth = JWTAuth.create(vertx, appConfig.jwt)
def router = Router.router(vertx)
def eb = vertx.eventBus()

// parse request body
router.route('/api/*').handler(BodyHandler.create().setUploadsDirectory('/tmp/vertx-file-uploads'))

// this route is excluded from the auth handler (it represents your login endpoint)
router.post('/api/register').handler({ ctx ->
    def body = ctx.getBodyAsJson()
    def msg = [
        email: body.email,
        password: body.password,
        passwordConfirm: body.passwordConfirm,
        permissions: body.permissions
    ]
    ctx.response().putHeader('Content-Type', 'text/plain')
    eb.send('registration.register', msg, { res ->
        if (res.succeeded()) {
            def result = res.result().body()
            if ('ok' == result.status) {
                ctx.response().end('Thank you, please check your email!')
            } else {
                ctx.response().end(result.message)
            }
        } else {
            ctx.response().end(res.cause().message)
        }
    })
})

// this route is excluded from the auth handler (it represents your login endpoint)
router.post('/api/login').handler({ ctx ->
    def params = ctx.getBodyAsJson()
    def msg = [
        email: params.email,
        password: params.password,
    ]
    ctx.response().putHeader('Content-Type', 'text/plain')
    eb.send('registration.login', msg, { res ->
        if (res.succeeded()) {
            ctx.response().end(res.result().body().token)
        } else {
            ctx.response().end(res.cause().message)
        }
    })
})

// this route is also excluded from the auth handler
router.get('/confirm_email/:token').handler({ ctx ->
    def token = ctx.request().getParam('token')
    def msg = [ token: token ]
    eb.send('registration.confirm', msg, { res ->
        if (res.succeeded()) {
            def result = res.result().body()
            if ('ok' == result.status) {
                ctx.response().putHeader('Location', appConfig.websiteUrl)
                ctx.response().setStatusCode(302).end()
            } else {
                ctx.response().putHeader('Content-Type', 'text/plain')
                ctx.response().end(result.message)
            }
        } else {
            ctx.response().putHeader('Content-Type', 'text/plain')
            ctx.response().setStatusCode(500).end(res.cause().message)
        }
    })
})

// protect the API (any authority is allowed)
router.route('/api/protected').handler(JWTAuthHandler.create(jwtAuth))

router.get('/api/protected').handler({ ctx ->
    ctx.response().putHeader('Content-Type', 'text/plain')
    ctx.response().end('This can be accessed by all roles')
})

// protect the route (admin authority is required)
router.route('/api/protected/admin').handler(JWTAuthHandler.create(jwtAuth).addAuthority('admin'))

router.get('/api/protected/admin').handler({ ctx ->
    def payload = ctx.user().principal() // content of the token
    def permissions = payload.permissions
    ctx.response().putHeader('Content-Type', 'text/plain')
    ctx.response().end('This can only be accessed by admins. Your roles are: ' + permissions.join(', '))
})

// protect the route (confirmed email is required)
router.route('/api/protected/email_confirmed').handler(JWTAuthHandler.create(jwtAuth))

// check the token payload manually, in case we want to implement our own rule
router.get('/api/protected/email_confirmed').handler({ ctx ->
    def payload = ctx.user().principal() // content of the token
    def emailConfirmed = payload.email_verified
    ctx.response().putHeader('Content-Type', 'text/plain')
    if (emailConfirmed) {
        ctx.response().end('This can only be accessed if you have confirmed your email address.')
    } else {
        ctx.fail(403)
    }
})

// Serve the non private static pages
router.route().handler(StaticHandler.create().setWebRoot('public'))

vertx.createHttpServer().requestHandler(router.&accept).listen(8080)
