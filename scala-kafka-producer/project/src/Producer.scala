
object Producer extends App {
  import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
  //import io.circe.generic.auto._
  import io.circe.syntax._
  import io.circe.parser
  import scala.io.Source
  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future
  import java.time.Instant
  import scala.concurrent.duration._
  import scala.concurrent.Await

  case class Message(car_id: Int, timestamp: String, data: Map[String, String])

  def simulateCar(carId: Int, route: List[Map[String, String]], producer: KafkaProducer[String, String]): Future[Unit] = Future {
    route.foreach { message =>
      val enrichedMessage = message + ("car_id" -> carId.toString, "timestamp" -> Instant.now.toString)
      println(s"sending data $carId: $enrichedMessage")
      producer.send(new ProducerRecord[String, String]("first_kafka_topic", enrichedMessage.asJson.noSpaces))
      Thread.sleep(2000)
    }
  }

  val producerProps = new java.util.Properties()
  producerProps.put("bootstrap.servers", sys.env.getOrElse("KAFKA_BROKER", "kafka:9092"))
  producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
  producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

  val producer = new KafkaProducer[String, String](producerProps)

  val routeSource = Source.fromResource("route.json")
  val routeWithBreak = io.circe.parser.decode[List[Map[String, String]]](routeSource.getLines().mkString).getOrElse(List.empty)
  routeSource.close()

  val futures = for (i <- 0 until 15) yield simulateCar(i, routeWithBreak, producer)

  Await.result(Future.sequence(futures), Duration.Inf)
  producer.close()
}

