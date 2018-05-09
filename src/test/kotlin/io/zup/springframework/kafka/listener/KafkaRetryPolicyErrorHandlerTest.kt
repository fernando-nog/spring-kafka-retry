package io.zup.springframework.kafka.listener

import io.zup.springframework.kafka.config.KafkaTestConfiguration
import io.zup.springframework.kafka.helper.ListenerFactory
import io.zup.springframework.kafka.helper.Matchers.hasHeader
import io.zup.springframework.kafka.helper.TestConstants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [KafkaTestConfiguration::class])
@EmbeddedKafka(partitions = 1, topics = [TestConstants.MAIN_TOPIC, TestConstants.RETRY_TOPIC])
class KafkaRetryPolicyErrorHandlerTest {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<Int, String>

    @Autowired
    private lateinit var kafkaEmbedded: KafkaEmbedded

    @Autowired
    private lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    private lateinit var receiver: Receiver<Int, String>

    @Autowired
    private lateinit var listenerFactory: ListenerFactory<Int, String>

    @Before
    fun setUp() {
        kafkaListenerEndpointRegistry.listenerContainers.forEach {
            ContainerTestUtils.waitForAssignment(it, kafkaEmbedded.partitionsPerTopic)
        }
    }

    @Test
    fun `should not invoke error handler when message listener succeeds`() {

        receiver
            .reset()
            .withInteractionCount(1)

        kafkaTemplate.send(TestConstants.MAIN_TOPIC, "hello")

        assertTrue(receiver.await())
    }

    @Test
    fun `should invoke error handler when message listener fails`() {

        receiver
            .reset()
            .withInteractionCount(2)
            .withMessageHandler {
                throw RuntimeException("Shit happens")
            }

        kafkaTemplate.send(TestConstants.MAIN_TOPIC, "hello")

        receiver.awaitRetry()
            .let { assertThat(it, hasHeader(KafkaRetryPolicyErrorHandler.REMAINING_RETRIES_HEADER, TestConstants.MAX_RETRIES - 1)) }

        assertTrue(receiver.await())
    }

}