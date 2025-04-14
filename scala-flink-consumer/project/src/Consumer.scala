import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer // Import Kafka Consumer
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

case class KafkaConfig(broker: String, topic: String, groupId: String)

case class Message(road_id: String, timestamp: Long, speed: Int)

object KafkaConsumerApp extends App {
  private val logger                        = LoggerFactory.getLogger(this.getClass)
  implicit val system: ActorSystem          = ActorSystem("KafkaConsumerSystem")
  implicit val materializer: Materializer   = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val kafkaConfig = KafkaConfig("kafka.kafka.svc.cluster.local:9092", "geohash1", "groupid1")

  val consumerSettings = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(kafkaConfig.broker)
    .withGroupId(kafkaConfig.groupId)
    .withProperty("auto.offset.reset", "earliest")

  val consumerFuture = Consumer
    .plainSource(consumerSettings, Subscriptions.topics(kafkaConfig.topic))
    .map { record =>
      // Parse JSON string into Message case class
      val json   = Json.parse(record.value())
      val roadId = (json \ "roadId").as[String]
      val speed  = (json \ "speed").as[Int]
      Message(roadId, System.currentTimeMillis(), speed)
    }
    .groupedWithin(1000, scala.concurrent.duration.FiniteDuration(5, "seconds")) // Batch messages
    .map { messages =>
      // Calculate average speed for each road_id
      messages
        .groupBy(_.road_id)
        .map { case (roadId, msgs) =>
          val totalSpeed = msgs.map(_.speed).sum
          val count      = msgs.size
          val avgSpeed   = if (count > 0) totalSpeed.toDouble / count else 0.0
          (roadId, avgSpeed, count)
        }
    }
    .to(Sink.foreach { aggregated =>
      // Log the aggregated results
      aggregated.foreach { case (roadId, avgSpeed, count) =>
        logger.error(s"Road ID: $roadId, Average Speed: $avgSpeed, Count: $count")
      }
    }).run()

  
 

  sys.addShutdownHook {
    system.terminate()
  }
}
