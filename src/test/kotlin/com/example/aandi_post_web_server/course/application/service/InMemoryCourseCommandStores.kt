package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentStore
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserQueryPort
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserReference
import com.example.aandi_post_web_server.course.application.port.CourseStore
import com.example.aandi_post_web_server.course.application.port.CourseWeekStore
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate

internal class InMemoryCourseStore(vararg initial: Course) : CourseStore {
    val courses = linkedMapOf<String, Course>().apply { initial.forEach { put(requireNotNull(it.id), it) } }
    val saved = mutableListOf<Course>()
    private var sequence = 0

    override fun findAllNewestFirst(): Flux<Course> = Flux.fromIterable(courses.values.sortedByDescending { it.createdAt })

    override fun findAllByIds(courseIds: Collection<String>): Flux<Course> =
        Flux.fromIterable(courses.values.filter { it.id in courseIds })

    override fun findById(courseId: String): Mono<Course> = Mono.justOrEmpty(courses[courseId])

    override fun findBySlug(slug: String): Mono<Course> = Mono.justOrEmpty(courses.values.firstOrNull { it.slug == slug })

    override fun existsBySlug(slug: String): Mono<Boolean> = Mono.just(courses.values.any { it.slug == slug })

    override fun save(course: Course): Mono<Course> = Mono.fromCallable {
        val persisted = if (course.id == null) course.copy(id = "generated-${++sequence}") else course
        courses[requireNotNull(persisted.id)] = persisted
        saved += persisted
        persisted
    }

    override fun deleteById(courseId: String): Mono<Void> = Mono.fromRunnable { courses.remove(courseId) }
}

internal class InMemoryCourseEnrollmentStore(vararg initial: CourseEnrollment) : CourseEnrollmentStore {
    val enrollments = initial.toMutableList()
    val saved = mutableListOf<CourseEnrollment>()
    val lookups = mutableListOf<Pair<String, String>>()

    override fun findByCourseIdAndUserId(courseId: String, userId: String): Mono<CourseEnrollment> {
        lookups += courseId to userId
        return Mono.justOrEmpty(enrollments.firstOrNull { it.courseId == courseId && it.userId == userId })
    }

    override fun findAllByCourseId(courseId: String): Flux<CourseEnrollment> =
        Flux.fromIterable(enrollments.filter { it.courseId == courseId })

    override fun findAllEnabledByUserId(userId: String): Flux<CourseEnrollment> =
        Flux.fromIterable(enrollments.filter { it.userId == userId })

    override fun save(enrollment: CourseEnrollment): Mono<CourseEnrollment> = Mono.fromCallable {
        enrollments.removeIf { it.courseId == enrollment.courseId && it.userId == enrollment.userId }
        enrollments += enrollment
        saved += enrollment
        enrollment
    }

    override fun delete(enrollment: CourseEnrollment): Mono<Void> = Mono.fromRunnable { enrollments.remove(enrollment) }

    override fun deleteAllByCourseId(courseId: String): Mono<Long> = Mono.fromCallable {
        val before = enrollments.size
        enrollments.removeIf { it.courseId == courseId }
        (before - enrollments.size).toLong()
    }
}

internal class InMemoryCourseWeekStore : CourseWeekStore {
    override fun findAllByCourseId(courseId: String): Flux<CourseWeek> = Flux.empty()

    override fun deleteAllByCourseId(courseId: String): Mono<Long> = Mono.just(0L)
}

internal class InMemoryEnrollmentUserQueryPort(vararg users: CourseEnrollmentUserReference) : CourseEnrollmentUserQueryPort {
    private val usersByPublicCode = users.associateBy { it.publicCode }
    val queriedPublicCodes = mutableListOf<String>()

    override fun findByPublicCode(publicCode: String): Mono<CourseEnrollmentUserReference> {
        queriedPublicCodes += publicCode
        return Mono.justOrEmpty(usersByPublicCode[publicCode])
    }
}

internal fun storedCourse(
    id: String? = "course-1",
    slug: String = "back-basic",
    fieldTag: CourseTrack = CourseTrack.SP,
    startDate: LocalDate = LocalDate.of(2026, 3, 2),
    endDate: LocalDate = LocalDate.of(2026, 3, 30),
    status: CourseStatus = CourseStatus.PUBLISHED,
    updatedAt: Instant = Instant.parse("2026-02-01T00:00:00Z"),
): Course = Course(
    id = id,
    slug = slug,
    fieldTag = fieldTag,
    startDate = startDate,
    endDate = endDate,
    metadata = CourseMetadata(
        title = "BACK 기초",
        description = "서버 기초",
        phase = CoursePhase.BASIC,
        attributes = mapOf("level" to 1),
    ),
    status = status,
    createdAt = Instant.parse("2026-02-01T00:00:00Z"),
    updatedAt = updatedAt,
)
