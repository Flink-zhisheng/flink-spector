/*
 * Copyright 2015 Otto (GmbH & Co KG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flinkspector.core.runtime

import com.lmax.disruptor.dsl.Disruptor
import com.lmax.disruptor.util.DaemonThreadFactory
import io.flinkspector.core.CoreSpec
import io.flinkspector.core.util.SerializeUtil
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor

class OutputPublisherSpec extends CoreSpec {

  val config = new ExecutionConfig()
  val typeInfo: TypeInformation[String] = TypeExtractor.getForObject("test")
  val serializer = typeInfo.createSerializer(config)

  trait OutputPublisherCase {
    val factory = new OutputEventFactory

    val disruptor = new Disruptor[OutputEvent](factory, bufferSize, DaemonThreadFactory.INSTANCE)

    val (subscriber, port) = (new OutputSubscriber(1, disruptor), 1)

    val publisher = new OutputPublisher(port, disruptor.getRingBuffer)

    disruptor.start()

    def close() = {
      subscriber.close()
      publisher.close()
      Thread.sleep(500)
    }

    def ser(msg: String) = {
      SerializeUtil.serialize(msg, serializer)
    }

    def der(msg: Array[Byte]) = {
      SerializeUtil.deserialize(msg, serializer)
    }
  }

  "The outputPublisher" should "send a open message" in new OutputPublisherCase {
    publisher.sendOpen(0, 0, SerializeUtil.serialize(serializer))
    val open = subscriber.recv()

    MessageType.getMessageType(open) shouldBe MessageType.OPEN
    close()
  }

  it should "send a message with a record" in new OutputPublisherCase {
    publisher.sendRecord(ser("hello"))
    val record = subscriber.recv()

    MessageType.getMessageType(record) shouldBe MessageType.REC
    der(MessageType.REC.getPayload(record)) shouldBe "hello"
    close()
  }

  it should "send a close message" in new OutputPublisherCase {
    publisher.sendClose(2, 2)
    val record = subscriber.recv()
    MessageType.getMessageType(record) shouldBe MessageType.CLOSE
    close()
  }


}
