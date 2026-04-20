package com.example.aandi_post_web_server.user.infrastructure.event

import com.example.aandi_post_web_server.user.infrastructure.config.UserSyncEventProperties
import com.example.aandi_post_web_server.user.application.service.ReportUserSyncService
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@Component
class SqsUserEventConsumer(
    private val properties: UserSyncEventProperties,
    private val sqsAsyncClient: SqsAsyncClient,
    private val authUserEventParser: AuthUserEventParser,
    private val reportUserSyncService: ReportUserSyncService,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(SqsUserEventConsumer::class.java)
    private val running = AtomicBoolean(false)
    private var pollingSubscription: Disposable? = null

    override fun start() {
        if (!properties.enabled || !running.compareAndSet(false, true)) {
            return
        }

        require(properties.queueUrl.isNotBlank()) {
            "app.events.user-sync.queue-url must not be blank when enabled=true"
        }

        pollingSubscription = Flux.defer { pollBatch() }
            .repeat()
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(30))
                    .doBeforeRetry { signal ->
                        log.warn(
                            "user-sync SQS polling failed; retrying consumer loop (attempt={})",
                            signal.totalRetries() + 1,
                            signal.failure(),
                        )
                    }
            )
            .subscribe(
                {},
                { ex ->
                    running.set(false)
                    log.error("user-sync SQS consumer terminated unexpectedly", ex)
                }
            )

        log.info(
            "user-sync SQS consumer started: queueUrl={}, waitTimeSeconds={}, maxNumberOfMessages={}",
            properties.queueUrl,
            properties.waitTimeSeconds,
            properties.maxNumberOfMessages,
        )
    }

    override fun stop() {
        pollingSubscription?.dispose()
        pollingSubscription = null
        if (running.compareAndSet(true, false)) {
            log.info("user-sync SQS consumer stopped")
        }
    }

    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    override fun isRunning(): Boolean = running.get()

    override fun isAutoStartup(): Boolean = true

    override fun getPhase(): Int = 0

    internal fun pollBatch(): Flux<Void> =
        receiveMessages()
            .flatMapMany { messages -> Flux.fromIterable(messages) }
            .concatMap { message ->
                processMessage(message)
                    .onErrorResume { ex ->
                        log.error(
                            "failed to process user-sync message: messageId={}, queueUrl={}",
                            message.messageId(),
                            properties.queueUrl,
                            ex,
                        )
                        Mono.empty()
                    }
            }

    internal fun processMessage(message: Message): Mono<Void> =
        Mono.fromCallable { authUserEventParser.parse(message.body()) }
            .flatMap { event ->
                reportUserSyncService.sync(event)
                    .doOnNext { outcome ->
                        log.info(
                            "processed user-sync event: messageId={}, eventType={}, userId={}, outcome={}",
                            message.messageId(),
                            event.eventType,
                            event.id,
                            outcome,
                        )
                    }
            }
            .flatMap { deleteMessage(message.receiptHandle()) }

    private fun receiveMessages(): Mono<List<Message>> {
        val request = ReceiveMessageRequest.builder()
            .queueUrl(properties.queueUrl)
            .waitTimeSeconds(properties.waitTimeSeconds)
            .maxNumberOfMessages(properties.maxNumberOfMessages)
            .build()

        return Mono.fromFuture(sqsAsyncClient.receiveMessage(request))
            .map { response -> response.messages() }
    }

    private fun deleteMessage(receiptHandle: String): Mono<Void> {
        val request = DeleteMessageRequest.builder()
            .queueUrl(properties.queueUrl)
            .receiptHandle(receiptHandle)
            .build()
        return Mono.fromFuture(sqsAsyncClient.deleteMessage(request)).then()
    }
}
