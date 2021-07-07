package components

import com.couchbase.client.scala.{Bucket, Cluster, Collection}
import com.google.inject.ImplementedBy
import play.api.Configuration
import play.inject.ApplicationLifecycle

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.concurrent.duration._

/** Holds the connection to Couchbase.
 *
 * Provided as a trait so that it can be easily replaced with a mocked implementation during testing */
@ImplementedBy(classOf[CouchbaseConnectionImpl])
trait CouchbaseConnection {
  val cluster: Cluster

  // In this example app we are only working with a single Bucket
  val bucket: Bucket
}

/** @Singleton ensures this class is created once.  Each `Cluster` object maintains various resources with a moderate cost,
 * such as thread pools, so we want to only have one of them generally.
 *
 * The class will be created on-demand, e.g. on the first request to this server.  If instead you want to create it at
 * application startup, see https://www.playframework.com/documentation/2.8.x/ScalaDependencyInjection#Eager-bindings.
 */
@Singleton
class CouchbaseConnectionImpl @Inject() (configuration: Configuration) extends CouchbaseConnection {

  // `configuration` comes from the conf/application.conf file, and should be adjusted for your configuration
  private val host = configuration.get[String]("couchbase.host")
  private val username = configuration.get[String]("couchbase.username")
  private val password = configuration.get[String]("couchbase.password")
  private val bucketName = configuration.get[String]("couchbase.bucket")

  // Using fail-fast exception handling .get

  val cluster: Cluster = Cluster.connect(host, username, password).get

  val bucket: Bucket = cluster.bucket(bucketName)
  bucket.waitUntilReady(30.seconds)
}
