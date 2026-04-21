package com.example.aandi_post_web_server.assignment.infrastructure.submission.event

import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
@ConditionalOnProperty(prefix = "app.events.judge-submission", name = ["enabled"], havingValue = "true")
class SqsJudgeSubmissionEventConsumer(
    private val properties: JudgeSubmissionEventProperties,
    @Qualifier("judgeSubmissionSqsAsyncClient")
    private val sqsAsyncClient: SqsAsyncClient,
    private val judgeCompletedEventParser: JudgeCompletedEventParser,
    private val projectionService: AssignmentSubmissionStatusProjectionService,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(SqsJudgeSubmissionEventConsumer::class.java)
    private val running = AtomicBoolean(false)
    private var pollingSubscription: Disposable? = null

    override fun start() {
        if (!properties.enabled || !running.compareAndSet(false, true)) {
            return
        }

        require(properties.queueUrl.isNotBlank()) {
            "REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL must not be blank when REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED=true"
        }
        require(properties.region.isNotBlank()) {
            "AWS_REGION must not be blank when REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED=true"
        }

        pollingSubscription = Mono.defer { pollBatch().then() }
            .repeatWhen { repeatSignal -> repeatSignal.delayElements(properties.pollDelay) }
            .retryWhen(
                Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(30))
                    .doBeforeRetry { signal ->
                        log.warn(
                            "judge-submission SQS polling failed; retrying consumer loop (attempt={})",
                            signal.totalRetries() + 1,
                            signal.failure(),
                        )
                    }
            )
            .subscribe(
                {},
                { ex ->
                    running.set(false)
                    log.error("judge-submission SQS consumer terminated unexpectedly", ex)
                }
            )

        log.info(
            "judge-submission SQS consumer started: region={}, queueUrl={}, waitTimeSeconds={}, maxNumberOfMessages={}, visibilityTimeoutSeconds={}, pollDelay={}",
            properties.region,
            properties.queueUrl,
            properties.waitTimeSeconds,
            properties.maxNumberOfMessages,
            properties.visibilityTimeoutSeconds,
            properties.pollDelay,
        )
    }

    override fun stop() {
        pollingSubscription?.dispose()
        pollingSubscription = null
        if (running.compareAndSet(true, false)) {
            log.info("judge-submission SQS consumer stopped")
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
                            "failed to process judge-submission message: messageId={}, queueUrl={}",
                            message.messageId(),
                            properties.queueUrl,
                            ex,
                        )
                        Mono.empty()
                    }
            }

    internal fun processMessage(message: Message): Mono<Void> {
        log.info(
            "judge-submission message received: messageId={}, queueUrl={}",
            message.messageId(),
            properties.queueUrl,
        )

        return when (val parseResult = judgeCompletedEventParser.parse(message.body())) {
            is JudgeCompletedEventParseResult.Ignored -> {
                log.warn(
                    "judge-submission message skipped: messageId={}, reason={}, queueUrl={}",
                    message.messageId(),
                    parseResult.reason,
                    properties.queueUrl,
                )
                deleteMessage(message.receiptHandle(), message.messageId())
            }
            is JudgeCompletedEventParseResult.Parsed -> {
                projectionService.upsert(parseResult.event)
                    .doOnNext { projection ->
                        log.info(
                            "judge-submission projection upserted: messageId={}, assignmentId={}, publicCode={}, lastEventTimestamp={}",
                            message.messageId(),
                            projection.assignmentId,
                            projection.publicCode,
                            projection.lastEventTimestamp,
                        )
                    }
                    .flatMap { deleteMessage(message.receiptHandle(), message.messageId()) }
            }
        }
    }

    private fun receiveMessages(): Mono<List<Message>> {
        val requestBuilder = ReceiveMessageRequest.builder()
            .queueUrl(properties.queueUrl)
            .waitTimeSeconds(properties.waitTimeSeconds)
            .maxNumberOfMessages(properties.maxNumberOfMessages)
            .visibilityTimeout(properties.visibilityTimeoutSeconds)

        return Mono.fromFuture(sqsAsyncClient.receiveMessage(requestBuilder.build()))
            .map { response -> response.messages() }
            .doOnNext { messages ->
                if (messages.isNotEmpty()) {
                    log.info(
                        "judge-submission messages received: queueUrl={}, count={}",
                        properties.queueUrl,
                        messages.size,
                    )
                }
            }
    }

    private fun deleteMessage(
        receiptHandle: String,
        messageId: String,
    ): Mono<Void> {
        val request = DeleteMessageRequest.builder()
            .queueUrl(properties.queueUrl)
            .receiptHandle(receiptHandle)
            .build()
        return Mono.fromFuture(sqsAsyncClient.deleteMessage(request))
            .doOnSuccess {
                log.debug(
                    "judge-submission deleteMessage succeeded: messageId={}, queueUrl={}",
                    messageId,
                    properties.queueUrl,
                )
            }
            .doOnError { ex ->
                log.warn(
                    "judge-submission deleteMessage failed: messageId={}, queueUrl={}",
                    messageId,
                    properties.queueUrl,
                    ex,
                )
            }
            .then()
    }
}
