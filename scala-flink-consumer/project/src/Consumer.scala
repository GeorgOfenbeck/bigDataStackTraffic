import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.api.common.serialization.SimpleStringSchema
// Removed incorrect import for DataStream
import org.apache.flink.api.common.functions.AggregateFunction
import java.util.Properties
import play.api.libs.json.Json

case class KafkaConfig(broker: String, topic: String, groupId: String) {
  def properties: Properties = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", broker)
    props.setProperty("group.id", groupId)
    props
  }
}

case class Message(road_id: String, timestamp: Long, speed: Int)

object Consumer extends App {
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    val kafkaConfig = KafkaConfig("kafka.kafka.svc.cluster.local:9092", "geohash1", "groupid1")
    val kafkaConsumer = new FlinkKafkaConsumer[String](kafkaConfig.topic, new SimpleStringSchema(), kafkaConfig.properties)

    val stream: DataStream[String] = env.addSource(kafkaConsumer)

    val messages: DataStream[Message] = stream
      .map { msg =>
        // Parse JSON string into Message case class
        val json = Json.parse(msg)
        val roadId = (json \ "road_id").as[String]
        val speed = (json \ "speed").as[Int]
        Message(roadId, System.currentTimeMillis(), speed)
      }

    val aggregatedStream = messages
      .keyBy(_.road_id)
      .timeWindow(Time.seconds(30), Time.seconds(5)) // Sliding window of 30 seconds, sliding every 5 seconds
      .aggregate(new AverageSpeedAggregator)

    aggregatedStream.print()

    env.execute("Flink Kafka Consumer with Sliding Window")
}

class AverageSpeedAggregator extends AggregateFunction[Message, (Int, Int), (Double, Int)] {
  // Accumulator: (sum of speeds, count of messages)
  override def createAccumulator(): (Int, Int) = (0, 0)

  override def add(value: Message, accumulator: (Int, Int)): (Int, Int) = {
    (accumulator._1 + value.speed, accumulator._2 + 1)
  }

  override def getResult(accumulator: (Int, Int)): (Double, Int) = {
    val averageSpeed = if (accumulator._2 > 0) accumulator._1.toDouble / accumulator._2 else 0.0
    (averageSpeed, accumulator._2)
  }

  override def merge(a: (Int, Int), b: (Int, Int)): (Int, Int) = {
    (a._1 + b._1, a._2 + b._2)
  }
}