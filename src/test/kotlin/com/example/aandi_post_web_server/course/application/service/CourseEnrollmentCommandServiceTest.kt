package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserQueryPort
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserReference
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseEnrollmentCommandServiceTest : StringSpec({
    "수강 등록 중 사용자 unique 충돌이 발생하면 CONFLICT를 반환한다" {
        val fixture = CourseEnrollmentCommandFixture()
        val duplicate = DuplicateKeyException("course enrollment race")
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val reportUser = enrollmentUser(id = "user-1", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenReturn(Mono.error(duplicate))

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "back-basic",
                request = EnrollCourseRequest(publicCode = "FL301"),
            )
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.CONFLICT
                exception.reason shouldBe "이미 등록된 사용자입니다. courseId=course-1, userId=user-1"
                exception.cause shouldBe duplicate
            }
            .verify()
    }

    "BANNED 상태 변경은 banReason이 필수다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            status = EnrollmentStatus.ENABLED,
            joinedAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-1",
                request = UpdateEnrollmentRequest(
                    status = EnrollmentStatus.BANNED,
                    banReason = null,
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    "수강생 삭제는 등록 정보를 제거한다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            status = EnrollmentStatus.ENABLED,
            joinedAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.courseEnrollmentRepository.delete(enrollment)).thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.deleteEnrollment(
                courseSlug = "back-basic",
                userId = "user-1",
            )
        )
            .verifyComplete()

        Mockito.verify(fixture.courseEnrollmentRepository).delete(enrollment)
    }

    "삭제된 수강생은 다시 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val reportUser = enrollmentUser(id = "user-1", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "back-basic",
                request = EnrollCourseRequest(publicCode = "FL301"),
            )
        )
            .assertNext { enrollment ->
                enrollment.status shouldBe EnrollmentStatus.ENABLED
                enrollment.userId shouldBe "user-1"
                enrollment.publicCode shouldBe "#FL301"
            }
            .verifyComplete()

        Mockito.verify(fixture.userQueryPort, Mockito.never()).findByPublicCode("FL301")
    }

    "canonical publicCode가 없으면 legacy 값으로 조회해 등록한다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val reportUser = enrollmentUser(id = "user-1", publicCode = "FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.empty())
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "back-basic",
                request = EnrollCourseRequest(publicCode = "#FL301"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-1"
                enrollment.publicCode shouldBe "FL301"
            }
            .verifyComplete()

        val lookupOrder = Mockito.inOrder(fixture.userQueryPort)
        lookupOrder.verify(fixture.userQueryPort).findByPublicCode("#FL301")
        lookupOrder.verify(fixture.userQueryPort).findByPublicCode("FL301")
    }

    "동기화된 사용자가 없으면 422를 반환하고 수강 정보를 조회하지 않는다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.empty())
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("FL301")).thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "back-basic",
                request = EnrollCourseRequest(publicCode = "FL301"),
            )
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
                exception.reason shouldBe
                    "report 서버에서 publicCode=#FL301 사용자를 찾을 수 없습니다. auth 이벤트 동기화 여부를 확인해주세요."
            }
            .verify()

        Mockito.verifyNoInteractions(fixture.courseEnrollmentRepository)
    }

    "FL 코스는 FL 일반 유저만 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val reportUser = enrollmentUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("fl-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "fl-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.FORBIDDEN
                error.reason shouldBe "FL 코스에는 FL 트랙 사용자만 등록할 수 있습니다."
            }
            .verify()
    }

    "SP 코스는 SP 일반 유저를 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = enrollmentUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-sp"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-sp"
                enrollment.publicCode shouldBe "#SP201"
            }
            .verifyComplete()
    }

    "SP 코스는 SP가 아닌 일반 유저 등록을 막는다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = enrollmentUser(id = "user-fl", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#FL301"),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.FORBIDDEN
                error.reason shouldBe "SP 코스에는 SP 트랙 사용자만 등록할 수 있습니다."
            }
            .verify()
    }

    "NO 코스는 모든 일반 유저를 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "no-basic", title = "NO 기초", fieldTag = CourseTrack.NO)
        val reportUser = enrollmentUser(id = "user-fl", publicCode = "#FL301", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("no-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#FL301")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-fl"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "no-basic",
                request = EnrollCourseRequest(publicCode = "#FL301"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-fl"
                enrollment.publicCode shouldBe "#FL301"
            }
            .verifyComplete()
    }

    "NO 코스는 SP 일반 유저도 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "no-basic", title = "NO 기초", fieldTag = CourseTrack.NO)
        val reportUser = enrollmentUser(id = "user-sp", publicCode = "#SP201", role = "USER")

        Mockito.`when`(fixture.courseRepository.findBySlug("no-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#SP201")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-sp"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "no-basic",
                request = EnrollCourseRequest(publicCode = "#SP201"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-sp"
                enrollment.publicCode shouldBe "#SP201"
            }
            .verifyComplete()
    }

    "ADMIN 은 어떤 코스에도 예외적으로 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val reportUser = enrollmentUser(id = "admin-1", publicCode = "#AD001", role = "ADMIN")

        Mockito.`when`(fixture.courseRepository.findBySlug("sp-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#AD001")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "admin-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "sp-basic",
                request = EnrollCourseRequest(publicCode = "#AD001"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "admin-1"
                enrollment.publicCode shouldBe "#AD001"
            }
            .verifyComplete()
    }

    "ORGANIZER 는 어떤 코스에도 예외적으로 등록할 수 있다" {
        val fixture = CourseEnrollmentCommandFixture()
        val course = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val reportUser = enrollmentUser(id = "organizer-1", publicCode = "#OR001", role = "ORGANIZER")

        Mockito.`when`(fixture.courseRepository.findBySlug("fl-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.userQueryPort.findByPublicCode("#OR001")).thenReturn(Mono.just(reportUser))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "organizer-1"))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseEnrollmentRepository.save(ArgumentMatchers.any(CourseEnrollment::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseEnrollment) }

        StepVerifier.create(
            fixture.service.enrollMember(
                courseSlug = "fl-basic",
                request = EnrollCourseRequest(publicCode = "#OR001"),
            )
        )
            .assertNext { enrollment ->
                enrollment.userId shouldBe "organizer-1"
                enrollment.publicCode shouldBe "#OR001"
            }
            .verifyComplete()
    }

})

private class CourseEnrollmentCommandFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val userQueryPort: CourseEnrollmentUserQueryPort = Mockito.mock(CourseEnrollmentUserQueryPort::class.java)
    val service = CourseEnrollmentCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        userQueryPort = userQueryPort,
    )
}

private fun queryCourse(id: String, slug: String, title: String, fieldTag: CourseTrack = CourseTrack.FL): Course = Course(
    id = id,
    slug = slug,
    fieldTag = fieldTag,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)

private fun enrollmentUser(
    id: String,
    publicCode: String,
    role: String,
): CourseEnrollmentUserReference = CourseEnrollmentUserReference(
    id = id,
    publicCode = publicCode,
    username = id,
    role = role,
)
