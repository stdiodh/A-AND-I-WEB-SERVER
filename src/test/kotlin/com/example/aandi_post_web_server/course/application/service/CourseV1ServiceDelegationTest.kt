package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CourseOutlineResponse
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.api.dto.CourseWeekResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class CourseV1ServiceDelegationTest : StringSpec({
    val commandService = Mockito.mock(CourseCommandService::class.java)
    val queryService = Mockito.mock(CourseQueryService::class.java)
    val service = CourseV1Service(commandService, queryService)

    beforeTest {
        Mockito.reset(commandService, queryService)
    }

    "코스 생성, 수정, 삭제는 command service 결과를 그대로 반환한다" {
        val createRequest = Mockito.mock(CreateCourseRequest::class.java)
        val updateRequest = Mockito.mock(UpdateCourseRequest::class.java)
        val created = Mono.just(Mockito.mock(CourseResponse::class.java))
        val updated = Mono.just(Mockito.mock(CourseResponse::class.java))
        val deleted = Mono.empty<Void>()
        Mockito.`when`(commandService.createCourse(createRequest)).thenReturn(created)
        Mockito.`when`(commandService.updateCourse("back-basic", updateRequest)).thenReturn(updated)
        Mockito.`when`(commandService.deleteCourse("back-basic")).thenReturn(deleted)

        service.createCourse(createRequest) shouldBeSameInstanceAs created
        service.updateCourse("back-basic", updateRequest) shouldBeSameInstanceAs updated
        service.deleteCourse("back-basic") shouldBeSameInstanceAs deleted

        Mockito.verify(commandService).createCourse(createRequest)
        Mockito.verify(commandService).updateCourse("back-basic", updateRequest)
        Mockito.verify(commandService).deleteCourse("back-basic")
        Mockito.verifyNoInteractions(queryService)
    }

    "수강 등록, 상태 변경, 삭제는 command service 결과를 그대로 반환한다" {
        val enrollRequest = Mockito.mock(EnrollCourseRequest::class.java)
        val updateRequest = Mockito.mock(UpdateEnrollmentRequest::class.java)
        val enrolled = Mono.just(Mockito.mock(CourseEnrollmentResponse::class.java))
        val updated = Mono.just(Mockito.mock(CourseEnrollmentResponse::class.java))
        val deleted = Mono.empty<Void>()
        Mockito.`when`(commandService.enrollMember("back-basic", enrollRequest)).thenReturn(enrolled)
        Mockito.`when`(commandService.updateEnrollmentStatus("back-basic", "user-1", updateRequest)).thenReturn(updated)
        Mockito.`when`(commandService.deleteEnrollment("back-basic", "user-1")).thenReturn(deleted)

        service.enrollMember("back-basic", enrollRequest) shouldBeSameInstanceAs enrolled
        service.updateEnrollmentStatus("back-basic", "user-1", updateRequest) shouldBeSameInstanceAs updated
        service.deleteEnrollment("back-basic", "user-1") shouldBeSameInstanceAs deleted

        Mockito.verifyNoInteractions(queryService)
    }

    "과제 생성, 복사, 수정, 삭제는 command service 결과를 그대로 반환한다" {
        val createRequest = Mockito.mock(CreateAssignmentRequest::class.java)
        val copyRequest = Mockito.mock(CopyAssignmentRequest::class.java)
        val updateRequest = Mockito.mock(UpdateAssignmentRequest::class.java)
        val created = Mono.just(Mockito.mock(AssignmentDetailResponse::class.java))
        val copied = Mono.just(Mockito.mock(AssignmentDetailResponse::class.java))
        val updated = Mono.just(Mockito.mock(AssignmentDetailResponse::class.java))
        val deleted = Mono.empty<Void>()
        Mockito.`when`(commandService.createAssignment("back-basic", createRequest, "admin-1")).thenReturn(created)
        Mockito.`when`(commandService.copyAssignment("front-basic", copyRequest, "admin-1")).thenReturn(copied)
        Mockito.`when`(commandService.updateAssignment("back-basic", "assignment-1", updateRequest)).thenReturn(updated)
        Mockito.`when`(commandService.deleteAssignment("back-basic", "assignment-1")).thenReturn(deleted)

        service.createAssignment("back-basic", createRequest, "admin-1") shouldBeSameInstanceAs created
        service.copyAssignment("front-basic", copyRequest, "admin-1") shouldBeSameInstanceAs copied
        service.updateAssignment("back-basic", "assignment-1", updateRequest) shouldBeSameInstanceAs updated
        service.deleteAssignment("back-basic", "assignment-1") shouldBeSameInstanceAs deleted

        Mockito.verifyNoInteractions(queryService)
    }

    "코스 조회 계열은 query service 결과를 그대로 반환한다" {
        val adminCourses = Flux.just(Mockito.mock(CourseResponse::class.java))
        val courses = Flux.just(Mockito.mock(CourseResponse::class.java))
        val course = Mono.just(Mockito.mock(CourseResponse::class.java))
        val outline = Mono.just(Mockito.mock(CourseOutlineResponse::class.java))
        val enrollments = Flux.just(Mockito.mock(CourseEnrollmentResponse::class.java))
        val weeks = Flux.just(Mockito.mock(CourseWeekResponse::class.java))
        val assignmentCourse = Mono.just(Mockito.mock(CourseResponse::class.java))
        Mockito.`when`(queryService.getAdminCourses()).thenReturn(adminCourses)
        Mockito.`when`(queryService.getCourses("user-1")).thenReturn(courses)
        Mockito.`when`(queryService.getCourse("back-basic", "user-1")).thenReturn(course)
        Mockito.`when`(queryService.getCourseOutline("back-basic", "user-1")).thenReturn(outline)
        Mockito.`when`(queryService.getEnrollments("back-basic")).thenReturn(enrollments)
        Mockito.`when`(queryService.getWeeks("back-basic", "user-1")).thenReturn(weeks)
        Mockito.`when`(queryService.getAssignmentCourse("assignment-1", "user-1")).thenReturn(assignmentCourse)

        service.getAdminCourses() shouldBeSameInstanceAs adminCourses
        service.getCourses("user-1") shouldBeSameInstanceAs courses
        service.getCourse("back-basic", "user-1") shouldBeSameInstanceAs course
        service.getCourseOutline("back-basic", "user-1") shouldBeSameInstanceAs outline
        service.getEnrollments("back-basic") shouldBeSameInstanceAs enrollments
        service.getWeeks("back-basic", "user-1") shouldBeSameInstanceAs weeks
        service.getAssignmentCourse("assignment-1", "user-1") shouldBeSameInstanceAs assignmentCourse

        Mockito.verifyNoInteractions(commandService)
    }

    "과제 조회 계열은 필터 인자를 바꾸지 않고 query service에 위임한다" {
        val adminAssignments = Flux.just(Mockito.mock(AssignmentSummaryResponse::class.java))
        val adminDetail = Mono.just(Mockito.mock(AssignmentDetailResponse::class.java))
        val byWeek = Flux.just(Mockito.mock(AssignmentSummaryResponse::class.java))
        Mockito.`when`(queryService.getAdminAssignments("back-basic", 2, AssignmentStatus.DRAFT)).thenReturn(adminAssignments)
        Mockito.`when`(queryService.getAdminAssignmentDetail("back-basic", "assignment-1")).thenReturn(adminDetail)
        Mockito.`when`(queryService.getAssignmentsByWeek("back-basic", 3, null, "user-1")).thenReturn(byWeek)

        service.getAdminAssignments("back-basic", 2, AssignmentStatus.DRAFT) shouldBeSameInstanceAs adminAssignments
        service.getAdminAssignmentDetail("back-basic", "assignment-1") shouldBeSameInstanceAs adminDetail
        service.getAssignmentsByWeek("back-basic", 3, null, "user-1") shouldBeSameInstanceAs byWeek

        Mockito.verify(queryService).getAdminAssignments("back-basic", 2, AssignmentStatus.DRAFT)
        Mockito.verify(queryService).getAssignmentsByWeek("back-basic", 3, null, "user-1")
        Mockito.verifyNoInteractions(commandService)
    }
})
