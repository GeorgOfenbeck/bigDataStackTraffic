import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, _}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.util.Collector
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import java.util.Properties
import spray.json._
import org.apache.flink.streaming.connectors.cassandra.CassandraSink
import org.apache.flink.connector.jdbc.JdbcSink
import org.apache.flink.connector.jdbc.JdbcStatementBuilder

case class Location(latitude: Double, longitude: Double)
case class GeoHashMessage(
    device_id: String,
    timestamp: String,
    location: Location,
    speed: Double,
    heading: Double,
    altitude: Double,
    accuracy: Double,
    geoHash: Int,
    roadId: Int,
)
case class RoadStats(roadId: Int, avgSpeed: Double, count: Long, windowStart: Long, windowEnd: Long)

import org.slf4j.LoggerFactory

object ScalaKafkaConsumer {
  private val logger = LoggerFactory.getLogger(ScalaKafkaConsumer.getClass)
  // JSON deserialization using Spray JSON
  object GeoHashMessageJsonProtocol extends DefaultJsonProtocol {
    implicit val locationFormat: RootJsonFormat[Location]             = jsonFormat2(Location)
    implicit val geoHashMessageFormat: RootJsonFormat[GeoHashMessage] = jsonFormat9(GeoHashMessage)
  }
  import GeoHashMessageJsonProtocol._
  def main(args: Array[String]): Unit = {
    // Test Cassandra connection
    //CassandraConnectionTester.testConnection()
    // Set up the execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment

    // Kafka properties
    val properties = new Properties()
    properties.setProperty("bootstrap.servers", "kafka.kafka.svc.cluster.local:9092")
    properties.setProperty("group.id", "flink-consumer-group")

    // Kafka consumer
    val kafkaConsumer = new FlinkKafkaConsumer[String](
      "geohash1",
      new SimpleStringSchema(),
      properties,
    )
    kafkaConsumer.setStartFromLatest()

    import org.apache.flink.api.common.typeinfo.TypeInformation
    implicit val stringTypeInfo: TypeInformation[String] = org.apache.flink.api.scala.createTypeInformation[String]

    implicit val geoHashMessageTypeInfo: TypeInformation[GeoHashMessage] =
      org.apache.flink.api.scala.createTypeInformation[GeoHashMessage]
    implicit val intTypeInfo: TypeInformation[Int] = org.apache.flink.api.scala.createTypeInformation[Int]
    implicit val doubleLongTypeInfo: TypeInformation[(Double, Long)] =
      org.apache.flink.api.scala.createTypeInformation[(Double, Long)]
    implicit val roadStatsTypeInfo: TypeInformation[RoadStats] =
      org.apache.flink.api.scala.createTypeInformation[RoadStats]

    val stream = env
      .addSource(kafkaConsumer)
      .map(x => toGeoHashMessage(x)) // Parse JSON to case class
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forMonotonousTimestamps[GeoHashMessage]()
          .withTimestampAssigner((event, _) => java.time.Instant.parse(event.timestamp).toEpochMilli),
      )

    // Sliding window computation
    val resultStream = stream
      .keyBy(_.roadId)
      .timeWindow(Time.seconds(30), Time.seconds(5))
      .aggregate(new AvgSpeedAndCountAggregator, new WindowResultFunction)

    // Output results to log
    //  logger.info(resultStream.toString())
    resultStream.print()
    logger.info("Result Stream: " + resultStream.toString())

      // Read PostgreSQL connection details from environment variables
    val postgresUrl = sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://localhost:5432/postgres")
    val postgresUser = sys.env.getOrElse("POSTGRES_USER", "postgres")
    val postgresPassword = sys.env.getOrElse("POSTGRES_PASSWORD", "")

    // Define the PostgreSQL sink
    val postgresSink = JdbcSink.sink[RoadStats](
      "INSERT INTO road_stats (road_id, window_start, window_end, avg_speed, count) VALUES (?, ?, ?, ?, ?)",
      new JdbcStatementBuilder[RoadStats] {
        override def accept(ps: java.sql.PreparedStatement, t: RoadStats): Unit = {
          ps.setInt(1, t.roadId)
          ps.setLong(2, t.windowStart)
          ps.setLong(3, t.windowEnd)
          ps.setDouble(4, t.avgSpeed)
          ps.setLong(5, t.count)
        }
      },
      new org.apache.flink.connector.jdbc.JdbcExecutionOptions.Builder().withBatchSize(1000).build(),
      new org.apache.flink.connector.jdbc.JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl(postgresUrl)
        .withDriverName("org.postgresql.Driver")
        .withUsername(postgresUser)
        .withPassword(postgresPassword)
        .build()
    )

    // Add the sink to the result stream
    resultStream.addSink(postgresSink)

// Add this after `resultStream.print()`
    /*
    CassandraSink
      .addSink(resultStream)
      //.setHost("cassandra.cassandra.svc.cluster.local") // Replace with your Cassandra service hostname
      .setQuery(
        "INSERT INTO roads.road_stats (road_id, window_start, window_end, avg_speed, count) VALUES (?, ?, ?, ?, ?);",
      )
      .setClusterBuilder(new org.apache.flink.streaming.connectors.cassandra.ClusterBuilder {
        override def buildCluster(builder: com.datastax.driver.core.Cluster.Builder): com.datastax.driver.core.Cluster = {
          builder.addContactPoint("cassandra.cassandra.svc.cluster.local")
          .withCredentials("cassandra", "gzyJ7m4Dca") // Add username and password here
          .build()
        }
      })
      .build() */

    // Execute the Flink job
    env.execute("Scala Kafka Consumer with Sliding Window")
  }

  // Aggregator to compute sum of speeds and count
  class AvgSpeedAndCountAggregator extends AggregateFunction[GeoHashMessage, (Double, Long), (Double, Long)] {
    override def createAccumulator(): (Double, Long) = (0.0, 0L)
    override def add(value: GeoHashMessage, accumulator: (Double, Long)): (Double, Long) =
      (accumulator._1 + value.speed, accumulator._2 + 1)
    override def getResult(accumulator: (Double, Long)): (Double, Long) = accumulator
    override def merge(a: (Double, Long), b: (Double, Long)): (Double, Long) =
      (a._1 + b._1, a._2 + b._2)
  }
  class WindowResultFunction extends WindowFunction[(Double, Long), RoadStats, Int, TimeWindow] {
    override def apply(
        key: Int,
        window: TimeWindow,
        input: Iterable[(Double, Long)],
        out: Collector[RoadStats],
    ): Unit = {
      val (sumSpeed, count) = input.iterator.next()
      out.collect(RoadStats(key, sumSpeed / count, count, window.getStart, window.getEnd))
    }
  }

  def toGeoHashMessage(json: String): GeoHashMessage =
    // Deserialize JSON string to GeoHashMessage
    json.parseJson.convertTo[GeoHashMessage]

}
