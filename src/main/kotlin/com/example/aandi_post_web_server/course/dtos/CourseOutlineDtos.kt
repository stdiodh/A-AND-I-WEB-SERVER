package com.example.aandi_post_web_server.course.dtos

import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "코스 목차 헤더 정보")
data class CourseOutlineHeaderResponse(
    @field:Schema(description = "코스 ID", example = "course-1")
    val id: String,
    @field:Schema(description = "코스 슬러그", example = "cs-basic-fl")
    val slug: String,
    @field:Schema(description = "분야 태그", example = "FL")
    val fieldTag: CourseTrack,
    @field:Schema(description = "코스명", example = "기초 CS 과정")
    val title: String,
    @field:Schema(description = "코스 설명", example = "Computer Science Fundamentals")
    val description: String?,
    @field:Schema(description = "과정 단계", example = "BASIC")
    val phase: CoursePhase?,
)

@Schema(description = "목차용 과제 요약")
data class CourseOutlineAssignmentItemResponse(
    @field:Schema(description = "과제 ID", example = "assignment-1")
    val assignmentId: String,
    @field:Schema(description = "주차 번호", example = "1")
    val weekNo: Int,
    @field:Schema(description = "주차 내 순서", example = "1")
    val orderInWeek: Int,
    @field:Schema(description = "과제 제목", example = "터미널 계산기")
    val title: String,
    @field:Schema(description = "난이도", example = "MID")
    val difficulty: AssignmentDifficulty,
    @field:Schema(description = "시작 시각(KST(Asia/Seoul))", example = "2026-03-03T09:00:00+09:00")
    val startAt: Instant,
    @field:Schema(description = "종료 시각(KST(Asia/Seoul))", example = "2026-03-11T08:59:59+09:00")
    val endAt: Instant,
    @field:Schema(description = "체크 표시 여부(완료=true)", example = "false")
    val checked: Boolean,
)

@Schema(description = "코스 목차 요약 응답")
data class CourseOutlineResponse(
    @field:Schema(description = "코스 헤더")
    val course: CourseOutlineHeaderResponse,
    @field:Schema(description = "코스 전체 과제 수", example = "3")
    val totalAssignments: Int,
    @field:Schema(description = "목차용 과제 목록(weekNo/orderInWeek로 정렬)")
    val assignments: List<CourseOutlineAssignmentItemResponse>,
)
