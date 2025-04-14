import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{Flow, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import org.apache.kafka.common.serialization.StringDeserializer
import play.api.libs.json.Json
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}
import scala.concurrent.Future

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

  Consumer
    .committableSource(consumerSettings, Subscriptions.topics(kafkaConfig.topic))
    .map { msg =>
      // Parse JSON string into Message case class
      val json   = Json.parse(msg.record.value())
      val roadId = (json \ "roadId").as[String]
      val speed  = (json \ "speed").as[Int]
      (Message(roadId, System.currentTimeMillis(), speed), msg.committableOffset)
    }
    .groupedWithin(1000, scala.concurrent.duration.FiniteDuration(5, "seconds")) // Batch messages
    .map { batch =>
      val messages = batch.map(_._1)
      val offsets  = batch.map(_._2)

      // Calculate average speed for each road_id
      val aggregated = messages
        .groupBy(_.road_id)
        .map { case (roadId, msgs) =>
          val totalSpeed = msgs.map(_.speed).sum
          val count      = msgs.size
          val avgSpeed   = if (count > 0) totalSpeed.toDouble / count else 0.0
          (roadId, avgSpeed, count)
        }

      (aggregated, offsets)
    }
    .mapAsync(1) { case (aggregated, offsets) =>
      // Log the aggregated results
      aggregated.foreach { case (roadId, avgSpeed, count) =>
        logger.error(s"Road ID: $roadId, Average Speed: $avgSpeed, Count: $count")
      }

        // Commit offsets after processing
        offsets.foreach { offset => 
            offset.commitScaladsl().onComplete {
                case Success(_) => logger.info("Offsets committed successfully")
                case Failure(ex) => logger.error(s"Failed to commit offsets: ${ex.getMessage}")
            }
            }
        // Return a dummy result to satisfy the mapAsync
        // This can be replaced with actual processing logic
        Future.successful(())
    }
    .runWith(Sink.ignore)

  sys.addShutdownHook {
    system.terminate()
  }
}