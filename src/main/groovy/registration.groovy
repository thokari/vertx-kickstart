import io.vertx.groovy.ext.auth.jwt.JWTAuth
import io.vertx.groovy.ext.jdbc.JDBCClient
import io.vertx.groovy.ext.mail.MailClient
import io.vertx.core.Future
import org.mindrot.jbcrypt.BCrypt

def appConfig = vertx.currentContext().config()
def jdbcConfig = [
    url: "jdbc:postgresql:${appConfig.dbName}",
    driver_class: 'org.postgresql.Driver'
]
def authProvider = JWTAuth.create(vertx, appConfig.jwt)
def dbClient = JDBCClient.createShared(vertx, jdbcConfig)
def mailClient = MailClient.createShared(vertx, appConfig.emailServer, 'MailClient')
def eb = vertx.eventBus()

eb.consumer 'registration.register', { msg ->
    def email = msg.body().email
    def password = msg.body().password
    def passwordConfirm = msg.body().passwordConfirm
    def permissions = msg.body().permissions
    if (!email.contains('@')) {
        replyError msg, 'Invalid email address'
    } else if (password.size() < 8) {
        replyError msg, 'Password too short'
    } else if (!(password.equals(passwordConfirm))) {
        replyError msg, 'Passwort not repeated correctly'
    } else {
        def token = generateEmailToken(authProvider, email)
        saveRegistration(dbClient, email, password, permissions, { res1 ->
            if (res1.succeeded()) {
                sendEmail(mailClient, email, token, appConfig.websiteUrl, { res2 ->
                    if (res2.succeeded()) {
                        replySuccess msg, [ email: email, token: token ]
                    } else {
                        replyError msg, res2.cause().message
                    }
                })
            } else {
                replyError msg, res1.cause().message
            }
        })
    }
}

eb.consumer 'registration.confirm', { msg ->
    def token = msg.body().token
    authProvider.authenticate([ jwt: token ], { authRes ->
        if (authRes.succeeded()) {
            def user = authRes.result()
            def payload = user.principal()
            def email = payload.email
            confirmEmail(dbClient, email, { confRes ->
                if (confRes.succeeded()) {
                    replySuccess msg, null
                } else {
                    replyError msg, confRes.cause().message
                }
            })
        } else {
            def errorMsg = authRes.cause().message ?: "Could not validate token '$token'"
            replyError msg, errorMsg
        }
    })
}

eb.consumer 'registration.login', { msg ->
    def email = msg.body().email
    def candidate = msg.body().password
    comparePasswordAndGetPermissions (dbClient, email, candidate, { res1 ->
        if (res1.succeeded()) {
            def loginResult = res1.result()
            def token = generateLoginToken(authProvider, email, loginResult)
            replySuccess msg, [ token: token ]
        } else {
            replyError msg, res1.cause().message
        }
    })
}

def sendEmail (mailClient, email, token, websiteUrl, cb) {
    def target = "$websiteUrl/confirm_email/$token"
    def html = """
Follow <a href="$target" target="_blank">this link</a> to confirm your email address.
"""
    def mail = [ from: 'registration@thokari.de', to: email, html: html ]
    println "Sending email to $email"
    mailClient.sendMail(mail, cb)
}

def generateEmailToken (authProvider, email) {
    def payload = [ email: email ]
    def expiresInMinutes = vertx.currentContext().config().emailExpiresInMinutes
    def options = [ expiresInMinutes: expiresInMinutes ]
    authProvider.generateToken payload, options
}

def generateLoginToken (authProvider, email, loginResult) {
    // list of standard jwt claims: http://www.iana.org/assignments/jwt/jwt.xhtml
    def payload = [ email: email, email_verified: loginResult.emailConfirmed ]
    def expiresInMinutes = vertx.currentContext().config().loginExpiresInMinutes
    def options = [ permissions: loginResult.permissions, expiresInMinutes: expiresInMinutes ]
    authProvider.generateToken payload, options
}

def saveRegistration (dbClient, email, password, permissions, cb) {
    withConnection(dbClient, { conn ->
        vertx.executeBlocking({ future ->
            // this might actually take some time if using more then default of 10 rounds of hashing
            // better not block the event loop
            future.complete(BCrypt.hashpw(password, BCrypt.gensalt()))
        }, { res ->
            if (res.succeeded()) {
                def hash = res.result()
                java.sql.Date sqlDate = new java.sql.Date(new java.util.Date().time)
                def params = [ email, false, hash, permissions.join(','), sqlDate ]
                // TODO use array
                def query = 'INSERT INTO registration (email, email_confirmed, password, permissions, created) VALUES (?, ?, ?, ?, ?)'
                conn.updateWithParams(query, params, cb)
            } else {
                cb(res)
            }
        })
    })
}

def confirmEmail (dbClient, email, cb) {
    withConnection(dbClient, { conn ->
        conn.queryWithParams('SELECT email_confirmed FROM registration WHERE email = ?', [ email ], { res1 ->
            if (res1.succeeded()) {
                def emailConfirmed = res1.result().results[0][0]
                if (emailConfirmed == true) {
                    cb(Future.failedFuture('Email address already confirmed'))
                } else {
                    def query = 'UPDATE registration SET email_confirmed = ? WHERE email = ?'
                    conn.updateWithParams(query, [ true, email ], cb)
                }
            } else {
                cb(res1)
            }
        })
    })
}

def comparePasswordAndGetPermissions (dbClient, email, candidate, cb) {
    withConnection(dbClient, { conn ->
        def query = 'SELECT password, permissions, email_confirmed FROM registration WHERE email = ?'
        conn.queryWithParams(query, [ email ], { res1 ->
            if (res1.succeeded()) {
                if (res1.result().numRows < 1) {
                    cb(Future.failedFuture('Credentials do not match'))
                }
                def row = res1.result().rows[0]
                def hashed = row.password
                def permissions = row.permissions.split(',').toList() // String[] != List == JsonArray
                def emailConfirmed = row.email_confirmed
                if (BCrypt.checkpw(candidate, hashed)) {
                    def loginResult = [
                        permissions: permissions,
                        emailConfirmed: emailConfirmed
                    ]
                    cb(Future.succeededFuture(loginResult))
                } else {
                    cb(Future.failedFuture('Credentials do not match'))
                }
            } else {
                cb(res1)
            }
        })
    })
}

def replyError (msg, errorMsg) {
    msg.reply([
        status: 'error',
        message: errorMsg
    ])
}

def replySuccess (msg, content) {
    def body = [ status: 'ok' ]
    if (content) {
        body += content // just add maps / json objects to merge them... <3 groovy!
    }
    msg.reply(body)
}

def withConnection (dbClient, closure) {
    dbClient.getConnection({ res ->
        if (res.succeeded()) {
            closure.call(res.result())
        } else {
            throw res.cause()
        }
    })
}
