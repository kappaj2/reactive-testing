package za.co.sfh.reactive.reservation;

import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;


@SpringBootApplication
public class ReservationApplication {

     @Bean
     ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
          return new R2dbcTransactionManager(connectionFactory);
     }

     @Bean
     public TransactionalOperator transactionalOperator(ReactiveTransactionManager reactiveTransactionManager) {
          return TransactionalOperator.create(reactiveTransactionManager);
     }

     //   New style endpoints to @RestController with methods...
     @Bean
     RouterFunction<ServerResponse> routes(ReservationRepository reservationRepository) {
          return route()
                  .GET("/reservations", serverRequest -> ok().body(reservationRepository.findAll(), Reservation.class))
                  .build();
     }

     public static void main(String[] args) {

          // BlockHound.install();
          SpringApplication.run(ReservationApplication.class, args);
     }

}

@Service
class GreetingService {
     Flux<GreetingResponse> greet(GreetingRequest greetingRequest) {
          return Flux.fromStream(Stream.generate(() -> new GreetingResponse("Hello " + greetingRequest.getName() + " at " + Instant.now())))
                  .delayElements(Duration.ofSeconds(1));
     }
}

@Configuration
class GreetingWebSocketConfiguration {
     /*
          Business logic handler

//                    Flux<WebSocketMessage> receive = webSocketSession.receive();
//                    Flux<String> names = receive.map(WebSocketMessage::getPayloadAsText);
//                    Flux<GreetingRequest> greetingRequestFlux = names.map(GreetingRequest::new);
//
//                    Flux<GreetingResponse> greetingResponseFlux = greetingRequestFlux.flatMap(greetingService::greet);
//                    Flux<String> map = greetingResponseFlux.map(gr -> gr.getMessage());
//                    Flux<WebSocketMessage> map1 = map.map(webSocketSession::textMessage);
      */
     @Bean
     WebSocketHandler webSocketHandler(GreetingService greetingService) {
          return webSocketSession -> {
               var receive = webSocketSession
                       .receive()
                       .map(WebSocketMessage::getPayloadAsText)
                       .map(GreetingRequest::new)
                       .flatMap(greetingService::greet)
                       .map(gr -> gr.getMessage())
                       .map(webSocketSession::textMessage);
               return webSocketSession.send(receive);
          };
     }

     @Bean
     SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler webSocketHandler) {
          return new SimpleUrlHandlerMapping(Map.of("/ws/greetings", webSocketHandler), 10);
     }

     @Bean
     WebSocketHandlerAdapter webSocketHandlerAdapter() {
          return new WebSocketHandlerAdapter();
     }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingResponse {
     private String message;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingRequest {
     private String name;
}

@RestController
@RequiredArgsConstructor
class ReservationRestController {

     private final ReservationRepository reservationRepository;

     @GetMapping("/reservations")
     Flux<Reservation> getReservations() {
          return this.reservationRepository.findAll();
     }
}


@Service
@RequiredArgsConstructor
class ReservationService {

     private final ReservationRepository reservationRepository;
     private final TransactionalOperator transactionalOperator;

     Flux<Reservation> saveAll(String... names) {
          return this.transactionalOperator.transactional(  // all inside transaction mananger
                  Flux.fromArray(names)
                          .map(name -> new Reservation(null, name))
                          .flatMap(this.reservationRepository::save)
                          .doOnEach(reservationSignal -> reservationSignal.getContext().get("Key")) // retrieve context previously added to pipe...
                          .doOnNext(r -> Assert.isTrue(isValid(r), "The name must have a capital first character"))
          );
     }

     private boolean isValid(Reservation r) {
          return Character.isUpperCase(r.getName().charAt(0));
     }
}

@Log4j2
@Component
@RequiredArgsConstructor
class SampleDataInitializer {

     private final ReservationRepository reservationRepository;
     private final ReservationService reservationService;

     @EventListener(ApplicationReadyEvent.class)
     public void ready() {
/*
          Flux<String> names = Flux.just("Josh", "Andre", "Bennie", "Dieter", "Johan", "Spencer", "Stephan");
          Flux<Reservation> reservations = names.map(name -> new Reservation(null, name));
          Flux<Reservation> saved = reservations.flatMap(this.reservationRepository::save);
 */

          var saved = this.reservationService
                  .saveAll("Josh", "Andre", "Bennie", "Dieter", "Johan", "Spencer", "Stephan");

//          var saved = Flux.just("Josh", "Andre", "Bennie", "Dieter", "Johan", "Spencer", "Stephan")
//                  .map(name -> new Reservation(null, name))
//                  .flatMap(this.reservationRepository::save);

          this.reservationRepository
                  .deleteAll()
                  .thenMany(saved)
                  .thenMany(this.reservationRepository.findAll())
                  .subscriberContext(Context.of("Key", "Value"))  // create context that is passed along with the request (not Thread)
                  .subscribe(log::info);
     }
}

interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {

}

@Data
@AllArgsConstructor
@NoArgsConstructor
//@Document
class Reservation {
     @Id
     private Integer id;
     private String name;
}