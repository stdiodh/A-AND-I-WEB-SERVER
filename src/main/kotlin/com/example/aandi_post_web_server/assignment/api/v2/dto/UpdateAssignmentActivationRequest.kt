package com.example.aandi_post_web_server.assignment.api.v2.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull

@Schema(description = "과제 활성화 상태 변경 요청")
data class UpdateAssignmentActivationRequest(
    @field:Schema(description = "활성화 여부. true 이면 USER/ORGANIZER 호출이 정상 응답하고, false 이면 503 ASSIGNMENT_DEACTIVATED 로 차단됩니다.", example = "false")
    @field:NotNull
    val active: Boolean?,
)
