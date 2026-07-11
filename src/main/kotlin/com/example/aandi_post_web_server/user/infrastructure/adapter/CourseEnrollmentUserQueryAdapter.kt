package com.example.aandi_post_web_server.user.infrastructure.adapter

import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserQueryPort
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserReference
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class CourseEnrollmentUserQueryAdapter(
    private val reportUserRepository: ReportUserRepository,
) : CourseEnrollmentUserQueryPort {
    override fun findByPublicCode(publicCode: String): Mono<CourseEnrollmentUserReference> =
        reportUserRepository.findByPublicCodeAndDeletedAtIsNull(publicCode)
            .map { user ->
                CourseEnrollmentUserReference(
                    id = user.id,
                    publicCode = user.publicCode,
                    username = user.username,
                    role = user.role,
                )
            }
}
