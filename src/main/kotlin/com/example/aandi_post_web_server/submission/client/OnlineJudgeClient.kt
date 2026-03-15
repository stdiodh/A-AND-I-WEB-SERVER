package com.example.aandi_post_web_server.submission.client

import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionStatus
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionTestCaseStatus
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface OnlineJudgeClient {
    fun createSubmission(
        authorizationHeader: String,
        request: JudgeSubmissionCreateRequest,
    ): Mono<JudgeSubmissionAccepted>

    fun getSubmissionResult(
        authorizationHeader: String,
        submissionId: String,
    ): Mono<JudgeSubmissionResult>

    fun streamSubmissionResult(
        authorizationHeader: String,
        submissionId: String,
    ): Flux<ServerSentEvent<String>>
}

@ConfigurationProperties(prefix = "app.online-judge")
data class OnlineJudgeClientProperties(
    val baseUrl: String = "http://localhost:8080",
)

@Configuration
@EnableConfigurationProperties(OnlineJudgeClientProperties::class)
class OnlineJudgeClientConfig {
    @Bean
    fun onlineJudgeWebClient(properties: OnlineJudgeClientProperties): WebClient =
        WebClient.builder()
            .baseUrl(properties.baseUrl)
            .build()
}

@Component
class WebClientOnlineJudgeClient(
    private val onlineJudgeWebClient: WebClient,
) : OnlineJudgeClient {
    override fun createSubmission(
        authorizationHeader: String,
        request: JudgeSubmissionCreateRequest,
    ): Mono<JudgeSubmissionAccepted> {
        return onlineJudgeWebClient.post()
            .uri("/v1/submissions")
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
            .bodyValue(request)
            .exchangeToMono { response ->
                when {
                    response.statusCode().is2xxSuccessful ->
                        response.bodyToMono(JudgeSubmissionAccepted::class.java)

                    response.statusCode().is4xxClientError ->
                        response.bodyToMono(String::class.java)
                            .defaultIfEmpty("")
                            .flatMap {
                                Mono.error(
                                    ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "채점 서버가 제출을 접수하지 못했습니다.",
                                    )
                                )
                            }

                    else ->
                        response.bodyToMono(String::class.java)
                            .defaultIfEmpty("")
                            .flatMap {
                                Mono.error(
                                    ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "채점 서버에 제출 요청을 처리할 수 없습니다.",
                                    )
                                )
                            }
                }
            }
    }

    override fun getSubmissionResult(
        authorizationHeader: String,
        submissionId: String,
    ): Mono<JudgeSubmissionResult> {
        return onlineJudgeWebClient.get()
            .uri("/v1/submissions/{submissionId}", submissionId)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
            .exchangeToMono { response ->
                when {
                    response.statusCode().is2xxSuccessful ->
                        response.bodyToMono(JudgeSubmissionResult::class.java)

                    response.statusCode() == HttpStatus.NOT_FOUND ->
                        Mono.empty()

                    else ->
                        response.bodyToMono(String::class.java)
                            .defaultIfEmpty("")
                            .flatMap {
                                Mono.error(
                                    ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "채점 서버에서 제출 결과를 조회할 수 없습니다.",
                                    )
                                )
                            }
                }
            }
    }

    override fun streamSubmissionResult(
        authorizationHeader: String,
        submissionId: String,
    ): Flux<ServerSentEvent<String>> {
        val eventType = object : ParameterizedTypeReference<ServerSentEvent<String>>() {}
        return onlineJudgeWebClient.get()
            .uri("/v1/submissions/{submissionId}/stream", submissionId)
            .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
            .retrieve()
            .onStatus(HttpStatusCode::isError) {
                Mono.error(ResponseStatusException(HttpStatus.BAD_GATEWAY, "채점 서버 스트림에 연결할 수 없습니다."))
            }
            .bodyToFlux(eventType)
    }
}

data class JudgeSubmissionCreateRequest(
    val problemId: String,
    val language: AssignmentSubmissionLanguage,
    val code: String,
    val options: JudgeSubmissionOptions = JudgeSubmissionOptions(),
)

data class JudgeSubmissionOptions(
    val realtimeFeedback: Boolean = true,
)

data class JudgeSubmissionAccepted(
    val submissionId: String,
    val streamUrl: String,
)

data class JudgeSubmissionResult(
    val submissionId: String,
    val status: AssignmentSubmissionStatus,
    val testCases: List<JudgeSubmissionTestCaseResult> = emptyList(),
)

data class JudgeSubmissionTestCaseResult(
    val caseId: Int,
    val status: AssignmentSubmissionTestCaseStatus,
    val timeMs: Double = 0.0,
    val memoryMb: Double = 0.0,
    val output: String? = null,
    val error: String? = null,
)
