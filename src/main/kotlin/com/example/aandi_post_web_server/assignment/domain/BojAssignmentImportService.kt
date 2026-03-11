package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.AssignmentImportSourcePayload
import com.example.aandi_post_web_server.assignment.enum.AssignmentSourcePlatform
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class BojAssignmentImportService(
    private val webClientBuilder: WebClient.Builder,
    private val bojProblemHtmlParser: BojProblemHtmlParser,
) : AssignmentImportService {

    override fun import(source: AssignmentImportSourcePayload): Mono<ImportedAssignmentContent> {
        if (source.platform != AssignmentSourcePlatform.BOJ) {
            return Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 문제 출처입니다: ${source.platform}"))
        }

        val url = "https://www.acmicpc.net/problem/${source.problemId}"
        return webClientBuilder.build()
            .get()
            .uri(url)
            .header(HttpHeaders.USER_AGENT, "AANDI-Report-Server/1.0")
            .header(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en;q=0.8")
            .retrieve()
            .bodyToMono(String::class.java)
            .map(bojProblemHtmlParser::parse)
            .flatMap { imported ->
                if (imported.title.isBlank() || imported.description.isBlank()) {
                    Mono.error(ResponseStatusException(HttpStatus.BAD_REQUEST, "BOJ 문제 파싱에 실패했습니다: ${source.problemId}"))
                } else {
                    Mono.just(imported)
                }
            }
            .onErrorMap(WebClientResponseException.NotFound::class.java) {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 BOJ 문제입니다: ${source.problemId}")
            }
            .onErrorMap(WebClientResponseException::class.java) {
                ResponseStatusException(HttpStatus.BAD_REQUEST, "BOJ 문제를 불러오지 못했습니다: ${source.problemId}")
            }
    }
}
