import org.scalacheck.Prop.{all, forAll, AnyOperators}
import org.scalacheck.Gen

import scala.io.Source
import java.time.Instant
import play.api.libs.json.Json
import play.api.libs.json.Format
import play.api.libs.json.JsObject

object RandomMessage {
  /*
   *{
  "device_id": "unique_device_id_123", // Anonymized unique identifier
  "timestamp": "2025-04-13T12:34:56Z", // UTC timestamp
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  },
  "speed": 25.5, // Current speed in km/h
  "heading": 90.0, // Direction of movement in degrees (0-360)
  "altitude": 15.0, // Altitude in meters
  "accuracy": 5.0, // Location accuracy in meters
  "motion_type": "driving" // Detected motion type (e.g., walking, driving, stationary)
  }
   */

  val gdevice_id = Gen.choose(0, Int.MaxValue)
  val gspeed     = Gen.choose(0, 100)
  val gheading   = Gen.choose(0, 360)
  val galtidute  = Gen.choose(-300, 8000)
  val gaccuracy  = Gen.choose(1, 300)
  val ggeoHash   = Gen.choose(1, 2) // for dummy purpose - to have 2 topics
  val groadId    = Gen.choose(1, 100)

  def enrichedMessage(): Gen[JsObject] = {
    val gen = for{
      device_id <- gdevice_id
      speed     <- gspeed
      heading   <- gheading
      altitude  <- galtidute
      accuracy  <- gaccuracy
      geoHash   <- ggeoHash
      roadId    <- groadId  
    } yield Json.obj(
      "device_id" -> device_id.toString,
      "timestamp" -> Instant.now.toString,
      "location"  -> Json.obj(
        "latitude"  -> -90.0,
        "longitude" -> -180.0 
      ),
      "speed"     -> speed,
      "heading"   -> heading,
      "altitude"  -> altitude,
      "accuracy"  -> accuracy,
      "geoHash"   -> geoHash,
      "roadId"    -> roadId
    )
    gen
  }
}
