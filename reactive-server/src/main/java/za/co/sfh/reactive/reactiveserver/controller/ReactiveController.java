package za.co.sfh.reactive.reactiveserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.Date;
import java.util.stream.Stream;

@RestController
@Slf4j
public class ReactiveController {

     @GetMapping("/events/{id}")
     Mono<Event> eventById(@PathVariable long id) {
          return Mono.just(new Event(id, new Date()));
     }

     @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, value = "/events")
     Flux<Event> getEvents() {
          Flux<Event> eventFlux = Flux.fromStream(Stream.generate(() -> new Event(System.currentTimeMillis(), new Date())));

          Flux<Long> durationFlux = Flux.interval(Duration.ofSeconds(1));

          return Flux.zip(eventFlux, durationFlux).map(Tuple2::getT1);
     }
}
