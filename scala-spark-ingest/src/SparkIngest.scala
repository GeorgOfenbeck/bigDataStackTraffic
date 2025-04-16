
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._

object SparkIngest {
  def main(args: Array[String]): Unit = {
    // Initialize SparkSession
    val spark = SparkSession.builder()
      .appName("PostgresToHDFS")
      .getOrCreate()

    // PostgreSQL connection properties
    val postgresUrl = sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://postgres-postgresql.postgres.svc.cluster.local:5432/postgres")
    val postgresUser = sys.env.getOrElse("POSTGRES_USER", "postgres")
    val postgresPassword = sys.env.getOrElse("POSTGRES_PASSWORD", "X6s4OheQQe")
    val postgresTable = "road_stats"

    val connectionProperties = new java.util.Properties()
    connectionProperties.setProperty("user", postgresUser)
    connectionProperties.setProperty("password", postgresPassword)
    connectionProperties.setProperty("driver", "org.postgresql.Driver")

    // Read data from PostgreSQL
    val roadStatsDF = spark.read
      .jdbc(postgresUrl, postgresTable, connectionProperties)

    // Optional: Perform transformations (e.g., partitioning or additional metrics)
    val transformedDF = roadStatsDF
      .withColumn("ingestion_time", current_timestamp()) // Add ingestion timestamp
      .repartition(col("road_id")) // Partition by road_id for efficient storage

    // Write data to HDFS in Parquet format
    val hdfsPath = "hdfs://namenode:8020/data/road_stats"
    transformedDF.write
      .mode(SaveMode.Append)
      .parquet(hdfsPath)

    // Purge data from PostgreSQL
    val roadIdsToDelete = roadStatsDF.select("road_id", "window_start").distinct().collect()
    val deleteQuery = roadIdsToDelete.map(row => s"DELETE FROM $postgresTable WHERE road_id = ${row.getInt(0)} AND window_start = ${row.getLong(1)}").mkString("; ")

    val connection = java.sql.DriverManager.getConnection(postgresUrl, postgresUser, postgresPassword)
    val statement = connection.createStatement()
    statement.execute(deleteQuery)
    statement.close()
    connection.close()

    // Stop SparkSession
    spark.stop()
  }
}