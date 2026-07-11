package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCoursePort
import com.example.aandi_post_web_server.assignment.application.port.AssignmentCourseReference
import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.ZoneId

@Component
class AssignmentCourseAdapter(
    private val courseRepository: CourseRepository,
    private val courseWeekRepository: CourseWeekRepository,
) : AssignmentCoursePort {
    override fun findBySlug(slug: CourseSlug): Mono<AssignmentCourseReference> =
        courseRepository.findBySlug(slug.value)
            .map { course -> AssignmentCourseReference(id = course.id, slug = course.slug) }

    override fun findSlugById(courseId: String): Mono<String> =
        courseRepository.findById(courseId)
            .map { course -> course.slug }

    override fun ensureWeekExistsOrCreate(
        courseId: CourseId,
        weekNo: WeekNo,
        startAt: Instant,
        endAt: Instant,
    ): Mono<Void> =
        courseWeekRepository.findByCourseIdAndWeekNo(courseId.value, weekNo.value)
            .switchIfEmpty(
                Mono.defer {
                    courseWeekRepository.save(
                        CourseWeek(
                            courseId = courseId.value,
                            weekNo = weekNo.value,
                            title = "${weekNo.value}주차",
                            startDate = startAt.atZone(SEOUL_ZONE_ID).toLocalDate(),
                            endDate = endAt.atZone(SEOUL_ZONE_ID).toLocalDate(),
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        )
                    )
                        .onErrorResume(DuplicateKeyException::class.java) { error ->
                            courseWeekRepository.findByCourseIdAndWeekNo(courseId.value, weekNo.value)
                                .switchIfEmpty(Mono.error(error))
                        }
                }
            )
            .then()

    private companion object {
        val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
