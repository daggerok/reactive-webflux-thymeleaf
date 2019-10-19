package com.example.reactivewebfluxthymeleaf

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.reactive.result.view.Rendering
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import org.thymeleaf.spring5.context.webflux.ReactiveDataDriverContextVariable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.*

data class Message(val body: String? = "",
                   val at: Instant = Instant.now(),
                   val id: UUID? = UUID.randomUUID())

@Repository
class MessageRepository(private val log: Logger = LogManager.getLogger(),
                        private val db: MutableList<Message> = mutableListOf()) {

    fun save(body: String) = save(Message(body))

    fun save(message: Message): Mono<Message> {
        log.info("save: {}", message)
        db.add(message)
        return Mono.just(message)
    }

    fun findAll() = Flux.fromIterable(db)
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApplicationWebExceptionHandler : WebExceptionHandler {

    private val log = LogManager.getLogger()

    override fun handle(exchange: ServerWebExchange, e: Throwable): Mono<Void> {
        log.info("handling {} error", e.localizedMessage)
        val redirect = "/err?details=" + e.localizedMessage.toString()
                .replace("[^a-zA-Z0-9_\\-\\\\.]".toRegex(), "_")
        val response = exchange.response
        response.statusCode = HttpStatus.PERMANENT_REDIRECT
        response.headers[HttpHeaders.LOCATION] = redirect
        return Mono.empty()
    }
}

@Configuration
class SharedConfig(private val messageRepository: MessageRepository) {
    @Bean fun sharedEventStream() = messageRepository.findAll().share()
}

@Controller
@RequestMapping("/messages")
class MessagesPage(private val sharedEventStream: Flux<Message>) {

    private val log = LogManager.getLogger()

    @GetMapping("", "/")
    fun index(): Rendering {
        val interval = Flux.interval(Duration.ofMillis(99))
        val zip = Flux.zip(sharedEventStream, interval).map { it.t1 }
        val messages = ReactiveDataDriverContextVariable(zip, 1)
        log.info("handling messages")
        if (System.currentTimeMillis().toInt() % 6 == 5) // denied symbols: |, \ ie: "\\"
            throw RuntimeException("oops ' ololo \" .. ,/[] | !@#$%^&*())_+")
        return Rendering.view("messages")
                .modelAttribute("messages", messages)
                .build()
    }
}

@Controller
class IndexPage(private val sharedEventStream: Flux<Message>,
                private val messageRepository: MessageRepository) {

    private val log = LogManager.getLogger()

    @PostMapping("/")
    fun sendMessageRedirect(@ModelAttribute("message") message: Message): Rendering {
        val payload = message.body ?: ""
        if (payload.isNotBlank()) {
            log.info("posting message: {}", message)
            messageRepository.save(payload)
        }
        return Rendering.view("redirect:/").build()
    }

    @GetMapping("", "/", "/err")
    fun get(@ModelAttribute("message") message: Message): Rendering {
        log.info("rendering messages")
        val data = sharedEventStream.sort { m1, m2 -> -m1.at.compareTo(m2.at) }
        val messages = ReactiveDataDriverContextVariable(data, 1)
        if (System.currentTimeMillis().toInt() % 6 == 5) // denied symbols: |, \ ie: "\\"
            throw RuntimeException("oops ' ololo \" .. ,/[] | !@#$%^&*())_+")
        return Rendering.view("index")
                // .modelAttribute("messages", zip)
                .modelAttribute("messages", messages)
                .build()
    }
}

@SpringBootApplication
class ReactiveWebfluxThymeleafApplication

fun main(args: Array<String>) {
    runApplication<ReactiveWebfluxThymeleafApplication>(*args)
}
