package com.example.aandi_post_web_server.assignment.domain

import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentExampleRequest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component

@Component
class BojProblemHtmlParser {

    fun parse(html: String): ImportedAssignmentContent {
        val document = Jsoup.parse(html)
        val title = document.selectFirst("#problem_title")?.text()?.trim().orEmpty()
        val description = extractSectionHtml(document, "description")
        val inputDescription = extractSectionHtml(document, "input")
        val outputDescription = extractSectionHtml(document, "output")
        val examples = extractExamples(document)

        return ImportedAssignmentContent(
            title = title,
            description = description,
            inputDescription = inputDescription.ifBlank { null },
            outputDescription = outputDescription.ifBlank { null },
            examples = examples,
        )
    }

    private fun extractSectionHtml(document: Document, sectionId: String): String {
        val section = document.selectFirst("section#$sectionId")?.clone() ?: return ""
        section.select("h2, h3").remove()
        return section.html().trim()
    }

    private fun extractExamples(document: Document): List<CreateAssignmentExampleRequest> {
        val inputs = document.select("section[id^=sampleinput] pre")
        val outputs = document.select("section[id^=sampleoutput] pre")
        val count = minOf(inputs.size, outputs.size)
        return (0 until count).map { index ->
            CreateAssignmentExampleRequest(
                seq = index + 1,
                inputText = inputs[index].text(),
                outputText = outputs[index].text(),
                description = "BOJ 예제 ${index + 1}",
            )
        }
    }
}
