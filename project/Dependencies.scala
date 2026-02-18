import sbt._


object Version {

  val scala      = "2.13.12"
  val pekko      = "1.0.2"
  val pekkoHttp  = "1.0.1"
  val sslconfig  = "0.6.1"
  val javaxWsRs  = "1.1.1"
  val rdf4j      = "2.1.6" //"2.3.2"
  val logback    = "1.2.3"
  val scalaTest  = "3.0.8"
  val fuseki     = "3.7.0"
  val xmlBind    = "2.3.2"
  val xerces     = "2.12.2"
}


object Dependencies {

  val pekkoActor         = "org.apache.pekko" %% "pekko-actor"                     % Version.pekko
  val pekkoStream        = "org.apache.pekko" %% "pekko-stream"                    % Version.pekko
  val pekkoHttpCore      = "org.apache.pekko" %% "pekko-http-core"                 % Version.pekkoHttp
  val pekkoHttpSprayJson = "org.apache.pekko" %% "pekko-http-spray-json"           % Version.pekkoHttp
  val pekkoSlf4j         = "org.apache.pekko" %% "pekko-slf4j"                     % Version.pekko
  val sslConfigLib       = "com.typesafe"     %% "ssl-config-core"                 % Version.sslconfig
  val javaxWsRs         = "javax.ws.rs"       %  "jsr311-api"                        % Version.javaxWsRs
  val logbackClassic    = "ch.qos.logback"    %  "logback-classic"                   % Version.logback
  val rdf4jRuntime      = "org.eclipse.rdf4j" %  "rdf4j-runtime"                     % Version.rdf4j
  val jakartaXmlBind    = "jakarta.xml.bind"  % "jakarta.xml.bind-api"               % Version.xmlBind
  val xercesImpl        = "xerces"            %  "xercesImpl"                        % Version.xerces

  val scalaTest         = "org.scalatest"     %% "scalatest"                         % Version.scalaTest   % Test
  val pekkoTestkit       = "org.apache.pekko" %% "pekko-testkit"                   % Version.pekko       % Test
  val pekkoStreamTestkit = "org.apache.pekko" %% "pekko-stream-testkit"            % Version.pekko       % Test
  val fusekiServer      = "org.apache.jena"   %  "jena-fuseki-server"                % Version.fuseki      % Test

  val `reactive-sparql-dependencies` = Seq(
    pekkoActor, pekkoStream, pekkoHttpCore, pekkoHttpSprayJson, pekkoSlf4j,
    javaxWsRs, rdf4jRuntime,
    logbackClassic, scalaTest, pekkoTestkit, pekkoStreamTestkit, fusekiServer,
    jakartaXmlBind, xercesImpl)
}
