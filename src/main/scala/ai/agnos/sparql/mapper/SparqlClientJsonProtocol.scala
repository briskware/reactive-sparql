package ai.agnos.sparql.mapper

import org.apache.pekko.http.scaladsl.unmarshalling._
import ai.agnos.sparql.api._
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Json protocol which relies on spray's Json library.
  */
object SparqlClientJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with PredefinedFromEntityUnmarshallers with PredefinedFromStringUnmarshallers {

  /**
    * A set if JSON parsers form "query results" format compliant
    * with https://www.w3.org/TR/2013/REC-sparql11-results-json-20130321/
    */
  implicit val format4: RootJsonFormat[QuerySolutionValue] = jsonFormat3(QuerySolutionValue)
  implicit object format5 extends RootJsonFormat[QuerySolution] {
    def write(c : QuerySolution) = {
      JsObject(c.values.map(e => e._1 -> e._2.toJson))
    }
    def read(row : JsValue) = read(row.asInstanceOf[JsObject])
    def read(row : JsObject) = {
      QuerySolution((row.fields.view.mapValues {
        (value : JsValue) => value.convertTo[QuerySolutionValue]
      }.toMap))
    }
  }
  implicit val format2: RootJsonFormat[ResultSetResults] = jsonFormat(ResultSetResults, "bindings")
  implicit val format1: RootJsonFormat[ResultSetVars] = jsonFormat(ResultSetVars, "vars")
  implicit val format3: RootJsonFormat[ResultSet] = jsonFormat2(ResultSet)


  /**
    * Boolean entity unmarshaller for (text/boolean) media type.
    * @return
    */
  implicit def booleanEntityUnmarshaller: FromEntityUnmarshaller[Boolean] =
    byteStringUnmarshaller mapWithInput { (entity, bytes) =>
      if (entity.isKnownEmpty) false
      else bytes.decodeString(Unmarshaller.bestUnmarshallingCharsetFor(entity).nioCharset.name).toLowerCase.equals("true")
    }

}
