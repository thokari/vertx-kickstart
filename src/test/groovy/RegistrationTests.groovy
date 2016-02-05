
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.ext.unit.TestContext
import io.vertx.ext.unit.junit.RunTestOnContext
import io.vertx.groovy.ext.unit.junit.VertxUnitRunner
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import java.sql.*
import static org.junit.Assert.*

@RunWith(VertxUnitRunner.class)
public class RegistrationTestSuite {

    @Rule
    public RunTestOnContext rule = new RunTestOnContext()

    @Before
    public void before(TestContext context) {
        Vertx vertx = new Vertx(rule.vertx())
        def config = [
            websiteUrl: 'http://localhost:8080',
            dbName: 'vertx_test',
            emailServer: [
                hostname: 'localhost',
                port: 1025
            ],
            jwt: [
                keyStore: [
                    type: 'jceks',
                    path: 'keystore.jceks',
                    password: 'password'
                ]
            ],
            emailExpiresInMinutes: 1440,
            loginExpiresInMinutes: 30
        ]
        Connection c = DriverManager.getConnection('jdbc:postgresql://localhost:5432/vertx_test')
        c.createStatement().executeUpdate('DROP TABLE IF EXISTS registration')
        c.createStatement().executeUpdate("""
        CREATE TABLE registration (
            email VARCHAR(256) NOT NULL PRIMARY KEY,
            email_confirmed BOOLEAN,
            permissions VARCHAR(64),
            password VARCHAR(256),
            created DATE
        );""")

        vertx.deployVerticle('groovy:registration', [ config: config ], { res ->
            context.async().complete()
        })
        context.async().await()
    }

    @After
    public void after(TestContext context) {
        rule.vertx.close(context.async().complete())
    }

    @Test
    public void testRegisterCanSucceed(TestContext context) {
        Vertx vertx = new Vertx(rule.vertx())
        def msg = [
            email: 'email@test.de',
            password: 'password',
            passwordConfirm: 'password',
            roles: ['admin'],
        ]
        vertx.setTimer(500, { res1 ->
            vertx.eventBus().send('registration.register', msg, { res2 ->
                if (res2.succeeded()) {
                    context.async().complete()
                } else {
                    println res2.cause()
                }
            })
        })
    }
}
