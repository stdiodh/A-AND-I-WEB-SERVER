package com.example.aandi_post_web_server.assignment.application.activation

import com.example.aandi_post_web_server.common.error.v2.AssignmentDeactivatedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AssignmentActivationGateFilter(
    private val activationService: AssignmentActivationService,
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!AssignmentApiPathMatcher.isGated(path)) {
            return chain.filter(exchange)
        }

        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication?.let(::hasAdminRole) ?: false }
            .defaultIfEmpty(false)
            .flatMap { isAdmin ->
                if (isAdmin) {
                    chain.filter(exchange)
                } else {
                    activationService.isActive()
                        .flatMap { active ->
                            if (active) {
                                chain.filter(exchange)
                            } else {
                                Mono.error(AssignmentDeactivatedException())
                            }
                        }
                }
            }
    }

    private fun hasAdminRole(authentication: Authentication): Boolean =
        authentication.authorities?.any { it.authority == ROLE_ADMIN } == true

    companion object {
        private const val ROLE_ADMIN: String = "ROLE_ADMIN"
    }
}
