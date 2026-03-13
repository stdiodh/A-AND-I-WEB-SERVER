package com.example.aandi_post_web_server.assignment.domain

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class BojProblemHtmlParserTest : StringSpec({

    "BOJ HTML 파서는 제목 설명 입출력 예제를 추출한다" {
        val html = """
            <html>
              <body>
                <h1 id="problem_title">A+B</h1>
                <section id="description">
                  <h2>문제</h2>
                  <p>두 정수 A와 B를 입력받은 다음, 합을 출력하는 프로그램을 작성하시오.</p>
                  <pre>예시 설명 코드</pre>
                </section>
                <section id="input">
                  <h2>입력</h2>
                  <p>첫째 줄에 A와 B가 주어진다. (0 &lt; A, B &lt; 10)</p>
                </section>
                <section id="output">
                  <h2>출력</h2>
                  <p>첫째 줄에 A+B를 출력한다.</p>
                </section>
                <section id="sampleinput1">
                  <h2>예제 입력 1</h2>
                  <pre>1 2</pre>
                </section>
                <section id="sampleoutput1">
                  <h2>예제 출력 1</h2>
                  <pre>3</pre>
                </section>
                <section id="sampleinput2">
                  <h2>예제 입력 2</h2>
                  <pre>3 4</pre>
                </section>
                <section id="sampleoutput2">
                  <h2>예제 출력 2</h2>
                  <pre>7</pre>
                </section>
              </body>
            </html>
        """.trimIndent()

        val parsed = BojProblemHtmlParser().parse(html)

        parsed.title shouldBe "A+B"
        parsed.description.shouldContain("두 정수 A와 B를 입력받은 다음")
        parsed.description.shouldContain("<pre>예시 설명 코드</pre>")
        parsed.inputDescription shouldContain "첫째 줄에 A와 B가 주어진다."
        parsed.outputDescription shouldContain "첫째 줄에 A+B를 출력한다."
        parsed.examples shouldHaveSize 2
        parsed.examples[0].seq shouldBe 1
        parsed.examples[0].inputText shouldBe "1 2"
        parsed.examples[0].outputText shouldBe "3"
        parsed.examples[0].description shouldBe "BOJ 예제 1"
        parsed.examples[1].seq shouldBe 2
        parsed.examples[1].inputText shouldBe "3 4"
        parsed.examples[1].outputText shouldBe "7"
    }

    "BOJ HTML 파서는 예제 쌍이 맞는 개수만 추출한다" {
        val html = """
            <html>
              <body>
                <h1 id="problem_title">Hello World</h1>
                <section id="description"><h2>문제</h2><p>Hello World를 출력한다.</p></section>
                <section id="sampleinput1"><h2>예제 입력 1</h2><pre></pre></section>
                <section id="sampleoutput1"><h2>예제 출력 1</h2><pre>Hello World!</pre></section>
                <section id="sampleinput2"><h2>예제 입력 2</h2><pre>ignored</pre></section>
              </body>
            </html>
        """.trimIndent()

        val parsed = BojProblemHtmlParser().parse(html)

        parsed.examples shouldHaveSize 1
        parsed.examples.first().outputText shouldBe "Hello World!"
    }
})
