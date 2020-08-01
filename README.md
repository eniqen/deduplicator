inside project directory run: sbt docker:publishLocal 

Then run docker container: docker run -p 8080:8080 deduplicator:0.1

You can send events using: sbt "runMain org.github.eniqen.deduplicator.GenApp" comand from project directory
