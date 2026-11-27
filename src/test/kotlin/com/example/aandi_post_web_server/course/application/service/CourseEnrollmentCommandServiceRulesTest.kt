package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.application.port.CourseEnrollmentUserReference
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier
import java.time.Instant

class CourseEnrollmentCommandServiceRulesTest : StringSpec({
    "수강 등록은 report 사용자 정보로 ENABLED 수강 정보를 저장한다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse(fieldTag = CourseTrack.SP)),
            users = arrayOf(reportUser(id = "user-1", publicCode = "#SP301", username = "hong")),
        )

        StepVerifier.create(fixture.service.enrollMember("Back-Basic", EnrollCourseRequest(publicCode = " sp301 ")))
            .assertNext { response ->
                response.courseId shouldBe "course-1"
                response.courseSlug shouldBe "back-basic"
                response.userId shouldBe "user-1"
                response.publicCode shouldBe "#SP301"
                response.username shouldBe "hong"
                response.status shouldBe EnrollmentStatus.ENABLED
                response.bannedAt.shouldBeNull()
                response.banReason.shouldBeNull()
            }
            .verifyComplete()

        val persisted = fixture.enrollmentStore.saved.single()
        persisted.courseId shouldBe "course-1"
        persisted.userId shouldBe "user-1"
        persisted.joinedAt shouldBe persisted.updatedAt
        fixture.userQueryPort.queriedPublicCodes shouldContainExactly listOf("#SP301")
    }

    "차단된 수강생 재등록은 CONFLICT를 반환하고 저장하지 않는다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse(fieldTag = CourseTrack.SP)),
            users = arrayOf(reportUser(id = "user-1", publicCode = "#SP301")),
            enrollments = arrayOf(enrollment(status = EnrollmentStatus.BANNED, banReason = "spam")),
        )

        StepVerifier.create(fixture.service.enrollMember("back-basic", EnrollCourseRequest(publicCode = "#SP301")))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.CONFLICT
                exception.reason shouldBe "차단된 사용자는 재등록할 수 없습니다: #SP301"
            }
            .verify()

        fixture.enrollmentStore.saved.shouldBeEmpty()
    }

    "이미 수강 중인 사용자 재등록은 중복 CONFLICT를 반환하고 저장하지 않는다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse(fieldTag = CourseTrack.SP)),
            users = arrayOf(reportUser(id = "user-1", publicCode = "#SP301")),
            enrollments = arrayOf(enrollment(status = EnrollmentStatus.ENABLED)),
        )

        StepVerifier.create(fixture.service.enrollMember("back-basic", EnrollCourseRequest(publicCode = "#SP301")))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.CONFLICT
                exception.reason shouldBe "이미 등록된 사용자입니다. courseId=course-1, userId=user-1"
                exception.cause.shouldBeNull()
            }
            .verify()

        fixture.enrollmentStore.saved.shouldBeEmpty()
    }

    "역할을 알 수 없는 사용자는 다른 트랙 코스에 등록할 수 없다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse(fieldTag = CourseTrack.SP)),
            users = arrayOf(reportUser(id = "user-1", publicCode = "#FL301", role = "GUEST")),
        )

        StepVerifier.create(fixture.service.enrollMember("back-basic", EnrollCourseRequest(publicCode = "#FL301")))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.FORBIDDEN
                exception.reason shouldBe "SP 코스에는 SP 트랙 사용자만 등록할 수 있습니다."
            }
            .verify()

        fixture.enrollmentStore.lookups.shouldBeEmpty()
    }

    "존재하지 않는 코스 등록은 404를 반환하고 사용자를 조회하지 않는다" {
        val fixture = EnrollmentRulesFixture(users = arrayOf(reportUser(id = "user-1", publicCode = "#SP301")))

        StepVerifier.create(fixture.service.enrollMember("missing", EnrollCourseRequest(publicCode = "#SP301")))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "코스를 찾을 수 없습니다: missing"
            }
            .verify()

        fixture.userQueryPort.queriedPublicCodes.shouldBeEmpty()
    }

    "publicCode 형식이 잘못되면 코스 조회 전에 400을 던진다" {
        val fixture = EnrollmentRulesFixture(courses = arrayOf(storedCourse()))

        val exception = shouldThrow<ResponseStatusException> {
            fixture.service.enrollMember("back-basic", EnrollCourseRequest(publicCode = "SP30"))
        }

        exception.statusCode shouldBe HttpStatus.BAD_REQUEST
        exception.reason shouldBe "publicCode 형식이 올바르지 않습니다. 예: #FL301"
        fixture.userQueryPort.queriedPublicCodes.shouldBeEmpty()
    }

    "BANNED 변경은 사유를 trim 해 차단 시각과 함께 저장한다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse()),
            enrollments = arrayOf(enrollment(status = EnrollmentStatus.ENABLED)),
        )

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = " user-1 ",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.BANNED, banReason = "  운영 정책 위반 "),
            ),
        )
            .assertNext { response ->
                response.courseSlug shouldBe "back-basic"
                response.status shouldBe EnrollmentStatus.BANNED
                response.banReason shouldBe "운영 정책 위반"
                response.bannedAt.shouldNotBeNull()
                response.bannedAt shouldBe response.updatedAt
            }
            .verifyComplete()

        val persisted = fixture.enrollmentStore.saved.single()
        persisted.id shouldBe "enroll-1"
        persisted.status shouldBe EnrollmentStatus.BANNED
        persisted.banReason shouldBe "운영 정책 위반"
        persisted.joinedAt shouldBe JOINED_AT
        fixture.enrollmentStore.lookups shouldContainExactly listOf("course-1" to "user-1")
    }

    "BANNED 변경 사유가 공백뿐이면 400을 반환하고 저장하지 않는다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse()),
            enrollments = arrayOf(enrollment(status = EnrollmentStatus.ENABLED)),
        )

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-1",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.BANNED, banReason = "   "),
            ),
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.BAD_REQUEST
                exception.reason shouldBe "BANNED 상태는 banReason이 필요합니다."
            }
            .verify()

        fixture.enrollmentStore.saved.shouldBeEmpty()
    }

    "ENABLED 변경은 차단 시각과 사유를 지운다" {
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse()),
            enrollments = arrayOf(
                enrollment(
                    status = EnrollmentStatus.BANNED,
                    bannedAt = Instant.parse("2026-03-05T00:00:00Z"),
                    banReason = "spam",
                ),
            ),
        )

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-1",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.ENABLED, banReason = "무시되는 사유"),
            ),
        )
            .assertNext { response ->
                response.status shouldBe EnrollmentStatus.ENABLED
                response.bannedAt.shouldBeNull()
                response.banReason.shouldBeNull()
            }
            .verifyComplete()

        val persisted = fixture.enrollmentStore.saved.single()
        persisted.status shouldBe EnrollmentStatus.ENABLED
        persisted.bannedAt.shouldBeNull()
        persisted.banReason.shouldBeNull()
        (persisted.updatedAt > JOINED_AT) shouldBe true
    }

    "등록되지 않은 사용자 상태 변경은 404를 반환한다" {
        val fixture = EnrollmentRulesFixture(courses = arrayOf(storedCourse()))

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-9",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.ENABLED),
            ),
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "코스에 등록된 사용자를 찾을 수 없습니다: user-9"
            }
            .verify()

        fixture.enrollmentStore.saved.shouldBeEmpty()
    }

    "상태 변경 대상 userId가 비어 있으면 400을 던진다" {
        val fixture = EnrollmentRulesFixture(courses = arrayOf(storedCourse()))

        val exception = shouldThrow<ResponseStatusException> {
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "  ",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.ENABLED),
            )
        }

        exception.statusCode shouldBe HttpStatus.BAD_REQUEST
        exception.reason shouldBe "userId는 비어 있을 수 없습니다."
    }

    "존재하지 않는 코스의 수강 상태 변경은 404를 반환한다" {
        val fixture = EnrollmentRulesFixture()

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "missing",
                userId = "user-1",
                request = UpdateEnrollmentRequest(status = EnrollmentStatus.ENABLED),
            ),
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        fixture.enrollmentStore.lookups.shouldBeEmpty()
    }

    "등록되지 않은 사용자 수강 삭제는 404를 반환하고 다른 수강 정보는 유지한다" {
        val existing = enrollment(userId = "user-1")
        val fixture = EnrollmentRulesFixture(
            courses = arrayOf(storedCourse()),
            enrollments = arrayOf(existing),
        )

        StepVerifier.create(fixture.service.deleteEnrollment("back-basic", "user-2"))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "코스에 등록된 사용자를 찾을 수 없습니다: user-2"
            }
            .verify()

        fixture.enrollmentStore.enrollments shouldContainExactly listOf(existing)
    }
})

private val JOINED_AT: Instant = Instant.parse("2026-03-01T00:00:00Z")

private class EnrollmentRulesFixture(
    courses: Array<Course> = emptyArray(),
    users: Array<CourseEnrollmentUserReference> = emptyArray(),
    enrollments: Array<CourseEnrollment> = emptyArray(),
) {
    val courseStore = InMemoryCourseStore(*courses)
    val enrollmentStore = InMemoryCourseEnrollmentStore(*enrollments)
    val userQueryPort = InMemoryEnrollmentUserQueryPort(*users)
    val service = CourseEnrollmentCommandService(
        courseStore = courseStore,
        courseEnrollmentStore = enrollmentStore,
        userQueryPort = userQueryPort,
    )
}

private fun reportUser(
    id: String,
    publicCode: String,
    username: String = id,
    role: String = "USER",
): CourseEnrollmentUserReference = CourseEnrollmentUserReference(
    id = id,
    publicCode = publicCode,
    username = username,
    role = role,
)

private fun enrollment(
    userId: String = "user-1",
    status: EnrollmentStatus = EnrollmentStatus.ENABLED,
    bannedAt: Instant? = null,
    banReason: String? = null,
): CourseEnrollment = CourseEnrollment(
    id = "enroll-1",
    courseId = "course-1",
    userId = userId,
    publicCode = "#SP301",
    username = userId,
    status = status,
    joinedAt = JOINED_AT,
    bannedAt = bannedAt,
    banReason = banReason,
    updatedAt = JOINED_AT,
)
