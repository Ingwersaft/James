[![Release](https://jitpack.io/v/Ingwersaft/James.svg?style=flat-square)](https://jitpack.io/#Ingwersaft/James)

# James
Micro chat framework for Kotlin.

Currently supports Rocket.Chat and Telegram as targets.

## Features
 * Rocket.Chat via meteor websocket api
 * Telegram
 * minimal DSL
 * conversation support
 * automatic mapping overview, mapped with `help`
 * mapping prefix, so you bot has a name
 * conversation support with retries
 
## Usage
With...
```kotlin
fun main(args: Array<String>) {
    james {
        rocketchat {
                        websocketTarget = "wss://example.org/websocket"
                        username = "example_bot"
                        password = "secret_password"
                        sslVerifyHostname = false
                        ignoreInvalidCa = true
        }    
        name = "felix"
        map("question", "i'll ask you for some text") {
            val answer = ask("please provide some text:")
            send("received: $answer")
        }
        map("dadjoke", "i'll send you a dadjoke") {
            send("Someone broke into my house last night and stole my limbo trophy. How low can you go?")
        }
    }
}
```
... you get:

![example](readmefiles/rocketchatexample.png)

**Take a look at the Mapping.kt source for all possibilities**

## Target chat specifics
the `send` and `ask` methods support optional options:
```kotlin
// Mapping:
fun send(text: String, options: Map<String, String> = emptyMap())
fun ask(text: String, options: Map<String, String> = emptyMap())
```

### Rocket.Chat
Change avatar (default `:tophat:`):
```kotlin
options.put("avatar",":alien:")
```

### Telegram
*No special options yet*

general stuff:
 * [How to get a telegram bot](http://not.found.org) 
 * [What can my bot see, what not](http://not.found.org)
 
Commands:
Every String with a leading `/` is clickable inside telegram. This can be used in your mapping string, but also if you ask
a user some question, you can provide possible, clickable, values.
 
## How to get James
James is published at [JitPack](https://jitpack.io/#Ingwersaft/James).
### Gradle
```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
...
dependencies {
    compile 'com.github.Ingwersaft:James:<Tag>'
}
```
### Maven
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
...
<dependency>
    <groupId>com.github.Ingwersaft</groupId>
    <artifactId>James</artifactId>
    <version>#TAG#</version>
</dependency>
```

### Javadoc
Jitpack also provides javadoc web publishing. Use the URL
`https://jitpack.io/com/github/Ingwersaft/James/<VERSION>/javadoc/`
(e.g.: [master-SNAPSHOT](https://jitpack.io/com/github/Ingwersaft/James/master-SNAPSHOT/javadoc/))
to access the javadoc for the given version.

## Known Problems
 * all chats don't retry calls in case of errors
