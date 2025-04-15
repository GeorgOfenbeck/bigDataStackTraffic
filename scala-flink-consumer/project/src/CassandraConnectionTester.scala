import com.datastax.driver.core.{Cluster, Session}

object CassandraConnectionTester {
  def testConnection(): Unit = {
    val cassandraHost = "cassandra.cassandra.svc.cluster.local" // Replace with your Cassandra hostname
    val username = "cassandra" // Replace with your username
    val password = "gzyJ7m4Dca" // Replace with your password

    var cluster: Cluster = null
    var session: Session = null

    try {
      // Build the cluster and connect
      cluster = Cluster.builder()
        .addContactPoint(cassandraHost)
        .withCredentials(username, password)
        .withoutMetrics()
        .build()

      session = cluster.connect()

      // Execute a simple query to test the connection
      val resultSet = session.execute("SELECT release_version FROM system.local;")
      val row = resultSet.one()
      println(s"Connected to Cassandra! Release Version: ${row.getString("release_version")}")
    } catch {
      case e: Exception =>
        println(s"Failed to connect to Cassandra: ${e.getMessage}")
    } finally {
      // Clean up resources
      if (session != null) session.close()
      if (cluster != null) cluster.close()
    }
  }
}