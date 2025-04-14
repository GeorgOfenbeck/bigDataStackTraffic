import org.slf4j.LoggerFactory
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import scala.io.Source
import java.time.Instant
import play.api.libs.json.Json
import play.api.libs.json.Format

object Producer extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)

  // Define the case class and JSON format for Play JSON
  case class Route(speed: Int)
  implicit val routeFormat: Format[Route] = Json.format[Route]

  val producerProps = new java.util.Properties()
  // producerProps.put("bootstrap.servers", sys.env.getOrElse("KAFKA_BROKER", "localhost:9092"))
  producerProps.put("bootstrap.servers", sys.env.getOrElse("KAFKA_BROKER", "kafka.kafka.svc.cluster.local:9092"))
  producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  logger.info(s"Kafka broker: ${producerProps.getProperty("bootstrap.servers")}")
  logger.info(s"Kafka topic: first_kafka_topic")
  val producer = new KafkaProducer[String, String](producerProps)

  // Read and parse the JSON file
  val routeSource = Source.fromResource("route.json")
  val routeJson   = routeSource.getLines().mkStrin
  routeSource.close()

  // val routeData = Json.parse(routeJson).as[List[Route]]

  // Send each route as a Kafka message

  var carId = 0

  val randomMessageGen = RandomMessage.enrichedMessage()

  while (true) {
    val enrichedMessage = randomMessageGen.sample
    logger.error(s"Sending data $enrichedMessage")
    producer.send(new ProducerRecord[String, String]("geohash1", enrichedMessage.toString()))
    Thread.sleep(500)
  }
  producer.close()
}
