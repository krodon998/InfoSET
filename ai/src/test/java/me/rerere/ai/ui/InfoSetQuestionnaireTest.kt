package me.rerere.ai.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class InfoSetQuestionnaireTest {
    private val createdAt = Instant.parse("2026-11-28T03:20:00Z")

    private fun choiceQuestion(type: InfoSetQuestionnaireType = InfoSetQuestionnaireType.SINGLE) =
        createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = type,
            question = "今晚先做什么？",
            options = listOf(
                InfoSetQuestionOption("code", "写代码"),
                InfoSetQuestionOption("rest", "休息"),
                InfoSetQuestionOption("game", "玩一会"),
            ),
            createdAt = createdAt,
            id = "q-1",
        )

    @Test
    fun `prompt and answer survive serialization with stable snapshots`() {
        val question = choiceQuestion()
        val answer = createInfoSetQuestionnaireAnswer(
            questionnaire = question,
            respondentLabel = "Aire",
            respondentId = "assistant:aire",
            selectedOptionIds = listOf("code"),
            answeredAt = Instant.parse("2026-11-28T03:21:00Z"),
        )

        val promptNotice = infoSetQuestionnaireNotice(question)
        val answerNotice = infoSetQuestionnaireAnswerNotice(question, answer)
        val restoredPrompt = Json.decodeFromString<UIMessagePart>(
            Json.encodeToString<UIMessagePart>(promptNotice)
        ) as UIMessagePart.Notice
        val restoredAnswer = Json.decodeFromString<UIMessagePart>(
            Json.encodeToString<UIMessagePart>(answerNotice)
        ) as UIMessagePart.Notice

        assertEquals(question, restoredPrompt.infoSetQuestionnaireOrNull())
        assertEquals(answer, restoredAnswer.infoSetQuestionnaireAnswerOrNull())
        assertEquals("今晚先做什么？", answer.questionSnapshot)
        assertEquals("写代码", answer.optionLabelsSnapshot["code"])
        assertTrue(restoredAnswer.isInfoSetQuestionnaireMemoryCandidate())
        assertTrue(restoredAnswer.infoSetQuestionnaireMemoryCandidateText()!!.contains("answer=写代码"))
    }

    @Test
    fun `all questionnaire types accept their native answer shape`() {
        val multi = choiceQuestion(InfoSetQuestionnaireType.MULTI)
        createInfoSetQuestionnaireAnswer(
            questionnaire = multi,
            respondentLabel = "Aire",
            selectedOptionIds = listOf("code", "game"),
        )

        val ranking = choiceQuestion(InfoSetQuestionnaireType.RANKING)
        createInfoSetQuestionnaireAnswer(
            questionnaire = ranking,
            respondentLabel = "Aire",
            rankedOptionIds = listOf("game", "code", "rest"),
        )

        val slider = createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = InfoSetQuestionnaireType.SLIDER,
            question = "现在有多困？",
            sliderMin = 0.0,
            sliderMax = 10.0,
            sliderStep = 0.5,
        )
        createInfoSetQuestionnaireAnswer(
            questionnaire = slider,
            respondentLabel = "Aire",
            sliderValue = 7.5,
        )

        val free = createInfoSetQuestionnaire(
            issuerLabel = "Aire",
            recipientLabel = "隐",
            type = InfoSetQuestionnaireType.FREE_TEXT,
            question = "想留一句什么？",
        )
        createInfoSetQuestionnaireAnswer(
            questionnaire = free,
            respondentLabel = "隐",
            freeText = "啵。",
        )
    }

    @Test
    fun `single and multi choice can use own answer only when allowed`() {
        val single = createInfoSetQuestionnaire(
            issuerLabel = "Aire",
            recipientLabel = "隐",
            type = InfoSetQuestionnaireType.SINGLE,
            question = "想吃什么？",
            options = listOf(
                InfoSetQuestionOption("a", "咖喱"),
                InfoSetQuestionOption("b", "粥"),
            ),
            allowOwnAnswer = true,
        )
        val own = createInfoSetQuestionnaireAnswer(
            questionnaire = single,
            respondentLabel = "隐",
            freeText = "火锅",
        )
        assertEquals("火锅", own.freeText)
    }

    @Test
    fun `multi choice preserves selected options and own answer together`() {
        val question = createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = InfoSetQuestionnaireType.MULTI,
            question = "今晚可以一起做什么？",
            options = listOf(
                InfoSetQuestionOption("code", "写代码"),
                InfoSetQuestionOption("game", "玩游戏"),
            ),
            allowOwnAnswer = true,
        )
        val answer = createInfoSetQuestionnaireAnswer(
            questionnaire = question,
            respondentLabel = "Aire",
            selectedOptionIds = listOf("code"),
            freeText = "再抱一会",
            sliderValue = 99.0,
        )
        val text = infoSetQuestionnaireAnswerNotice(question, answer)
            .infoSetQuestionnaireMemoryCandidateText()!!

        assertEquals(listOf("code"), answer.selectedOptionIds)
        assertNull(answer.sliderValue)
        assertTrue(text.contains("写代码 + 自定义：再抱一会"))
    }

    @Test
    fun `question payload drops fields that do not belong to its type`() {
        val slider = createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = InfoSetQuestionnaireType.SLIDER,
            question = "困意？",
            options = listOf(
                InfoSetQuestionOption("x", "不该保留"),
                InfoSetQuestionOption("y", "也不该保留"),
            ),
            allowOwnAnswer = true,
            sliderMin = 0.0,
            sliderMax = 10.0,
            sliderStep = 1.0,
        )
        assertTrue(slider.options.isEmpty())
        assertFalse(slider.allowOwnAnswer)

        val single = createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = InfoSetQuestionnaireType.SINGLE,
            question = "选一个",
            options = listOf(
                InfoSetQuestionOption("a", "A"),
                InfoSetQuestionOption("b", "B"),
            ),
            sliderMin = 0.0,
            sliderMax = 10.0,
            sliderStep = 1.0,
        )
        assertEquals(null, single.sliderMin)
        assertEquals(null, single.sliderMax)
        assertEquals(null, single.sliderStep)

        val ranking = createInfoSetQuestionnaire(
            issuerLabel = "隐",
            recipientLabel = "Aire",
            type = InfoSetQuestionnaireType.RANKING,
            question = "排一下",
            options = listOf(
                InfoSetQuestionOption("a", "A"),
                InfoSetQuestionOption("b", "B"),
            ),
            allowOwnAnswer = true,
        )
        assertFalse(ranking.allowOwnAnswer)
    }

    @Test
    fun `invalid answer shape is rejected before entering timeline`() {
        val single = choiceQuestion()

        val failure = runCatching {
            createInfoSetQuestionnaireAnswer(
                questionnaire = single,
                respondentLabel = "Aire",
                selectedOptionIds = listOf("code", "rest"),
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `prompt is not a memory candidate while answer can opt out`() {
        val question = choiceQuestion()
        val answer = createInfoSetQuestionnaireAnswer(
            questionnaire = question,
            respondentLabel = "Aire",
            selectedOptionIds = listOf("rest"),
        )

        assertFalse(infoSetQuestionnaireNotice(question).isInfoSetQuestionnaireMemoryCandidate())
        assertFalse(
            infoSetQuestionnaireAnswerNotice(question, answer, memoryCandidate = false)
                .isInfoSetQuestionnaireMemoryCandidate()
        )
    }
}
