# vertx-kickstart

This project brings together several of the official  [vertx-examples](https://github.com/vert-x3/vertx-examples) and provides a potential starting point for new applications. It is also supposed to showcase JWT authentication and basic development tooling, like reloading on code changes and continuous testing. Reduction of the overall amount of code or configuration was also a consideration.

It implements a basic registration story:
 - User enter an email address, password (and desired permissions)
 - User receives an email, to which a confirmation is being sent.
 - User can log in, and access HTTP resources with varying permissions

## Setup
1. Install the PostgreSQL database service for your operating system
2. Create database 'vertx'
3. Install a development email client (I recommend [MailDev](http://djfarrelly.github.io/MailDev/))
4. Execute `./gradlew flywayMigrate`
5. Execute `./gradlew run`
6. Open [http://localhost:8080/register.html]()
7. Submit registration, optionally provide (comma separated) roles
8. (Optionally) open email client (in case of MailDev [http://localhost:1080]())
9. (Optionally confirm your email address)
10. Go to [http://localhost:8080](), log in and access the resources.


## Tests

Run the tests with `./gradlew test --continuous` to have the rerun on any change to source files 
