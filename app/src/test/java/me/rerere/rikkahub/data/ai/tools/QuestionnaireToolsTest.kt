package me.rerere.rikkahub.data.ai.tools

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.InfoSetQuestionnaireType
import me.rerere.ai.ui.InfoSetQuestionOption
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.createInfoSetQuestionnaire
import me.rerere.ai.ui.infoSetQuestionnaireNotice
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class QuestionnaireToolsTest {
    @Test
    fun `questionnaire emitted by a tool remains discoverable on an inactive branch`() {
        val questionnaire = createInfoSetQuestionnaire(
            issuerLabel = "Aire",
            recipientLabel = "隐",
            type = InfoSetQuestionnaireType.SINGLE,
            question = "先做哪一个？",
            options = listOf(
                InfoSetQuestionOption("o1", "写代码"),
                InfoSetQuestionOption("o2", "休息"),
            ),
            id = "q-tool",
        )
        val withQuestionnaire = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Tool(
                    toolCallId = "present-q-tool",
                    toolName = QuestionnaireToolNames.PRESENT,
                    input = """{"type":"single","question":"先做哪一个？"}""",
                    output = listOf(
                        UIMessagePart.Text("""{"status":"presented","questionnaire_id":"q-tool"}"""),
                        infoSetQuestionnaireNotice(questionnaire),
                    ),
                )
            ),
        )
        val activeBranch = UIMessage.assistant("另一个分支")
        val conversation = Conversation.ofId(Uuid.random()).copy(
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(withQuestionnaire, activeBranch),
                    selectIndex = 1,
                )
            )
        )

        assertEquals(questionnaire, conversation.findInfoSetQuestionnaire("q-tool"))
    }

    @Test
    fun `assistant cannot answer questionnaire addressed to another actor`() {
        val questionnaire = createInfoSetQuestionnaire(
            issuerLabel = "Aire",
            recipientId = "user:self",
            recipientLabel = "隐",
            type = InfoSetQuestionnaireType.SINGLE,
            question = "给隐的题",
            options = listOf(
                InfoSetQuestionOption("a", "A"),
                InfoSetQuestionOption("b", "B"),
            ),
            id = "q-user",
        )

        val failure = runCatching {
            questionnaire.requireRecipient("assistant:aire")
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
