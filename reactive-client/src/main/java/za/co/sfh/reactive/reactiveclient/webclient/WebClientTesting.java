package za.co.sfh.reactive.reactiveclient.webclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WebClientTesting {

     @Bean
     WebClient client() {
          return WebClient.create("http://localhost:8080");
     }

     @Autowired
     private WebClient webClient;

     public Mono<Event> someRestCall(String name) {
          return webClient
                  .get()
                  .uri("", name)
                  .retrieve()
                  .bodyToMono(Event.class);
     }


     @Bean
     CommandLineRunner demo(WebClient webClient) {
          return args -> {
               webClient.method(HttpMethod.GET)
                       .uri("/events")
                       .accept(MediaType.TEXT_EVENT_STREAM)
                       .exchange()
                       .flatMapMany(cr -> cr.bodyToFlux(Event.class))
                       .subscribe(System.out::println);
          };
          /*
               @Override
               public ResponseSpec retrieve() {
                 return new DefaultResponseSpec(exchange(), this::createRequest);
               }

               Use retrieve - exchange give more control.
               WebClient.ResponseSpec retrieve(); ->   ResponseSpec
               Mono<ClientResponse> exchange(); -> you are responsible for handling the the body mapping
           */
     }
}
